package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让饰品槽中的鞘翅也能渲染在玩家背上。
 *
 * <p>原版 {@link CapeLayer#render} 通过 {@code getItemBySlot(EquipmentSlot.CHEST)}
 * 判断是否穿鞘翅来决定渲染。本 Mixin 用 {@code @Redirect} 拦截该调用，
 * 若胸甲槽为空则查饰品槽，有鞘翅就返回给渲染代码。</p>
 *
 * <p>烟花火箭加速不需要额外 Mixin —— 原版 {@code FireworkRocketItem.use()}
 * 只检查 {@code isFallFlying()} 标志，已被本模组的三个鞘翅 Mixin 正确维护。</p>
 */
@Mixin(CapeLayer.class)
public class MixinCapeLayerRender {

    /**
     * 拦截 CapeLayer.render 中所有 getItemBySlot(EquipmentSlot) 调用。
     * 仅对 CHEST 槽做替换：空槽时检查饰品槽有无鞘翅。
     */
    @Redirect(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At(value = "INVOKE", target = "net/minecraft/client/player/AbstractClientPlayer.getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack yizxian_fakeChestElytraForRender(AbstractClientPlayer player, EquipmentSlot slot) {
        ItemStack real = player.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST) return real;
        if (!real.isEmpty()) return real; // 胸甲槽有物品，优先

        // 胸甲槽空 → 查饰品槽
        AccessoryContainer container = AccessoryContainer.get(player);
        for (int i = 0; i < container.getSlotCount(); i++) {
            ItemStack s = container.getItem(i);
            if (s.is(Items.ELYTRA)) return s;
        }
        return real;
    }
}
