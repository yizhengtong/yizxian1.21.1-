package net.minecraft.client.yiz.xian.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.yiz.xian.menu.LightCompassMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 光明指南针 GUI Screen。
 *
 * 严格按用户给定坐标布局（GUI 内相对像素）：
 *   ① 工作槽 3 个        : (8,22) (26,22) (44,22)  ← 由 Menu 的 Slot 自绘
 *   ② 搜索栏 EditBox     : x=80  y=28  w=89  h=11
 *   ③ 展示栏 5列×9行滚动  : 起点 (8,49)，每格 18×18，可见约 4 行，滚动看全部
 *   ④ 玩家快捷栏 9 格     : 起点 (8,143)，由 Menu 的 Slot 自绘
 *   ⑤ 滚动条             : x=174 y=49 w=13 h=111
 *
 * 交互：
 *   - 鼠标滚轮 / 拖滚动条 → 滚动展示栏
 *   - 搜索栏输入 → 过滤展示栏物品
 *   - 左键点展示栏物品 → 送入第一个空工作槽（menu.sendToWorkSlot）
 *   - 右键点工作槽 → 移除该工作槽物品（menu.removeFromWorkSlot）
 */
public class LightCompassScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<LightCompassMenu>
        implements MenuAccess<LightCompassMenu> {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.tryBuild("yizxianmod", "textures/gui/container/light_compass.png");

    // ── 布局常量（GUI 内相对坐标，精确对齐背景图真实槽位）──
    // 背景图 195×168。槽位边框 #373737 在 x=8~24（宽17），故槽位左上角 x=8，内部 16px。
    // 列起点: 8,26,44,62,80,98,116,134,152（间距18）
    // 展示栏行起点: 49,67,85,103,121（间距18，5行）
    private static final int DISPLAY_X = 8;
    private static final int DISPLAY_Y = 49;
    private static final int DISPLAY_COLS = 9;               // 横向 9 列（创造模式标准）
    private static final int SLOT = 18;
    private static final int DISPLAY_ROWS_VISIBLE = 5;       // 竖向 5 行可见

    private static final int SCROLL_X = 174;
    private static final int SCROLL_Y = 49;
    private static final int SCROLL_W = 13;
    private static final int SCROLL_H = 111;

    private EditBox searchBox;

    /** 当前滚动偏移（以「行」为单位，0 表示顶部）。 */
    private int scrollRow = 0;
    private boolean draggingScroll = false;

    /** 过滤后的展示物品（依据搜索词）。 */
    private List<ItemStack> filtered = new ArrayList<>();

    public LightCompassScreen(LightCompassMenu menu, net.minecraft.world.entity.player.Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 195;
        this.imageHeight = 168;
        this.inventoryLabelX = 9999; // 关掉「Inventory」文字
        this.titleLabelX = 9999;    // 关掉默认标题
    }

    @Override
    protected void init() {
        super.init();
        // 搜索栏：相对坐标 (80,28) → 屏幕绝对坐标 = leftPos+80, topPos+28
        searchBox = new EditBox(this.font, this.leftPos + 80, this.topPos + 28, 89, 11,
            Component.translatable("container.yizxianmod.light_compass.search"));
        searchBox.setHint(Component.literal("搜索...").withStyle(s -> s.withColor(0x808080)));
        searchBox.setResponder(this::onSearch);
        this.addRenderableWidget(searchBox);

        rebuildFiltered();
        // 不默认聚焦搜索框：玩家手动点击才聚焦，否则键盘用于操作快捷栏
    }

    private void onSearch(String query) {
        rebuildFiltered();
        scrollRow = 0;
    }

    /** 依据搜索词重建过滤列表。 */
    private void rebuildFiltered() {
        filtered.clear();
        String q = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";
        for (ItemStack stack : menu.getDisplayItems()) {
            if (q.isEmpty()) {
                filtered.add(stack);
            } else {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(q) || stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(stack);
                }
            }
        }
    }

    /** 展示栏可见的物品范围。 */
    private int displayVisibleCount() {
        return DISPLAY_COLS * DISPLAY_ROWS_VISIBLE;
    }
    private int maxScrollRow() {
        int totalRows = (filtered.size() + DISPLAY_COLS - 1) / DISPLAY_COLS;
        return Math.max(0, totalRows - DISPLAY_ROWS_VISIBLE);
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        // 展示栏物品是「虚拟槽」，不在 Menu 的 Slot 列表里，所以原版 renderSlot 不会画。
        // 这里在 super.render（已画背景+真实槽位）之后，用屏幕绝对坐标补画展示物品 + 滚动条。
        renderDisplayItems(g, mouseX, mouseY);
        // 原版 renderTooltip 只处理真实 Slot；展示栏是虚拟槽，这里自己画 tooltip
        ItemStack hovered = getHoveredDisplayItem(mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        } else {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    /** 返回鼠标当前悬停的展示栏物品（用于 tooltip + 点击共用同一判定）。 */
    private ItemStack getHoveredDisplayItem(double mouseX, double mouseY) {
        int idx = displayIndexAt(mouseX, mouseY);
        if (idx < 0) return null;
        int listIdx = scrollRow * DISPLAY_COLS + idx;
        if (listIdx < 0 || listIdx >= filtered.size()) return null;
        return filtered.get(listIdx);
    }

    /** 画展示栏虚拟物品 + 滚动条。基准与背景一致用 leftPos-1/topPos-1（见 gui-bg-offset），
     *  这样物品图标才能对齐纹理上的槽位框（纹理框已随背景偏 -1）。 */
    private void renderDisplayItems(GuiGraphics g, int mouseX, int mouseY) {
        int ox = this.leftPos - 1;  // 绘制基准，与背景 blit 一致
        int oy = this.topPos - 1;
        int clipLeft = ox + DISPLAY_X;
        int clipTop = oy + DISPLAY_Y;
        int clipRight = ox + DISPLAY_X + DISPLAY_COLS * SLOT;
        int clipBottom = oy + DISPLAY_Y + DISPLAY_ROWS_VISIBLE * SLOT;
        g.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        int baseIdx = scrollRow * DISPLAY_COLS;
        for (int i = 0; i < displayVisibleCount(); i++) {
            int listIdx = baseIdx + i;
            if (listIdx >= filtered.size()) break;
            int col = i % DISPLAY_COLS;
            int row = i / DISPLAY_COLS;
            int sx = ox + DISPLAY_X + col * SLOT;
            int sy = oy + DISPLAY_Y + row * SLOT;
            ItemStack stack = filtered.get(listIdx);
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(this.font, stack, sx + 1, sy + 1);
            // 高亮鼠标悬停的展示格：物品画在格子内侧 (sx+1, sy+1) 共 16×16，高亮要对齐物品位置
            if (mouseX >= sx + 1 && mouseX < sx + 1 + 16 && mouseY >= sy + 1 && mouseY < sy + 1 + 16) {
                g.fill(sx + 1, sy + 1, sx + 1 + 16, sy + 1 + 16, 0x40FFFFFF);
            }
        }
        g.disableScissor();

        // 滚动条（也对齐纹理）
        drawScrollbar(g, ox, oy);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        // 背景：项目惯例用 leftPos-1/topPos-1 偏移（见记忆 gui-bg-offset），
        // 这样 slot 的 leftPos+slot.x 才与纹理上的槽位框对齐，否则整体偏 1px。
        // 同时必须用 9 参数 blit 显式传纹理真实尺寸 195×168（7 参数版硬编码 256×256 会拉伸）。
        int lx = this.leftPos - 1;
        int ty = this.topPos - 1;
        if (BACKGROUND != null) {
            g.blit(BACKGROUND, lx, ty, 0, 0.0f, 0.0f, this.imageWidth, this.imageHeight, 195, 168);
        } else {
            g.fill(lx, ty, lx + this.imageWidth, ty + this.imageHeight, 0xFF2B2B2B);
        }
    }

    private void drawScrollbar(GuiGraphics g, int ox, int oy) {
        int x = ox + SCROLL_X;
        int y = oy + SCROLL_Y;
        // 轨道
        g.fill(x, y, x + SCROLL_W, y + SCROLL_H, 0xFF222222);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + SCROLL_H - 1, 0xFF555555);
        // 滑块
        int max = maxScrollRow();
        int trackH = SCROLL_H - 4;
        int thumbH = max <= 0 ? trackH : Math.max(10, trackH * DISPLAY_ROWS_VISIBLE / ((filtered.size() + DISPLAY_COLS - 1) / DISPLAY_COLS));
        int thumbY = max <= 0 ? y + 2 : y + 2 + (trackH - thumbH) * scrollRow / max;
        g.fill(x + 2, thumbY, x + SCROLL_W - 2, thumbY + thumbH, 0xFFAAAAAA);
    }

    // ── 交互 ──

    /** 鼠标点击：展示栏左键入工作槽 / 工作槽左键移除 / 滚动条拖动 / 点外部使搜索框失焦。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条（自绘，对齐纹理 → 用 leftPos-1 基准，与 drawScrollbar 一致）
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        if (mouseX >= ox + SCROLL_X && mouseX < ox + SCROLL_X + SCROLL_W
                && mouseY >= oy + SCROLL_Y && mouseY < oy + SCROLL_Y + SCROLL_H) {
            draggingScroll = true;
            setScrollByY(mouseY);
            searchBox.setFocused(false); // 点滚动条 → 搜索框失焦
            return true;
        }

        // 展示栏点击：左键 → 发包送入工作槽（服务端权威，客户端改容器不会反向同步）
        int dispIdx = displayIndexAt(mouseX, mouseY);
        if (dispIdx >= 0 && button == 0) {
            int listIdx = scrollRow * DISPLAY_COLS + dispIdx;
            if (listIdx < filtered.size()) {
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.sendAdd(filtered.get(listIdx));
            }
            searchBox.setFocused(false); // 点展示栏 → 搜索框失焦
            return true;
        }

        // 工作槽左键 → 若已占用则发包移除（真实 WorkSlot 位置 → leftPos 基准）
        int workIdx = workSlotIndexAt(mouseX, mouseY);
        if (workIdx >= 0 && button == 0) {
            if (this.menu.isWorkSlotOccupied(workIdx)) {
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.sendRemove(workIdx);
            }
            searchBox.setFocused(false);
            return true;
        }

        // 点在搜索框 → 显式获焦（自己调 searchBox.mouseClicked 处理光标定位 + Screen.setFocused 让它收键盘）；
        // 点在别处 → 清焦点，搜索框失焦，键盘恢复操作快捷栏。
        boolean inSearch = mouseX >= searchBox.getX() && mouseX < searchBox.getX() + searchBox.getWidth()
                        && mouseY >= searchBox.getY() && mouseY < searchBox.getY() + searchBox.getHeight();
        if (inSearch) {
            this.setFocused(searchBox);
            searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        } else {
            this.setFocused(null);
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScroll) {
            setScrollByY(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标在展示栏或滚动条上时滚动（自绘区域 → leftPos-1 基准）
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        if (mouseX >= ox + DISPLAY_X && mouseX < ox + DISPLAY_X + DISPLAY_COLS * SLOT + SCROLL_W
                && mouseY >= oy + DISPLAY_Y && mouseY < oy + DISPLAY_Y + DISPLAY_ROWS_VISIBLE * SLOT + 4) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void setScrollByY(double mouseY) {
        int trackTop = (this.topPos - 1) + SCROLL_Y + 2;
        int trackH = SCROLL_H - 4;
        double ratio = (mouseY - trackTop) / trackH;
        ratio = Math.max(0, Math.min(1, ratio));
        scrollRow = (int) Math.round(ratio * maxScrollRow());
    }

    /** 返回鼠标所在展示栏格的索引（0~displayVisibleCount-1），不在则 -1。
     *  基准与绘制一致用 leftPos-1；格子内物品画在 (格子+1, +1) 共 16×16，判定对齐物品区域。 */
    private int displayIndexAt(double mouseX, double mouseY) {
        int ox = this.leftPos - 1, oy = this.topPos - 1;
        int localX = (int) Math.round(mouseX) - (ox + DISPLAY_X);
        int localY = (int) Math.round(mouseY) - (oy + DISPLAY_Y);
        if (localX < 0 || localY < 0) return -1;
        int col = localX / SLOT;
        int row = localY / SLOT;
        if (col < 0 || col >= DISPLAY_COLS || row < 0 || row >= DISPLAY_ROWS_VISIBLE) return -1;
        return row * DISPLAY_COLS + col;
    }

    /** 返回鼠标所在工作槽索引（0~2），不在则 -1。工作槽 x=5/23/41，y=22。 */
    private int workSlotIndexAt(double mouseX, double mouseY) {
        for (int i = 0; i < LightCompassMenu.WORK_SLOT_COUNT; i++) {
            int sx = this.leftPos + 8 + i * SLOT;
            int sy = this.topPos + 22;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) return i;
        }
        return -1;
    }
}
