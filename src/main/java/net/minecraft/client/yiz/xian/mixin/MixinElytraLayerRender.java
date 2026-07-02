package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让饰品槽中的鞘翅渲染在玩家背上。
 *
 * <p>1.21.1 中鞘翅模型由独立的 {@link ElytraLayer} 渲染（不是 {@code CapeLayer}），
 * 它通过 {@code getItemBySlot(EquipmentSlot.CHEST)} 判断是否渲染。
 * 本 Mixin 拦截该调用，胸甲槽无鞘翅时查饰品槽。</p>
 */
@Mixin(ElytraLayer.class)
public class MixinElytraLayerRender {

    @Redirect(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE", target = "net/minecraft/world/entity/LivingEntity.getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack yizxian_fakeChestElytra(LivingEntity entity, EquipmentSlot slot) {
        ItemStack real = entity.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST) return real;
        if (!real.isEmpty()) return real;

        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            AccessoryContainer c = AccessoryContainer.get(player);
            for (int i = 0; i < c.getSlotCount(); i++) {
                if (c.getItem(i).is(Items.ELYTRA)) return c.getItem(i);
            }
        }
        return real;
    }
}
