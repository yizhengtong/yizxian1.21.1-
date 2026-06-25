package net.minecraft.client.yiz.xian.realm;

import net.minecraft.client.yiz.api.*;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 境界属性挂载 —— 统一走 {@link ItemAttributeHandler} 实体级 API。
 * <p>
 * 8 种境界属性全用 {@link RealmStages#sumAdditive(Player, String)} 取累积值，
 * 原版属性（生命/攻击/攻速）→ {@code ItemAttributeHandler.setEntityXxx()}
 * 自定义属性（伤害增幅/减免）→ {@code ItemAttributeHandler.setEntityDamageXxx()}
 * 格挡/复活 → 直接走前置库注册表回调。
 * 饱食回复加成 → 饱食 ≥20 时所有回血走 Delta 通道增幅。
 * </p>
 */
public final class RealmAttributeHandler {

    private RealmAttributeHandler() {}

    /** 在 YizxianMod 构造器中调用一次 */
    public static void register() {
        // —— 减伤 + 格挡 ——（直接在回调里取实时值，突破后自动生效）
        DamageReductionRegistry.register((entity, oldHealth, newHealth) -> {
            if (!(entity instanceof Player player)) return newHealth;
            double reduction = RealmStages.sumAdditive(player, "damage_reduction");
            double block = RealmStages.sumAdditive(player, "damage_block");
            if (reduction <= 0 && block <= 0) return newHealth;
            float damage = oldHealth - newHealth;
            float reduced = damage * (float) (1.0 - reduction) - (float) block;
            if (reduced < 0) reduced = 0;
            return oldHealth - reduced;
        });

        // —— 证我复活 ——
        UndyingRegistry.register((entity, source) -> {
            if (entity instanceof Player player) {
                RealmStage stage = RealmProgressionAPI.getCurrentStage(player);
                if (stage != null && stage.id().equals(RealmStages.WITNESS_SELF)) {
                    return new UndyingRegistry.ReviveResult(entity.getMaxHealth(), null);
                }
            }
            return UndyingRegistry.ReviveResult.NONE;
        });
    }

    /**
     * 应用/刷新全部境界属性。
     * 在突破后和玩家登录时调用。
     */
    public static void applyAttributes(ServerPlayer player) {
        double hpBonus  = RealmStages.sumAdditive(player, "max_health_bonus");
        double atkBonus = RealmStages.sumAdditive(player, "attack_bonus");
        double spdReduction = RealmStages.sumAdditive(player, "attack_speed");   // 正值累积，如 0.99
        double spdMult  = 1.0 / (1.0 - Math.min(spdReduction, 0.999));  // 攻速倍率，如 1000×
        double amp      = RealmStages.sumAdditive(player, "damage_amplification");

        ItemAttributeHandler.setEntityMaxHealth(player, hpBonus);
        ItemAttributeHandler.setEntityAttackDamage(player, atkBonus);
        ItemAttributeHandler.setEntityAttackSpeed(player, spdMult);
        ItemAttributeHandler.setEntityDamageAmplification(player, amp);

        // 突破后补满
        player.setHealth(player.getMaxHealth());
    }

    /**
     * 取饱食回复加成倍率。
     * <p>
     * 遍历所有已达成境界的 food_bonus 值求和：
     * 筑命 +50%、谌我 +50%、揖别 +100%、证我 +300%
     * → 证我累积 = 1.0 + 5.0 = 6.0（6 倍回血）。
     * </p>
     */
    public static float getTotalFoodBonus(Player player) {
        return (float) (1.0 + RealmStages.sumAdditive(player, "food_bonus"));
    }

    /** 每 tick 生命回复 —— 永远走 healthRegen Delta 通道，不参与饱食增幅 */
    public static void applyHealthRegen(ServerPlayer player) {
        double regenPerSecond = RealmStages.sumAdditive(player, "health_regen");
        if (regenPerSecond <= 0) return;

        float regenPerTick = (float) (regenPerSecond / 20.0);
        YizModQZKAPI.healthRegen(player, regenPerTick);
    }
}
