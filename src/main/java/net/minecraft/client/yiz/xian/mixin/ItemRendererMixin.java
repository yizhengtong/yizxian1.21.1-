package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.yiz.xian.item.TerraBladeItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对 TerraBladeItem 强制重映射 ItemDisplayContext + leftHand：
 *
 * <pre>
 * 第一人称 LEFT  → RIGHT + leftHand=false (永远右手展示，不镜像)
 * 第三人称 RIGHT → LEFT  + leftHand=true  (永远左手拔刀姿势)
 * </pre>
 *
 * <p>仅重映射 displayContext 不够——NeoForge 的
 * {@code ClientHooks.handleCameraTransforms} 同时依赖 leftHand 做镜像，
 * 必须一并修改。</p>
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
            PoseStack poseStack, MultiBufferSource bufferSource,
            int combinedLight, int combinedOverlay, BakedModel model,
            CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof TerraBladeItem)) return;

        ItemDisplayContext remappedCtx = context;
        boolean remappedLeft = leftHand;

        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            remappedCtx = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            remappedLeft = false; // 关闭左手镜像
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            remappedCtx = ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            remappedLeft = true; // 使用左手姿势
        }

        if (remappedCtx != context) {
            ci.cancel();
            ((ItemRenderer) (Object) this).render(
                stack, remappedCtx, remappedLeft,
                poseStack, bufferSource,
                combinedLight, combinedOverlay, model
            );
        }
    }
}
