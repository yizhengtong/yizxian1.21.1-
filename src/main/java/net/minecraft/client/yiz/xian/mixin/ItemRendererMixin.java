package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品渲染拦截：第三人称攻击时替换 display transform 为 ThreadLocal 关键帧。
 * 第一人称 LEFT → RIGHT 重映射保留兼容。
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Z"
               + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
               + "IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yizxian_remapContext(
            ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack ps, MultiBufferSource buf,
            int light, int overlay, BakedModel model,
            CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof ILeftHandRender)) return;

        // ── 第三人称攻击动画：ThreadLocal 传来的关键帧 ──
        if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                && TerraBladeThirdPersonMixin.IS_ATTACKING.get()) {
            float[] kf = TerraBladeThirdPersonMixin.ANIM_BUF.get();
            if (kf != null) {
                ci.cancel();
                ps.pushPose();
                ps.translate(kf[3] / 16f, kf[4] / 16f, kf[5] / 16f);
                ps.mulPose(new Quaternionf().rotationXYZ(
                    (float)Math.toRadians(kf[0]), (float)Math.toRadians(kf[1]), (float)Math.toRadians(kf[2])));
                ps.scale(kf[6], kf[7], kf[8]);
                ps.mulPose(new Quaternionf()
                    .rotateZ((float)Math.toRadians(BlockbenchAnimParser.elemRotZ))
                    .rotateY((float)Math.toRadians(BlockbenchAnimParser.elemRotY))
                    .rotateX((float)Math.toRadians(BlockbenchAnimParser.elemRotX)));
                ((ItemRenderer)(Object)this).renderStatic(stack, ItemDisplayContext.NONE,
                    light, overlay, ps, buf, net.minecraft.client.Minecraft.getInstance().level, 0);
                ps.popPose();
                return;
            }
        }

        // ── 第一/三人称左右手重映射（兼容）──
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            ci.cancel();
            ((ItemRenderer)(Object)this).render(
                stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false,
                ps, buf, light, overlay, model);
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            ci.cancel();
            ((ItemRenderer)(Object)this).render(
                stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true,
                ps, buf, light, overlay, model);
        }
    }
}
