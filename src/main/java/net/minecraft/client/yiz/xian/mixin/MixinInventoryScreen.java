package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.yiz.xian.api.AccessorySlot;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为玩家生存背包界面绘制饰品槽位背景。
 *
 * <p>Target {@link InventoryScreen}（其 {@code renderBg} 有具体实现）。
 * 通过 {@link AbstractContainerScreenAccessor} 读取父类的 {@code leftPos}/{@code topPos}。</p>
 *
 * <p>槽位背景用纯色填充（风格贴近原版物品栏槽位），不再依赖外部 PNG 纹理，
 * 避免独立纹理在 GuiGraphics/RenderSystem 下的加载路径问题。</p>
 */
@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {

    /** 饰品槽行 Y 坐标（与 MixinInventoryMenu 的 ACCESSORY_Y 保持一致） */
    private static final int ACCESSORY_Y = 166;
    private static final int SLOT_SIZE = 18;

    /** 用户提供的槽位背景纹理（64×64 PNG）。路径必须含 .png 后缀！ */
    private static final ResourceLocation SLOT_BG =
        ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/gui/container/accessory_slot.png");
    /** 面板底色（原版物品栏背景色 #C6C6C6） */
    private static final int PANEL_FILL  = 0xFF_C6C6C6;
    /** 面板外框色 */
    private static final int PANEL_BORDER = 0xFF_373737;

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void yizxian_renderAccessoryBg(GuiGraphics g, float partialTick, int mouseX, int mouseY,
                                           CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;
        int slotCount = countAccessorySlots(self);
        if (slotCount <= 0) return;

        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) this;
        int leftPos = acc.getLeftPos();
        int topPos  = acc.getTopPos();

        // ── 面板区域 ──
        int panelLeft = leftPos + 7;
        int panelTop  = topPos + ACCESSORY_Y - 5;
        int panelW    = slotCount * SLOT_SIZE + 2;
        int panelH    = SLOT_SIZE + 10;

        // ── 1) 面板底色 ──
        g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, PANEL_FILL);

        // ── 2) 面板 1px 深色外框 ──
        g.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 1, PANEL_BORDER);
        g.fill(panelLeft, panelTop + panelH - 1, panelLeft + panelW, panelTop + panelH, PANEL_BORDER);
        g.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelH, PANEL_BORDER);
        g.fill(panelLeft + panelW - 1, panelTop, panelLeft + panelW, panelTop + panelH, PANEL_BORDER);

        // ── 3) 每个槽位 — model.png 纹理（64×64 → 18×18 缩放） ──
        for (int i = 0; i < slotCount; i++) {
            int sx = leftPos + 8 + i * SLOT_SIZE;
            int sy = topPos + ACCESSORY_Y;
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            float scale = (float) SLOT_SIZE / 64f;
            g.pose().scale(scale, scale, 1f);
            g.blit(SLOT_BG, 0, 0, 0f, 0f, 64, 64, 64, 64);
            g.pose().popPose();
        }
    }

    private static int countAccessorySlots(InventoryScreen screen) {
        int count = 0;
        for (var slot : screen.getMenu().slots) {
            if (slot instanceof AccessorySlot) count++;
        }
        return count;
    }
}
