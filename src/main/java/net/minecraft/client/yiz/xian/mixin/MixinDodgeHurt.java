package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.handler.AccessoryProtectionHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 闪避消费 —— 在 {@code hurt()} HEAD 检查玩家是否预存了闪避。
 * 若有 → 先开 yiz 无敌 → 放行 hurt() → ProtectedServerPlayer.hurt() 返回 false → 伤害被吞。
 */
@Mixin(LivingEntity.class)
public abstract class MixinDodgeHurt {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizxian$dodgeBeforeHurt(
            net.minecraft.world.damagesource.DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (AccessoryProtectionHandler.consumeDodgeIfPresent(player)) {
            // 闪避已消费 → 无敌已开 → 让 hurt() 继续 → 被吞
            // 不需要 cancel——hurt() 会走到 ProtectedServerPlayer.hurt() 返回 false
        }
    }
}
