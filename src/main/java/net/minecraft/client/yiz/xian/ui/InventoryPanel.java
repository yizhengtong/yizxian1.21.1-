package net.minecraft.client.yiz.xian.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 背包面板主控类（单例）。
 *
 * <p>在玩家背包界面快捷栏下方渲染带边框的槽位面板。
 * 负责几何计算、渲染调度、悬停检测和点击分发。</p>
 */
public class InventoryPanel {

    public static final int PANEL_EXTRA = 34;
    public static final int ORIGINAL_HEIGHT = 166;

    private static final int IMAGE_WIDTH = 176;
    private static final int PADDING = 5;
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int ROWS = 1;

    private static final int PANEL_WIDTH  = PADDING * 2 + COLS * SLOT;
    private static final int PANEL_HEIGHT = PADDING * 2 + ROWS * SLOT;

    /** 槽位自定义背景图 — 前 5 格为 1~5.png，其余为 model.png */
    private static final ResourceLocation[] SLOT_BG = new ResourceLocation[COLS];
    static {
        for (int i = 0; i < COLS; i++) {
            String name = i < 5 ? String.valueOf(i + 1) : "model";
            SLOT_BG[i] = ResourceLocation.fromNamespaceAndPath(
                "yizxianmod", "textures/gui/container/" + name + ".png"
            );
        }
    }

    private final SimpleContainer container;
    private int hoveredIdx = -1;

    public InventoryPanel() {
        this.container = new SimpleContainer(COLS * ROWS);
    }

    // ── 面板尺寸 ──

    public static int panelWidth()  { return PANEL_WIDTH; }
    public static int panelHeight() { return PANEL_HEIGHT; }

    // ── 几何 ──

    public int panelLeft(int leftPos) {
        return leftPos + (IMAGE_WIDTH - PANEL_WIDTH) / 2;
    }

    public int panelTop(int topPos) {
        return topPos + ORIGINAL_HEIGHT;
    }

    private int slotRelX(int col) {
        return (IMAGE_WIDTH - PANEL_WIDTH) / 2 + PADDING + col * SLOT;
    }

    private int slotRelY(int row) {
        return ORIGINAL_HEIGHT + PADDING + row * SLOT;
    }

    // ── 渲染 ──

    public void renderBackground(GuiGraphics g, int leftPos, int topPos) {
        int px = panelLeft(leftPos);
        int py = panelTop(topPos);
        PanelRenderHelper.drawBorder(g, px, py, PANEL_WIDTH, PANEL_HEIGHT);
    }

    public void renderSlots(GuiGraphics g, int leftPos, int topPos) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int tx = leftPos + slotRelX(col) - 1;
                int ty = topPos  + slotRelY(row) - 1;
                ResourceLocation bg = SLOT_BG[col];
                PanelRenderHelper.drawCustomSlot(g, bg, tx, ty);
            }
        }
    }

    public void renderItems(GuiGraphics g, int leftPos, int topPos, Font font) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                ItemStack stack = container.getItem(idx);
                if (!stack.isEmpty()) {
                    int ix = leftPos + slotRelX(col);
                    int iy = topPos  + slotRelY(row);
                    g.renderItem(stack, ix, iy);
                    g.renderItemDecorations(font, stack, ix, iy);
                }
            }
        }
    }

    public void renderHoveredSlot(GuiGraphics g, int leftPos, int topPos, int mouseX, int mouseY) {
        hoveredIdx = getSlotAt(mouseX, mouseY, leftPos, topPos);
        if (hoveredIdx >= 0) {
            int row = hoveredIdx / COLS;
            int col = hoveredIdx % COLS;
            int hx = leftPos + slotRelX(col);
            int hy = topPos  + slotRelY(row);
            g.fill(hx, hy, hx + SLOT - 2, hy + SLOT - 2, 0x80FFFFFF);
        }
    }

    // ── 悬停 ──

    public int getSlotAt(double mouseX, double mouseY, int leftPos, int topPos) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int sx = leftPos + slotRelX(col) - 1;
                int sy = topPos  + slotRelY(row) - 1;
                if (mouseX >= sx && mouseX < sx + SLOT
                        && mouseY >= sy && mouseY < sy + SLOT) {
                    return row * COLS + col;
                }
            }
        }
        return -1;
    }

    // ── 点击 ──

    public boolean handleClick(double mouseX, double mouseY, int button,
                                AbstractContainerMenu menu, Player player,
                                int leftPos, int topPos) {
        int idx = getSlotAt(mouseX, mouseY, leftPos, topPos);
        if (idx < 0) return false;
        return PanelMouseHandler.handleSlotClick(idx, button, menu, container, player);
    }

    // ── 持久化 ──

    public void loadItems() {
        PanelItemStorage.load(container);
    }

    public void saveItems() {
        PanelItemStorage.save(container);
    }

    public int getHoveredIdx() {
        return hoveredIdx;
    }

    // ── 静态保存（供事件监听器调用）──

    private static InventoryPanel instance;

    public static InventoryPanel getInstance() {
        if (instance == null) {
            instance = new InventoryPanel();
            instance.loadItems();
        }
        return instance;
    }

    public static void saveAll() {
        if (instance != null) {
            instance.saveItems();
        }
    }
}
