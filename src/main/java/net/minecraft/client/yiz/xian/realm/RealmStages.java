package net.minecraft.client.yiz.xian.realm;

import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.RealmStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * 注册 Yiz Xian 四个境界，对齐文档站蓝图数值。
 * <p>
 * {@link #BLUEPRINT} 存全部 8 种属性每阶段的原始数值。
 * 乘法属性（伤害增幅、攻速、减伤、饱食加成）也通过
 * {@link RealmStage#attributeModifiers()} 注册，走前置库累积乘算；
 * 加法属性由 {@link RealmAttributeHandler} 单独遍历求和。
 * </p>
 *
 * <h3>蓝图累积总值（到证我）</h3>
 * 生命+80 / 攻击+53 / 伤害增幅180% / 攻速-99% /
 * 减伤95% / 格挡30 / 回复18/秒 / 饱食回复+400%
 */
public final class RealmStages {

    private RealmStages() {}

    public static final ResourceLocation BUILD_DESTINY = id("build_destiny");
    public static final ResourceLocation REALIZE_SELF  = id("realize_self");
    public static final ResourceLocation FAREWELL      = id("farewell");
    public static final ResourceLocation WITNESS_SELF  = id("witness_self");

    /**
     * 全量蓝图：境界ID → (属性名 → 每阶段数值)。
     * 加法属性存原始值，乘法属性也存原始值（例如 damage_reduction=0.10 表示 +10% 减免）。
     */
    static final Map<ResourceLocation, Map<String, Double>> BLUEPRINT = new LinkedHashMap<>();

    static {
        BLUEPRINT.put(BUILD_DESTINY, Map.of(
            "max_health_bonus",   10.0,
            "attack_bonus",        3.0,
            "damage_amplification", 0.10,
            "attack_speed",        0.20,
            "damage_reduction",    0.10,
            "damage_block",        1.0,
            "health_regen",        0.5,
            "food_bonus",          0.50
        ));
        BLUEPRINT.put(REALIZE_SELF, Map.of(
            "max_health_bonus",   16.0,
            "attack_bonus",        9.0,
            "damage_amplification", 0.30,
            "attack_speed",        0.30,
            "damage_reduction",    0.15,
            "damage_block",        3.0,
            "health_regen",        1.5,
            "food_bonus",          0.50
        ));
        BLUEPRINT.put(FAREWELL, Map.of(
            "max_health_bonus",   24.0,
            "attack_bonus",       14.0,
            "damage_amplification", 0.20,
            "attack_speed",        0.40,
            "damage_reduction",    0.25,
            "damage_block",        7.0,
            "health_regen",        4.0,
            "food_bonus",          1.0
        ));
        BLUEPRINT.put(WITNESS_SELF, Map.of(
            "max_health_bonus",   30.0,
            "attack_bonus",       27.0,
            "damage_amplification", 1.20,
            "attack_speed",        0.09,
            "damage_reduction",    0.45,
            "damage_block",       19.0,
            "health_regen",       12.0,
            "food_bonus",          3.00
        ));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("yizxianmod", name);
    }

    /**
     * 注册四个境界到前置库。
     * 乘法属性走 attributeModifiers 累积乘算；
     * 加法属性通过 {@link #sumAdditive(Player, String)} 单独求和。
     */
    public static void register() {
        RealmProgressionAPI.registerStage(new RealmStage(
            BUILD_DESTINY, 0, "筑命",
            Map.of("damage_amplification", 1.10,
                   "damage_reduction", 0.90, "food_bonus", 1.50)
        ));

        RealmProgressionAPI.registerStage(new RealmStage(
            REALIZE_SELF, 1, "谌我",
            Map.of("damage_amplification", 1.30,
                   "damage_reduction", 0.85, "food_bonus", 1.50)
        ));

        RealmProgressionAPI.registerStage(new RealmStage(
            FAREWELL, 2, "揖别",
            Map.of("damage_amplification", 1.20,
                   "damage_reduction", 0.75, "food_bonus", 2.00)
        ));

        RealmProgressionAPI.registerStage(new RealmStage(
            WITNESS_SELF, 3, "证我",
            Map.of("damage_amplification", 2.20,
                   "damage_reduction", 0.55, "food_bonus", 4.00)
        ));
    }

    // ==================== 累积计算 ====================

    /**
     * 加法属性累积：遍历所有已达成境界求和。
     */
    public static double sumAdditive(Player player, String key) {
        RealmStage current = RealmProgressionAPI.getCurrentStage(player);
        if (current == null) return 0;
        double sum = 0;
        for (RealmStage stage : RealmProgressionAPI.getAllStages()) {
            Map<String, Double> mods = BLUEPRINT.get(stage.id());
            if (mods != null) sum += mods.getOrDefault(key, 0.0);
            if (stage.id().equals(current.id())) break;
        }
        return sum;
    }

    /**
     * 乘法属性累积：走前置库 {@link RealmProgressionAPI#getCumulativeModifiers(Player)}。
     */
    public static double productMultiplier(Player player, String key) {
        return RealmProgressionAPI.getCumulativeModifiers(player).getOrDefault(key, 1.0);
    }
}
