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
 * 第一人称：检测 ILeftHandRender，用关键帧动画渲染主手武器。
 * <p>
 * 渲染方式复刻原版：基础右手位 translate(0.56,-0.52,-0.72)
 * → {@link FirstPersonSwordRenderer} 插值后的 firstperson_righthand 变换
 * → 物品几何（NONE 上下文，不再叠加 display 变换）。
 * <p>
 * 关键帧由用户在 Blockbench 中用 firstperson_righthand display transform 摆出，
 * 攻击进度 {@code getAttackAnim} 驱动 1→2→3→4 关键帧插值。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class TerraBladeFirstPersonMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void yizxian_fp(
            float pt, PoseStack ps, MultiBufferSource.BufferSource buf,
            LocalPlayer player, int light, CallbackInfo ci
    ) {
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof ILeftHandRender)) return;

        ci.cancel();
        ItemInHandRenderer self = (ItemInHandRenderer) (Object) this;

        int animIdx = ComboStateMachine.getCurrentAnimIndex(player);

        ps.pushPose();
        ps.translate(0.56F, -0.52F, -0.72F);             // 原版基础右手位
        FirstPersonSwordRenderer.applyTransform(ps, animIdx, player);
        self.renderItem(player, main,
            ItemDisplayContext.NONE, false, ps, buf, light);
        ps.popPose();
    }
}
