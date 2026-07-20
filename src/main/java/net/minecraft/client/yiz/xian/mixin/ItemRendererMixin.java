package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ILeftHandRender 物品左右手重映射。
 * FIRST_PERSON_LEFT_HAND → FIRST_PERSON_RIGHT_HAND
 * THIRD_PERSON_RIGHT_HAND → THIRD_PERSON_LEFT_HAND
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
