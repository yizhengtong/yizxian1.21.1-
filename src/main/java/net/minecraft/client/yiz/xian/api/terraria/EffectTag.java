package net.minecraft.client.yiz.xian.api.terraria;

import com.mojang.serialization.Codec;

/**
 * 泰拉瑞亚配饰效果标签（纯枚举，不带数值）。
 *
 * <p>每个标签代表一类能力。能力 Mixin 通过
 * {@link AccessoryFlags#has(net.minecraft.world.entity.player.Player, EffectTag)}
 * 查询玩家是否拥有该能力（含合成链继承解析），<b>不直接判定具体物品</b>
 * —— 新增饰品只要在配方卡上贴对标签，能力代码一行不改。</p>
 *
 * <p>数值（跳跃高度、免疫时长等）阶段 2 另放 {@code TerrariaCards.Card.values} 表回填，
 * 不污染本枚举结构。</p>
 */
public enum EffectTag {

    // ── 多段跳（每种跳一个独立跳槽） ──
    /** 云朵瓶系：云跳。 */
    JUMP_CLOUD,
    /** 暴雪瓶系：暴雪跳。 */
    JUMP_BLIZZARD,
    /** 沙暴瓶系：沙跳（跳得最高）。 */
    JUMP_SANDSTORM,

    // ── 跳跃属性（数值 tag，挂在 Card.values 上，2026-07-06 属性化改造） ──
    /** 该饰品提供的跳跃次数（按槽位顺序消耗）。 */
    JUMP_COUNT,
    /** 该饰品每次跳的高度（格），驱动 jumpVelocity 反解。 */
    JUMP_HEIGHT,
    /** 该饰品对摔伤安全距离的贡献（累加）。 */
    FALL_SAFE,
    /** 该饰品对摔伤伤害减免的贡献（累加）。 */
    FALL_REDUCE,

    // ── 移动属性（2026-07-06 阶段1） ──
    /** 移动速度加成（%，累加）。基础 0.1，最终 = 0.1 × (1 + Σ/100)。 */
    MOVE_SPEED,
    /** 最大奔跑速度加成（%，累加）。加速 3 秒达到 maxSpeed = walkSpeed × (1 + (50 + Σ)/100)。 */
    MAX_RUN_SPEED,
    /** 跳跃力度加成（%，累加）。基础 jump_strength 0.42，最终 = 0.42 × (1 + Σ/100)。 */
    JUMP_STRENGTH,
    /** 空中移速加成（%，累加）。起跳后在空中时移动速度提高，跳得更远但不更高。
     *  空中速度 = walkSpeed × (1 + Σ/100)。 */
    AIR_SPEED,

    // ── 防御 / 减伤数值（累加） ──
    /** 防御力（点）。 */
    ARMOR,
    /** 击退抗性（0~1，1=全免）。 */
    KNOCKBACK_RESIST,
    /** 减伤率（%）。 */
    DAMAGE_REDUCTION,
    /** 闪避几率（%）。 */
    DODGE_CHANCE,
    /** 无敌帧倍率（×，默认1）。 */
    INVINCIBILITY_MULT,
    /** 熔岩免疫时间（tick，20=1秒）。 */
    LAVA_IMMUNE_TIME,
    /** 熔岩伤害减免（点，80→35=减45点）。 */
    LAVA_DAMAGE_REDUCTION,
    /** 生命再生速率（/秒）。 */
    LIFE_REGEN_RATE,
    /** 生命再生百分比（%/秒）。每 1 点 = 每 tick 回复 0.05% 最大生命值。 */
    LIFE_REGEN_PCT,

    // ── 战斗属性（累加） ──
    /** 全伤害加成（%）。 */
    GENERIC_DAMAGE,
    /** 近战伤害（%）。 */
    MELEE_DAMAGE,
    /** 远程伤害（%）。 */
    RANGED_DAMAGE,
    /** 魔法伤害（%）。 */
    MAGIC_DAMAGE,
    /** 召唤伤害（%）。 */
    SUMMON_DAMAGE,
    /** 暴击率（%）。 */
    CRIT_RATE,
    /** 盔甲穿透（点）。 */
    ARMOR_PENETRATION,
    /** 攻击速度（%）。 */
    ATTACK_SPEED,
    /** 击退力（倍率，默认1）。 */
    ATTACK_KNOCKBACK,
    /** 攻击范围（%）。 */
    ATTACK_RANGE,

    // ── 飞行 / 机动数值 ──
    /** 飞行时间（tick，火箭靴系）。 */
    FLIGHT_TIME,
    /** 跳跃速度倍率（默认1，蛙腿=1.6）。 */
    JUMP_SPEED,
    /** 最大安全坠落距离增量（格）。 */
    MAX_FALL_SAFE,

    // ── 杂项数值 ──
    /** 运气（点，累加）。 */
    LUCK,
    /** 仆从上限增量（只）。 */
    MAX_MINIONS,
    /** 哨兵上限增量（座）。 */
    MAX_SENTRIES,
    /** 水下呼吸延长（tick）。 */
    WATER_BREATH_TIME,
    /** 箭矢伤害加成（%）。 */
    ARROW_DAMAGE,
    /** 箭矢速度加成（%）。 */
    ARROW_SPEED,
    /** 箭矢节省几率（%）。 */
    ARROW_SAVE_CHANCE,

    // ── 免疫 flag ──
    FALL_IMMUNE,
    /** 免疫燃烧。 */
    BURN_IMMUNE,
    /** 免疫中毒。 */
    POISON_IMMUNE,
    /** 免疫黑暗。 */
    DARKNESS_IMMUNE,
    /** 免疫诅咒。 */
    CURSE_IMMUNE,
    /** 免疫流血。 */
    BLEED_IMMUNE,
    /** 免疫困惑。 */
    CONFUSE_IMMUNE,
    /** 免疫缓慢。 */
    SLOW_IMMUNE,
    /** 免疫虚弱。 */
    WEAK_IMMUNE,
    /** 免疫沉默。 */
    SILENCE_IMMUNE,
    /** 免疫破损盔甲。 */
    BROKEN_ARMOR_IMMUNE,
    /** 免疫石化。 */
    STONE_IMMUNE,
    /** 免疫冷冻。 */
    CHILLED_IMMUNE,
    /** 免疫冰冻。 */
    FROZEN_IMMUNE,
    /** 免疫击退。 */
    KNOCKBACK_IMMUNE,

    // ── 能力 flag ──
    /** 无限飞行。 */
    INFINITE_FLIGHT,
    /** 自动跳跃。 */
    AUTO_JUMP,
    /** 漂浮（飞毯）。 */
    FLOAT,
    /** 冲刺攻击（克苏鲁护盾）。 */
    DASH_ATTACK,
    /** 重力翻转。 */
    GRAVITY_FLIP,
    /** 悬停。 */
    HOVER,
    /** 沙上加速。 */
    SAND_SPEED_BOOST,
    /** 近战自动挥舞。 */
    AUTO_SWING,
    /** 近战附加狱炎。 */
    MELEE_FIRE,
    /** 箭矢强化。 */
    ARROW_BUFF,
    /** 木箭→烈焰箭。 */
    WOODEN_TO_FLAMING,
    /** 水中向上游。 */
    WATER_SWIM,
    /** 水下呼吸延长。 */
    WATER_BREATH,
    /** 水中发光。 */
    WATER_GLOW,
    /** 水面行走。 */
    WATER_WALK,
    /** 无限呼吸。 */
    INFINITE_BREATH,
    /** 冰上防滑。 */
    ICE_SKATE,
    /** 行走长花。 */
    FLOWER_WALK,
    /** 静止生命再生。 */
    LIFE_REGEN_BOOST,
    /** 熔岩免疫计时。 */
    LAVA_IMMUNE,

    // ── 触发 / 被动攻击 flag ──
    /** 受伤放蜜蜂。 */
    HURT_SPAWN_BEES,
    /** 受伤落星。 */
    HURT_SPAWN_STARS,
    /** 受伤恐慌加速。 */
    HURT_PANIC_SPEED,
    /** 受伤困惑敌怪。 */
    HURT_CONFUSE_ENEMIES,
    /** 闪避后暴击/召唤伤害加成。 */
    DODGE_BUFF,
    /** 周期骨手套射弹。 */
    TICK_BONE_GLOVE,
    /** 周期挥发明胶。 */
    TICK_VOLATILE_GELATIN,
    /** 周期孢子攻击。 */
    TICK_SPORE_SAC,
    /** 周期暗影之手。 */
    TICK_SHADOW_HAND,
    /** 强化蜜蜂。 */
    BEE_PACK,

    // ── 变形 flag ──
    /** 夜晚狼人变形。 */
    WEREWOLF_FORM,
    /** 水下人鱼变形。 */
    MERFOLK_FORM,
    /** 史莱姆被动化。 */
    SLIME_PASSIVE;

    /** EffectTag 的 Codec（按枚举名序列化，供 DataComponentType 使用）。 */
    public static final Codec<EffectTag> CODEC = Codec.STRING.xmap(EffectTag::valueOf, Enum::name);
}
