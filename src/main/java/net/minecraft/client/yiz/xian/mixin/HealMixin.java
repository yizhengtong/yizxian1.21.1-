package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.xian.realm.RealmStages;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 饱食回复增幅 Mixin —— 拦截 {@link LivingEntity#heal(float)}。
 * <p>
 * 当玩家饱食度 ≥20 时，原版回血不变，
 * 增幅部分（healAmount × food_bonus）走 {@link YizModQZKAPI#healthRegen} Delta 通道。
 * </p>
 */
@Mixin(LivingEntity.class)
public class HealMixin {

    @Inject(method = "heal", at = @At("HEAD"))
    private void onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (player.getFoodData().getFoodLevel() < 20) return;
        if (!(amount > 0)) return;

        // 原回血照常走（不取消），增幅部分走 Delta 通道
        double foodBonus = RealmStages.sumAdditive(player, "food_bonus");
        if (foodBonus > 0) {
            YizModQZKAPI.healthRegen(player, amount * (float) foodBonus);
        }
    }
}
