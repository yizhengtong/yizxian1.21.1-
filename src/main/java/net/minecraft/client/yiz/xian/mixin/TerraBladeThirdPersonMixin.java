package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称：取消 ILeftHandRender 武器的手持渲染。
 * 武器由 {@link net.minecraft.client.yiz.xian.render.ThirdPersonWeaponRenderer} 在 RenderLevelStageEvent 中独立渲染。
 */
@Mixin(ItemInHandLayer.class)
public abstract class TerraBladeThirdPersonMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void yizxian_tp(
            LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
            PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof ILeftHandRender)) return;
        if (arm == HumanoidArm.LEFT) return;
        ci.cancel();
    }
}
