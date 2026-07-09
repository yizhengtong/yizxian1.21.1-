package net.minecraft.client.yiz.xian.item.terraria;

import net.minecraft.client.yiz.xian.api.terraria.EffectTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 泰拉瑞亚配饰配方卡（M0 阶段：仅 id + 中文名 + 英文名）。
 *
 * <p>注册名规则：{@code acc_<id>}（如 {@code acc_53} = 云朵瓶），
 * 与贴图 {@code textures/item/acc_<id>.png}、模型 {@code models/item/acc_<id>.json} 对齐。</p>
 *
 * <p><b>后续阶段扩展</b>（阶段 1/2，本 M0 阶段暂不填）：</p>
 * <ul>
 *   <li>{@code directEffects} —— 自己直接提供的效果（含数值）</li>
 *   <li>{@code parents} —— 合成父级饰品 id 列表（驱动自动继承）</li>
 *   <li>{@code overrides} —— 对父级同名效果的覆盖</li>
 * </ul>
 *
 * <p>数据源：{@code D:\ZM\yizgzq\png\Item\信息\信息_清理.md}。</p>
 */
public final class TerrariaCards {

    private TerrariaCards() {}

    /**
     * 一张配饰卡。承载 id + 中文名 + 英文名 + 效果继承链。
     *
     * <p><b>效果继承（阶段 1 起）</b>：</p>
     * <ul>
     *   <li>{@code directEffects} —— 自己直接提供的效果标签</li>
     *   <li>{@code parents} —— 合成父级饰品 id 列表（驱动自动继承）</li>
     *   <li>{@code suppressedEffects} —— 撕掉从父级继承来的标签（替换规则开关，
     *       如沙暴瓶 {@code suppress JUMP_CLOUD} 实现沙跳替换云跳）</li>
     *   <li>{@code values} —— 标签对应的数值（阶段 2 回填）</li>
     * </ul>
     * <p>父级里的非饰品材料（禁戒碎片、寒霜核）不在 {@link #CARDS} 中，
     * {@link #byId} 返回 null，继承解析自动跳过。</p>
     */
    public record Card(int id, String zhName, String enName,
                       Set<EffectTag> directEffects, List<Integer> parents,
                       Set<EffectTag> suppressedEffects, Map<EffectTag, Float> values) {

        /** M0 兼容构造器：仅 id + 中英文名，效果字段全空。 */
        public Card(int id, String zhName, String enName) {
            this(id, zhName, enName, Set.of(), List.of(), Set.of(), Map.of());
        }

        /** 注册名：{@code acc_<id>}。 */
        public String regName() {
            return "acc_" + id;
        }

        /** 语言键：{@code item.yizxianmod.acc_<id>}。 */
        public String langKey() {
            return "item.yizxianmod." + regName();
        }
    }

    /**
     * 42 个泰拉配饰（45 PNG 减去 3 个武器：757 泰拉刃 / 3570 月耀 / 4758 刃阵）。
     * 顺序按泰拉内部 ID 升序。
     */
    public static final List<Card> CARDS = List.of(
        new Card(53,   "云朵瓶",          "Cloud in a Bottle",
            Set.of(EffectTag.JUMP_CLOUD), List.of(), Set.of(),
            Map.of(EffectTag.JUMP_COUNT, 1f, EffectTag.JUMP_HEIGHT, 4f,
                   EffectTag.FALL_SAFE, 4f, EffectTag.FALL_REDUCE, 2f)),
        new Card(54,   "赫尔墨斯靴",      "Hermes Boots",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MAX_RUN_SPEED, 22.5f)),
        new Card(128,  "火箭靴",          "Rocket Boots",
            Set.of(), List.of(), Set.of(), Map.of(EffectTag.FLIGHT_TIME, 30f)),
        new Card(156,  "钴护盾",          "Cobalt Shield",
            Set.of(EffectTag.KNOCKBACK_IMMUNE), List.of(), Set.of(),
            Map.of(EffectTag.ARMOR, 1f)),
        new Card(158,  "幸运马掌",        "Lucky Horseshoe",
            Set.of(EffectTag.FALL_IMMUNE), List.of(), Set.of(), Map.of()),
        new Card(159,  "闪亮红气球",      "Shiny Red Balloon",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f)),
        new Card(193,  "黑曜石骷髅头",    "Obsidian Skull",
            Set.of(EffectTag.BURN_IMMUNE), List.of(), Set.of(),
            Map.of(EffectTag.ARMOR, 1f)),
        new Card(211,  "猛爪手套",        "Feral Claws",
            Set.of(EffectTag.AUTO_SWING), List.of(), Set.of(),
            Map.of(EffectTag.ATTACK_SPEED, 12f)),
        new Card(285,  "鞋带束头",        "Aglet",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 5f)),
        new Card(399,  "云朵气球",        "Cloud in a Balloon",
            Set.of(), List.of(53), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f)),
        new Card(405,  "幽灵靴",          "Spectre Boots",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MAX_RUN_SPEED, 22.5f)),
        new Card(532,  "星星斗篷",        "Star Cloak",
            Set.of(EffectTag.HURT_SPAWN_STARS), List.of(), Set.of(), Map.of()),
        new Card(536,  "泰坦手套",        "Titan Glove",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.ATTACK_KNOCKBACK, 2f, EffectTag.ATTACK_RANGE, 10f)),
        new Card(554,  "十字项链",        "Cross Necklace",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.INVINCIBILITY_MULT, 2f)),
        new Card(857,  "沙暴瓶",          "Sandstorm in a Bottle",
            Set.of(EffectTag.JUMP_SANDSTORM), List.of(53), Set.of(EffectTag.JUMP_CLOUD),
            Map.of(EffectTag.JUMP_COUNT, 1f, EffectTag.JUMP_HEIGHT, 7f,
                   EffectTag.FALL_SAFE, 7f, EffectTag.FALL_REDUCE, 4f)),
        new Card(862,  "星星面纱",        "Star Veil",
            Set.of(EffectTag.HURT_SPAWN_STARS), List.of(), Set.of(),
            Map.of(EffectTag.INVINCIBILITY_MULT, 2f)),
        new Card(897,  "强力手套",        "Power Glove",
            Set.of(EffectTag.AUTO_SWING), List.of(), Set.of(),
            Map.of(EffectTag.ATTACK_KNOCKBACK, 2f, EffectTag.ATTACK_RANGE, 10f,
                   EffectTag.ATTACK_SPEED, 12f)),
        new Card(898,  "闪电靴",          "Lightning Boots",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 8f, EffectTag.MAX_RUN_SPEED, 25.31f)),
        new Card(906,  "熔岩护身符",      "Lava Charm",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.LAVA_IMMUNE_TIME, 140f)),
        new Card(907,  "黑曜石水上漂靴",  "Obsidian Water Walking Boots",
            Set.of(EffectTag.BURN_IMMUNE, EffectTag.WATER_WALK), List.of(), Set.of(), Map.of()),
        new Card(908,  "熔岩靴",          "Lava Waders",
            Set.of(EffectTag.WATER_WALK, EffectTag.BURN_IMMUNE), List.of(), Set.of(),
            Map.of(EffectTag.LAVA_DAMAGE_REDUCTION, 45f, EffectTag.LAVA_IMMUNE_TIME, 140f)),
        new Card(935,  "复仇者徽章",      "Avenger Emblem",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.GENERIC_DAMAGE, 12f)),
        new Card(936,  "机械手套",        "Mechanical Glove",
            Set.of(EffectTag.AUTO_SWING), List.of(), Set.of(),
            Map.of(EffectTag.ATTACK_SPEED, 12f, EffectTag.ATTACK_KNOCKBACK, 2f,
                   EffectTag.MELEE_DAMAGE, 12f, EffectTag.ATTACK_RANGE, 10f)),
        new Card(950,  "溜冰鞋",          "Ice Skates",
            Set.of(EffectTag.ICE_SKATE), List.of(), Set.of(), Map.of()),
        new Card(983,  "沙暴气球",        "Sandstorm in a Balloon",
            Set.of(), List.of(857), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f)),
        new Card(987,  "暴雪瓶",          "Blizzard in a Bottle",
            Set.of(EffectTag.JUMP_BLIZZARD), List.of(53), Set.of(EffectTag.JUMP_CLOUD),
            Map.of(EffectTag.JUMP_COUNT, 1f, EffectTag.JUMP_HEIGHT, 5f,
                   EffectTag.FALL_SAFE, 5f, EffectTag.FALL_REDUCE, 3f)),
        new Card(1163, "暴雪气球",        "Blizzard in a Balloon",
            Set.of(), List.of(987), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f)),
        new Card(1250, "蓝马掌气球",      "Blue Horseshoe Balloon",
            Set.of(EffectTag.FALL_IMMUNE), List.of(399), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f)),
        new Card(1322, "岩浆石",          "Magma Stone",
            Set.of(EffectTag.MELEE_FIRE), List.of(), Set.of(), Map.of()),
        new Card(1323, "黑曜石玫瑰",      "Obsidian Rose",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.LAVA_DAMAGE_REDUCTION, 45f)),
        new Card(1343, "烈火手套",        "Fire Gauntlet",
            Set.of(EffectTag.MELEE_FIRE, EffectTag.AUTO_SWING), List.of(), Set.of(),
            Map.of(EffectTag.ATTACK_SPEED, 12f, EffectTag.MELEE_DAMAGE, 12f,
                   EffectTag.ATTACK_KNOCKBACK, 2f, EffectTag.ATTACK_RANGE, 10f)),
        new Card(1862, "霜花靴",          "Frostspark Boots",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 8f, EffectTag.MAX_RUN_SPEED, 25.31f)),
        new Card(3097, "克苏鲁护盾",      "Shield of Cthulhu",
            Set.of(EffectTag.DASH_ATTACK), List.of(), Set.of(), Map.of()),
        new Card(3212, "鲨牙项链",        "Shark Tooth Necklace",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.ARMOR_PENETRATION, 5f)),
        new Card(3223, "混乱之脑",        "Brain of Confusion",
            Set.of(EffectTag.HURT_CONFUSE_ENEMIES, EffectTag.DODGE_BUFF), List.of(), Set.of(),
            Map.of(EffectTag.DODGE_CHANCE, 10f)),
        new Card(3224, "蠕虫围巾",        "Worm Scarf",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.DAMAGE_REDUCTION, 17f)),
        new Card(3337, "闪亮石",          "Shiny Stone",
            Set.of(EffectTag.LIFE_REGEN_BOOST), List.of(), Set.of(),
            Map.of(EffectTag.LIFE_REGEN_RATE, 40f)),
        new Card(3580, "Yoraiz0r的魔法",  "Yoraiz0r's Spell"),
        new Card(4989, "翱翔徽章",        "Soaring Insignia",
            Set.of(EffectTag.INFINITE_FLIGHT), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 7.5f, EffectTag.JUMP_SPEED, 1.8f)),
        new Card(5000, "泰拉闪耀靴",      "Terraspark Boots",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 8f, EffectTag.MAX_RUN_SPEED, 25.31f)),
        new Card(5107, "魔光护符",        "Magiluminescence",
            Set.of(), List.of(), Set.of(),
            Map.of(EffectTag.MOVE_SPEED, 15f)),
        new Card(5331, "马掌气球束",      "Bundle of Horseshoe Balloons",
            Set.of(EffectTag.FALL_IMMUNE), List.of(399, 1163, 983), Set.of(),
            Map.of(EffectTag.JUMP_STRENGTH, 33f))
    );

    /** 卡片总数（应 = 42）。供自检用。 */
    public static final int COUNT = CARDS.size();

    /** id → Card 索引，供 {@link net.minecraft.client.yiz.xian.api.terraria.AccessoryFlags} 继承解析 O(1) 查找。 */
    private static final Map<Integer, Card> BY_ID = buildById();

    private static Map<Integer, Card> buildById() {
        Map<Integer, Card> m = new HashMap<>();
        for (Card c : CARDS) m.put(c.id(), c);
        return Map.copyOf(m);
    }

    /** 按 id 查卡片；非饰品材料（不在 CARDS 中）返回 null。 */
    public static Card byId(int id) {
        return BY_ID.get(id);
    }
}
