package net.minecraft.client.yiz.xian.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 面板纹理拼装工具。
 *
 * <p>封装 GUI 积木块的绘制逻辑：四角(5×5) + 四边(1×5/5×1) + 填充(1×1) + 槽位(18×18)。</p>
 * <p>遵循 Minecraft GUI 纹理系统的 -1px 偏移规则。</p>
 */
public final class PanelRenderHelper {

    private static final ResourceLocation TEX_FILL = tex("fill_white");
    private static final ResourceLocation TEX_SLOT = tex("slot_default");
    private static final ResourceLocation TEX_TL   = tex("border_corner_tl");
    private static final ResourceLocation TEX_TR   = tex("border_corner_tr");
    private static final ResourceLocation TEX_BL   = tex("border_corner_bl");
    private static final ResourceLocation TEX_BR   = tex("border_corner_br");
    private static final ResourceLocation TEX_TOP    = tex("border_edge_top");
    private static final ResourceLocation TEX_BOTTOM = tex("border_edge_bottom");
    private static final ResourceLocation TEX_LEFT   = tex("border_edge_left");
    private static final ResourceLocation TEX_RIGHT  = tex("border_edge_right");

    public static final int BORDER = 5;
    public static final int SLOT   = 18;

    private PanelRenderHelper() {}

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        int innerW = w - BORDER * 2;
        int innerH = h - BORDER * 2;
        if (innerW > 0 && innerH > 0) {
            g.blit(TEX_FILL, x + BORDER, y + BORDER, 0, 0, innerW, innerH, 1, 1);
        }
        g.blit(TEX_TOP,    x + BORDER, y,               0, 0, innerW, BORDER, 1, BORDER);
        g.blit(TEX_BOTTOM, x + BORDER, y + h - BORDER,  0, 0, innerW, BORDER, 1, BORDER);
        g.blit(TEX_LEFT,   x,          y + BORDER,      0, 0, BORDER, innerH, BORDER, 1);
        g.blit(TEX_RIGHT,  x + w - BORDER, y + BORDER,  0, 0, BORDER, innerH, BORDER, 1);
        g.blit(TEX_TL, x, y,                   0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_TR, x + w - BORDER, y,      0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_BL, x, y + h - BORDER,      0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_BR, x + w - BORDER, y + h - BORDER, 0, 0, BORDER, BORDER, BORDER, BORDER);
    }

    public static void drawSlot(GuiGraphics g, int tx, int ty) {
        g.blit(TEX_SLOT, tx, ty, 0, 0, SLOT, SLOT, SLOT, SLOT);
    }

    /**
     * 绘制自定义槽位背景 — 将 64×64 纹理等比缩放到 SLOT×SLOT 槽位尺寸。
     * tx/ty 需已含 -1 偏移。
     */
    public static void drawCustomSlot(GuiGraphics g, ResourceLocation tex, int tx, int ty) {
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        float scale = (float) SLOT / 64f;
        g.pose().scale(scale, scale, 1f);
        g.blit(tex, 0, 0, 0, 0, 64, 64, 64, 64);
        g.pose().popPose();
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            "yizxianmod", "textures/gui/container/" + name + ".png"
        );
    }
}
