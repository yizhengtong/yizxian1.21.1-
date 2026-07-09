package net.minecraft.client.yiz.xian.item.terraria;

import net.minecraft.client.yiz.xian.api.terraria.AccessoryFlags;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.client.yiz.xian.api.terraria.JumpAttributes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * 泰拉瑞亚配饰通用类 —— 42 个泰拉配饰共用，靠 {@link #terrariaId} 区分。
 *
 * <p>2026-07-06 属性化改造后，每个饰品通过 {@link TerrariaCards.Card#values}
 * 和父级继承链提供 4 个跳跃属性，本类覆写 tooltip 显示这些数值
 * （仿泰拉刃 {@code MeleeWeaponItem.appendHoverText} 的排版风格）。</p>
 *
 * <p>装备/卸下/持久化全部复用 {@code AccessoryContainer}（SSOT 架构），
 * <b>不需要任何 onEquip/onUnequip 钩子</b>。</p>
 */
public class TerrariaAccessoryItem extends Item {

    private final int terrariaId;

    public TerrariaAccessoryItem(int terrariaId, Properties properties) {
        super(properties);
        this.terrariaId = terrariaId;
    }

    /** 该饰品的泰拉瑞亚内部 ID（如 53 = 云朵瓶）。供能力 Mixin 精确判定用。 */
    public int terrariaId() {
        return terrariaId;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                 List<Component> tooltip, TooltipFlag flag) {
        Map<EffectTag, Float> attrs = AccessoryFlags.resolveValues(terrariaId);
        // 组件注入覆盖 Card.values
        if (JumpAttributes.hasAny(stack)) {
            attrs.putAll(JumpAttributes.getWithDefaults(stack));
        }
        appendAttrStats(tooltip, attrs);
    }

    /**
     * 格式化全部数值属性 tooltip（仿泰拉刃排版：§9 标签名 §f 值）。
     * <p>非零属性才渲染（flag 类不在此处显示）。供本类和 {@code ItemTooltipEvent} 共用。</p>
     */
    /**
     * 通用数值属性 tooltip 渲染，按 {@link #ATTR_LABELS} 查标签名和格式。
     * <p>新增数值属性只需更新 {@code ATTR_LABELS} 和 {@code ATTR_ORDER}，不用改本方法。</p>
     */
    public static void appendAttrStats(List<Component> tooltip, Map<EffectTag, Float> attrs) {
        for (EffectTag tag : ATTR_ORDER) {
            float v = attrs.getOrDefault(tag, 0f);
            if (v <= 0) continue;
            String[] label = ATTR_LABELS.get(tag);
            if (label == null) continue;
            String fmtVal = fmt(v);
            // 格式：$9<标签> $f<值><后缀>  或  $9<标签> $f+<值><后缀>
            String prefix = label[1] != null && label[1].contains("%") ? "+" : "";
            tooltip.add(Component.literal(
                "§9" + label[0] + " §f" + prefix + fmtVal + (label[1] != null ? label[1] : "")));
        }
    }

    /**
     * 数值属性标签表：tag → [中文名, 后缀]。
     * 后缀含义：null=纯数值，"%"=百分比，"格"=格，"点"=点，"tick"=tick。
     */
    public static final java.util.Map<EffectTag, String[]> ATTR_LABELS = java.util.Map.<EffectTag, String[]>ofEntries(
        java.util.Map.entry(EffectTag.JUMP_COUNT,    new String[]{"跳跃次数", null}),
        java.util.Map.entry(EffectTag.JUMP_HEIGHT,   new String[]{"跳跃高度", " 格"}),
        java.util.Map.entry(EffectTag.FALL_SAFE,     new String[]{"安全距离", null}),
        java.util.Map.entry(EffectTag.FALL_REDUCE,   new String[]{"摔伤减免", " 点"}),
        java.util.Map.entry(EffectTag.MOVE_SPEED,    new String[]{"移动速度", "%"}),
        java.util.Map.entry(EffectTag.MAX_RUN_SPEED, new String[]{"最大奔跑速度", "%"}),
        java.util.Map.entry(EffectTag.JUMP_STRENGTH, new String[]{"跳跃力度", "%"}),
        java.util.Map.entry(EffectTag.AIR_SPEED,     new String[]{"空中移速", "%"}),
        java.util.Map.entry(EffectTag.ARMOR,         new String[]{"防御力", " 点"}),
        java.util.Map.entry(EffectTag.DAMAGE_REDUCTION,  new String[]{"减伤率", "%"}),
        java.util.Map.entry(EffectTag.DODGE_CHANCE,  new String[]{"闪避率", "%"}),
        java.util.Map.entry(EffectTag.INVINCIBILITY_MULT, new String[]{"无敌帧", "×"}),
        java.util.Map.entry(EffectTag.LAVA_IMMUNE_TIME,  new String[]{"熔岩免疫", " tick"}),
        java.util.Map.entry(EffectTag.LAVA_DAMAGE_REDUCTION, new String[]{"熔岩减伤", " 点"}),
        java.util.Map.entry(EffectTag.LIFE_REGEN_RATE,    new String[]{"生命再生", "/秒"}),
        java.util.Map.entry(EffectTag.LIFE_REGEN_PCT,    new String[]{"生命再生%", "%"}),
        java.util.Map.entry(EffectTag.GENERIC_DAMAGE,    new String[]{"全伤害", "%"}),
        java.util.Map.entry(EffectTag.MELEE_DAMAGE,      new String[]{"近战伤害", "%"}),
        java.util.Map.entry(EffectTag.RANGED_DAMAGE,     new String[]{"远程伤害", "%"}),
        java.util.Map.entry(EffectTag.MAGIC_DAMAGE,      new String[]{"魔法伤害", "%"}),
        java.util.Map.entry(EffectTag.SUMMON_DAMAGE,     new String[]{"召唤伤害", "%"}),
        java.util.Map.entry(EffectTag.CRIT_RATE,         new String[]{"暴击率", "%"}),
        java.util.Map.entry(EffectTag.ARMOR_PENETRATION, new String[]{"盔甲穿透", " 点"}),
        java.util.Map.entry(EffectTag.ATTACK_SPEED,      new String[]{"攻击速度", "%"}),
        java.util.Map.entry(EffectTag.ATTACK_KNOCKBACK,  new String[]{"击退力", "×"}),
        java.util.Map.entry(EffectTag.ATTACK_RANGE,      new String[]{"攻击范围", "%"}),
        java.util.Map.entry(EffectTag.FLIGHT_TIME,       new String[]{"飞行时间", " tick"}),
        java.util.Map.entry(EffectTag.JUMP_SPEED,        new String[]{"跳跃速度", "×"}),
        java.util.Map.entry(EffectTag.MAX_FALL_SAFE,     new String[]{"安全坠落", " 格"}),
        java.util.Map.entry(EffectTag.LUCK,              new String[]{"运气", " 点"}),
        java.util.Map.entry(EffectTag.MAX_MINIONS,       new String[]{"仆从上限", " 只"}),
        java.util.Map.entry(EffectTag.MAX_SENTRIES,      new String[]{"哨兵上限", " 座"}),
        java.util.Map.entry(EffectTag.WATER_BREATH_TIME, new String[]{"水下呼吸", " tick"}),
        java.util.Map.entry(EffectTag.ARROW_DAMAGE,      new String[]{"箭矢伤害", "%"}),
        java.util.Map.entry(EffectTag.ARROW_SPEED,       new String[]{"箭矢速度", "%"}),
        java.util.Map.entry(EffectTag.ARROW_SAVE_CHANCE, new String[]{"箭矢节省", "%"})
    );

    /** 渲染顺序（数值属性）：按此列表的顺序输出 tooltip 行。 */
    private static final EffectTag[] ATTR_ORDER = {
        EffectTag.JUMP_COUNT, EffectTag.JUMP_HEIGHT, EffectTag.FALL_SAFE, EffectTag.FALL_REDUCE,
        EffectTag.MOVE_SPEED, EffectTag.MAX_RUN_SPEED, EffectTag.AIR_SPEED,
        EffectTag.JUMP_STRENGTH, EffectTag.JUMP_SPEED,
        EffectTag.ARMOR, EffectTag.DAMAGE_REDUCTION, EffectTag.DODGE_CHANCE,
        EffectTag.INVINCIBILITY_MULT, EffectTag.KNOCKBACK_RESIST,
        EffectTag.LAVA_IMMUNE_TIME, EffectTag.LAVA_DAMAGE_REDUCTION,
        EffectTag.LIFE_REGEN_RATE, EffectTag.LIFE_REGEN_BOOST,
        EffectTag.GENERIC_DAMAGE, EffectTag.MELEE_DAMAGE, EffectTag.RANGED_DAMAGE,
        EffectTag.MAGIC_DAMAGE, EffectTag.SUMMON_DAMAGE,
        EffectTag.CRIT_RATE, EffectTag.ARMOR_PENETRATION,
        EffectTag.ATTACK_SPEED, EffectTag.ATTACK_KNOCKBACK, EffectTag.ATTACK_RANGE,
        EffectTag.FLIGHT_TIME, EffectTag.MAX_FALL_SAFE,
        EffectTag.LUCK, EffectTag.MAX_MINIONS, EffectTag.MAX_SENTRIES,
        EffectTag.WATER_BREATH_TIME,
        EffectTag.ARROW_DAMAGE, EffectTag.ARROW_SPEED, EffectTag.ARROW_SAVE_CHANCE
    };

    /** 整数显示不加 .0，非整数保留一位。 */
    private static String fmt(float v) {
        return v == Math.round(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }
}
