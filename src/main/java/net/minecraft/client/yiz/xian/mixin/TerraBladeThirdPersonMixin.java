package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimLoader;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称：renderArmWithItem 入口拦截，强制 arm=LEFT + context=THIRD_PERSON_LEFT_HAND，
 * 并在攻击时应用 Blockbench 动画旋转。
 */
@Mixin(ItemInHandLayer.class)
public abstract class TerraBladeThirdPersonMixin {

    @Invoker("renderArmWithItem")
    public abstract void invokeRenderArmWithItem(
        LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
        PoseStack ps, MultiBufferSource buf, int light
    );

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void yizxian_tp(
            LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
            PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof ILeftHandRender)) return;
        if (arm == HumanoidArm.LEFT) return;

        ci.cancel();

        // 应用攻击动画
        if (entity instanceof Player player) {
            float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            float swingProgress = player.getAttackAnim(pt);
            int animIdx = ComboStateMachine.getCurrentAnimIndex(player);
            if (swingProgress > 0.01f) {
                BlockbenchAnimLoader.applyAttack(ps, animIdx, swingProgress);
            }
        }

        invokeRenderArmWithItem(entity, stack,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
            ps, buf, light);
    }
}
