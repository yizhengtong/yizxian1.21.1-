package net.minecraft.client.yiz.xian.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HUD 编辑器（DEL+ALT 打开）。
 *
 * <p>所有注册的 {@link HudElement} 强制全部显示（含 disabled，灰显）：
 * <ul>
 *   <li>左键拖动位置</li>
 *   <li>滚轮缩放（鼠标悬停在元素上滚动）</li>
 *   <li>右键 / 中键切换显隐</li>
 *   <li>ESC 保存退出</li>
 * </ul>
 * 不暂停游戏（{@code isPauseScreen=false}），所见即所得。</p>
 */
public class HudEditorScreen extends Screen {

    private static final float SCALE_MIN = 0.5f;
    private static final float SCALE_MAX = 3.0f;
    private static final float SCALE_STEP = 0.1f;

    private final HudDragState drag = new HudDragState();
    private Button resetBtn;

    public HudEditorScreen() {
        super(Component.literal("HUD 编辑器"));
    }

    @Override
    protected void init() {
        super.init();
        HudManager.setEditMode(true);
        resetBtn = Button.builder(Component.literal("§c重置全部到默认"),
                b -> HudPositionConfig.clear())
                .bounds(width - 130, height - 24, 120, 16)
                .build();
        addRenderableWidget(resetBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // 不调 super，禁用背景模糊，画半透明遮罩
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        for (HudElement e : HudManager.all()) {
            HudManager.drawElement(g, e, true);
            drawFrame(g, e, e == drag.selected, hitElement(e, mx, my), !HudManager.isEnabled(e));
        }

        // 顶部提示
        g.drawString(font, "§f§lHUD 编辑器  §r§7左键拖动 · 滚轮缩放 · 右键/中键显隐 · ESC 保存退出",
                8, 6, 0xFFFFFFFF);

        super.render(g, mx, my, pt);  // 画按钮
    }

    // ── 选中框 / 四角手柄 / id 标签 ──

    private void drawFrame(GuiGraphics g, HudElement e, boolean selected, boolean hover, boolean disabled) {
        int x = HudManager.getX(e), y = HudManager.getY(e);
        float s = HudManager.getScale(e);
        int w = (int) (e.getLogicalWidth() * s);
        int h = (int) (e.getLogicalHeight() * s);
        int col;
        if (disabled) col = 0x55888888;
        else if (selected) col = 0xFFFFD700;
        else if (hover) col = 0x88FFFFFF;
        else col = 0x55FFFFFF;
        // 1px 边框
        g.fill(x - 1, y - 1, x, y + h + 1, col);
        g.fill(x + w, y - 1, x + w + 1, y + h + 1, col);
        g.fill(x - 1, y - 1, x + w + 1, y, col);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, col);
        // id 标签（上方）
        g.drawString(font, (disabled ? "§8§m" : "§e") + e.getId(), x, y - 10, 0xFFFFFFFF);
        // 选中时四角手柄（视觉提示，实际缩放用滚轮）
        if (selected) {
            int t = 3;
            g.fill(x - t, y - t, x + t + 1, y + t + 1, 0xFFFFFF00);
            g.fill(x + w - t, y - t, x + w + t + 1, y + t + 1, 0xFFFFFF00);
            g.fill(x - t, y + h - t, x + t + 1, y + h + t + 1, 0xFFFFFF00);
            g.fill(x + w - t, y + h - t, x + w + t + 1, y + h + t + 1, 0xFFFFFF00);
        }
    }

    // ── 命中测试（屏幕坐标，含 scale）──

    private boolean hitElement(HudElement e, double mx, double my) {
        int x = HudManager.getX(e), y = HudManager.getY(e);
        float s = HudManager.getScale(e);
        return mx >= x && mx <= x + e.getLogicalWidth() * s
            && my >= y && my <= y + e.getLogicalHeight() * s;
    }

    private void clampToScreen(HudElement e, int[] xy) {
        float s = HudManager.getScale(e);
        int w = (int) (e.getLogicalWidth() * s);
        int h = (int) (e.getLogicalHeight() * s);
        xy[0] = Math.max(0, Math.min(width - w, xy[0]));
        xy[1] = Math.max(0, Math.min(height - h, xy[1]));
    }

    // ── 鼠标 ──

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;  // 按钮优先

        List<HudElement> list = new ArrayList<>(HudManager.all());
        Collections.reverse(list);  // 上层优先
        HudElement hit = null;
        for (HudElement e : list) {
            if (hitElement(e, mx, my)) { hit = e; break; }
        }

        if (hit == null) {
            drag.reset();
            return true;
        }

        // 右键 / 中键 → 切换显隐
        if (btn == GLFW.GLFW_MOUSE_BUTTON_2 || btn == GLFW.GLFW_MOUSE_BUTTON_3) {
            HudManager.setEnabled(hit, !HudManager.isEnabled(hit));
            drag.selected = hit;
            drag.mode = HudDragState.Mode.NONE;
            return true;
        }

        if (btn != GLFW.GLFW_MOUSE_BUTTON_1) return true;

        // 左键 → 选中并进入拖拽
        drag.selected = hit;
        drag.mode = HudDragState.Mode.DRAG;
        drag.dragOffX = mx - HudManager.getX(hit);
        drag.dragOffY = my - HudManager.getY(hit);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (drag.selected == null || drag.mode != HudDragState.Mode.DRAG) {
            return super.mouseDragged(mx, my, btn, dx, dy);
        }
        HudElement e = drag.selected;
        int[] xy = { (int) (mx - drag.dragOffX), (int) (my - drag.dragOffY) };
        clampToScreen(e, xy);
        HudManager.setPosition(e, xy[0], xy[1]);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        // 松手退出 DRAG，但保留 selected（可继续滚轮缩放，直至点击空白）
        if (drag.mode == HudDragState.Mode.DRAG) {
            drag.mode = HudDragState.Mode.NONE;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        // 优先缩放选中元素；否则缩放鼠标下悬停元素
        HudElement target = drag.selected;
        if (target == null || !hitElement(target, mx, my)) {
            target = null;
            List<HudElement> list = new ArrayList<>(HudManager.all());
            Collections.reverse(list);
            for (HudElement e : list) {
                if (hitElement(e, mx, my)) { target = e; drag.selected = e; break; }
            }
        }
        if (target == null) return super.mouseScrolled(mx, my, sx, sy);
        float ns = Math.max(SCALE_MIN, Math.min(SCALE_MAX,
                HudManager.getScale(target) + (float) sy * SCALE_STEP));
        HudManager.setScale(target, ns);
        return true;
    }

    // ── 键盘 / 生命周期 ──

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        HudPositionConfig.save();
        super.onClose();
    }

    @Override
    public void removed() {
        // 确保任意方式退出（切换 Screen / 关游戏）都恢复普通 HUD 分发
        HudManager.setEditMode(false);
    }
}
