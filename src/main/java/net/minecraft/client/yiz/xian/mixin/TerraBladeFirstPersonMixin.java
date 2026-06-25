package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.client.yiz.xian.render.FirstPersonSwordRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称：ILeftHandRender 武器统一走右手管线 + 关键帧动画。
 * 非武器物品保留原版渲染，保证兼容。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class TerraBladeFirstPersonMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void yizxian_fp(
            float pt, PoseStack ps, MultiBufferSource.BufferSource buf,
            LocalPlayer player, int light, CallbackInfo ci
    ) {
        ItemStack main = player.getMainHandItem();
        ItemStack off  = player.getOffhandItem();
        boolean m = main.getItem() instanceof ILeftHandRender;
        boolean o = off.getItem() instanceof ILeftHandRender;
        if (!m && !o) return;

        ci.cancel();
        ItemInHandRenderer self = (ItemInHandRenderer) (Object) this;
        int animIdx = ComboStateMachine.getCurrentAnimIndex(player);

        // 武器手（有 ILeftHandRender 的手）：关键帧动画
        if (m) renderWeapon(ps, buf, player, light, self, main, animIdx);
        if (o) renderWeapon(ps, buf, player, light, self, off,  animIdx);

        // 非武器手：保留原版渲染，不消失
        if (!m && !main.isEmpty()) {
            ps.pushPose();
            ps.translate(0.56F, -0.52F, -0.72F);
            self.renderItem(player, main, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps, buf, light);
            ps.popPose();
        }
        if (!o && !off.isEmpty()) {
            ps.pushPose();
            ps.translate(0.56F, -0.52F, -0.72F);
            self.renderItem(player, off, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps, buf, light);
            ps.popPose();
        }
    }

    private static void renderWeapon(PoseStack ps, MultiBufferSource.BufferSource buf,
                                     LocalPlayer player, int light, ItemInHandRenderer self,
                                     ItemStack stack, int animIdx) {
        ps.pushPose();
        ps.translate(0.56F, -0.52F, -0.72F);
        FirstPersonSwordRenderer.applyTransform(ps, animIdx, player, stack);
        self.renderItem(player, stack, ItemDisplayContext.NONE, false, ps, buf, light);
        ps.popPose();
    }
}
