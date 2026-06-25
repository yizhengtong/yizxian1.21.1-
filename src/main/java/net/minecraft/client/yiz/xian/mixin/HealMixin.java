package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.xian.realm.RealmStages;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回复拦截 Mixin — 拦截 {@link LivingEntity#heal(float)}。
 * <ul>
 *   <li>禁疗实体 → 取消回血</li>
 *   <li>玩家饱食度 ≥20 时增幅部分走 Delta 通道</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class HealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(amount > 0)) return;

        // 禁疗检查（所有实体）
        if (TerraprismaRenderHandler.isAntiHealed(self.getUUID())) {
            ci.cancel();
            return;
        }

        // 玩家饱食回复增幅
        if (!(self instanceof Player player)) return;
        if (player.getFoodData().getFoodLevel() < 20) return;

        double foodBonus = RealmStages.sumAdditive(player, "food_bonus");
        if (foodBonus > 0) {
            YizModQZKAPI.healthRegen(player, amount * (float) foodBonus);
        }
    }
}
