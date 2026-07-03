package net.minecraft.client.yiz.xian.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD 管理器：注册表 + 渲染分发 + 位置/缩放/显隐访问。
 *
 * <p>所有 {@link HudElement} 注册于此。{@link #onRenderGui} 订阅
 * {@code RenderGuiEvent.Post}，按各元素的配置位置/scale 绘制。</p>
 *
 * <h3>editMode</h3>
 * <p>{@link HudEditorScreen} 打开时置 true，普通分发跳过（由编辑器自己绘制全部元素），
 * 避免 HUD 在编辑器下重影。Screen 关闭时回 false。</p>
 */
public final class HudManager {

    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();

    private static boolean editMode = false;

    private HudManager() {}

    // ── 注册表 ──

    public static void register(HudElement e) {
        ELEMENTS.put(e.getId(), e);
    }

    public static Collection<HudElement> all() {
        return ELEMENTS.values();
    }

    public static void setEditMode(boolean v) { editMode = v; }
    public static boolean isEditMode() { return editMode; }

    // ── 位置 / 缩放 / 开关（未配置时回退到元素默认）──

    public static int getX(HudElement e) {
        HudPositionConfig.Entry en = HudPositionConfig.get(e.getId());
        return en != null ? en.x() : e.getDefaultX();
    }

    public static int getY(HudElement e) {
        HudPositionConfig.Entry en = HudPositionConfig.get(e.getId());
        return en != null ? en.y() : e.getDefaultY();
    }

    public static float getScale(HudElement e) {
        HudPositionConfig.Entry en = HudPositionConfig.get(e.getId());
        return en != null ? en.scale() : e.getDefaultScale();
    }

    public static boolean isEnabled(HudElement e) {
        HudPositionConfig.Entry en = HudPositionConfig.get(e.getId());
        return en != null ? en.enabled() : true;
    }

    // ── 编辑器写入（实时持久化）──

    public static void setPosition(HudElement e, int x, int y) {
        HudPositionConfig.put(e.getId(), x, y, isEnabled(e), getScale(e));
    }

    public static void setScale(HudElement e, float scale) {
        HudPositionConfig.put(e.getId(), getX(e), getY(e), isEnabled(e), scale);
    }

    public static void setEnabled(HudElement e, boolean enabled) {
        HudPositionConfig.put(e.getId(), getX(e), getY(e), enabled, getScale(e));
    }

    // ── 渲染分发（订阅 RenderGuiEvent.Post）──

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (editMode) return;                                // 编辑器接管
        if (Minecraft.getInstance().screen != null) return;  // 有其它 Screen 打开时不画
        GuiGraphics g = event.getGuiGraphics();
        for (HudElement e : ELEMENTS.values()) {
            if (!isEnabled(e)) continue;
            drawElement(g, e, false);
        }
    }

    /**
     * 绘制单个元素（正常分发与编辑器共用）。
     * PoseStack 平移到元素位置并按 scale 缩放，元素在 (0,0) 用逻辑尺寸绘制。
     */
    public static void drawElement(GuiGraphics g, HudElement e, boolean editMode) {
        int x = getX(e), y = getY(e);
        float s = getScale(e);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        if (s != 1f) pose.scale(s, s, 1);
        e.render(g, editMode);
        pose.popPose();
    }
}
