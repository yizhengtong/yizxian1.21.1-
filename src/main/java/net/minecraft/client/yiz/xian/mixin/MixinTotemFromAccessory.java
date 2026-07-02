package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让饰品槽中的不死图腾也能在死亡时触发。
 *
 * <p>参照 Charm of Undying 的实现模式：在 {@code checkTotemDeathProtection} 头部
 * 遍历饰品槽，找到不死图腾就触发效果并拦截死亡。</p>
 *
 * <p>不影响原版手持图腾的逻辑——原版仍然遍历双手检查图腾，本 Mixin 只是在此之前
 * 多加一层饰品槽检查。</p>
 */
@Mixin(LivingEntity.class)
public class MixinTotemFromAccessory {

    /**
     * 在 checkTotemDeathProtection 头部注入，优先检查饰品槽。
     * <p>cancellable=true：找到图腾后直接返回 true，不继续走原版手持逻辑。</p>
     */
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void yizxian_checkAccessoryTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (player.level().isClientSide) return;

        AccessoryContainer container = AccessoryContainer.get(player);
        for (int i = 0; i < container.getSlotCount(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                // 消耗图腾
                stack.shrink(1);
                container.setChanged();

                // 标准不死图腾效果
                player.setHealth(1.0F);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

                // 触发图腾动画（粒子 + 音效）
                player.level().broadcastEntityEvent(self, (byte) 35);

                cir.setReturnValue(true);
                return;
            }
        }
    }
}
