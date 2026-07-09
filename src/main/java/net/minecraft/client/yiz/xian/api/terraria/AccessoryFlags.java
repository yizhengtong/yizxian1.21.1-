package net.minecraft.client.yiz.xian.api.terraria;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.item.terraria.TerrariaAccessoryItem;
import net.minecraft.client.yiz.xian.item.terraria.TerrariaCards;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 配饰能力查询入口 —— 按 {@link EffectTag} 查询玩家当前能力（含合成链继承 + 多饰品叠加计数）。
 *
 * <h3>叠加规则（用户决策 2026-07-05）</h3>
 * <ul>
 *   <li><b>不同饰品</b>（terrariaId 不同）提供同一 tag → 次数<b>累加</b>
 *       （装云朵瓶 + 云朵气球 = 2 次云跳）</li>
 *   <li><b>同一饰品</b>多实例（两个云朵瓶）→ <b>去重</b>，只算 1 次</li>
 * </ul>
 *
 * <h3>继承规则</h3>
 * <ul>
 *   <li><b>替换</b>：子级用 {@code Card.suppressedEffects} 撕掉父级继承来的同名标签
 *       （沙暴瓶 suppress JUMP_CLOUD → 只剩沙跳）</li>
 *   <li><b>合并</b>：多条合成线的效果全部保留（气球束 = 云气球 + 暴雪气球 + 沙气球 → 三跳合一）</li>
 *   <li><b>环检测</b>：循环依赖自动截断</li>
 * </ul>
 *
 * <p>第一切片实时递归、不缓存（饰品少、链浅，性能足够）。</p>
 */
public final class AccessoryFlags {

    private AccessoryFlags() {}

    /** 玩家是否拥有某效果（叠加次数 >= 1）。 */
    public static boolean has(Player player, EffectTag tag) {
        return getCount(player, tag) > 0;
    }

    /** 玩家拥有某效果的叠加次数（不同饰品累加、同饰品去重）。 */
    public static int getCount(Player player, EffectTag tag) {
        return getCounts(player).getOrDefault(tag, 0);
    }

    /** 玩家所有激活的效果（次数 >= 1 的 tag 集合）。 */
    public static Set<EffectTag> getAll(Player player) {
        EnumSet<EffectTag> set = EnumSet.noneOf(EffectTag.class);
        set.addAll(getCounts(player).keySet());
        return set;
    }

    /**
     * 玩家所有效果的叠加计数（核心 API）。
     * <p>遍历饰品槽，按 terrariaId 去重（同 id 多实例只解析一次），再按 tag 累加。
     * 这是"多饰品叠加同样效果"的数据来源。</p>
     */
    public static Map<EffectTag, Integer> getCounts(Player player) {
        Map<EffectTag, Integer> counts = new EnumMap<>(EffectTag.class);
        AccessoryContainer c = AccessoryContainer.getIfExists(player);
        if (c == null) return counts;
        // 按 terrariaId 去重：同一饰品（同 id）多实例只解析一次 → 同饰品不叠加
        Map<Integer, Set<EffectTag>> byItem = new HashMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack stack = c.getItem(i);
            if (stack.getItem() instanceof TerrariaAccessoryItem tai && !stack.isEmpty()) {
                byItem.computeIfAbsent(tai.terrariaId(), AccessoryFlags::resolve);
            }
        }
        // 不同饰品的效果叠加计数 → 不同饰品同效果叠加
        for (Set<EffectTag> tags : byItem.values()) {
            for (EffectTag t : tags) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** 取某 tag 的数值（阶段 2 数值回填后实现，本次占位 0）。 */
    public static float getValue(Player player, EffectTag tag) {
        // TODO 阶段 2：遍历装备 + 继承链，取该 tag 对应数值的最高值（同种数值取最高不叠加）
        return 0f;
    }

    // ── 继承解析 ──────────────────────────────────────────────

    /** 解析某饰品 id 的完整效果集（递归父级 + 替换 + 合并 + 环检测）。 */
    private static Set<EffectTag> resolve(int terrariaId) {
        Set<Integer> visited = new HashSet<>();
        return dfs(terrariaId, visited);
    }

    /**
     * 深度优先解析（<b>返回本子树结果</b>）：先合并各父级子树 → 撕掉本节点 suppressed
     * → 加本节点 direct。
     *
     * <p><b>必须用返回值风格</b>而非共享累加器：suppressed 只能作用于本子树累加的结果，
     * 否则在多线合并时会误伤兄弟子树 —— 例如气球束 = 云气球 + 暴雪气球 + 沙气球，
     * 沙暴瓶的 {@code suppress JUMP_CLOUD} 不应撕掉云气球贡献的云跳。</p>
     *
     * <p>{@code visited} 同时承担<b>环检测</b>（重复访问返回空集）和<b>幂等</b>
     * （菱形继承下同一父级只算一次）。</p>
     */
    private static Set<EffectTag> dfs(int id, Set<Integer> visited) {
        if (!visited.add(id)) return EnumSet.noneOf(EffectTag.class);   // 环检测 / 幂等
        TerrariaCards.Card card = TerrariaCards.byId(id);
        if (card == null) return EnumSet.noneOf(EffectTag.class);       // 父级中的非饰品材料
        EnumSet<EffectTag> acc = EnumSet.noneOf(EffectTag.class);
        for (int pid : card.parents()) {
            acc.addAll(dfs(pid, visited));
        }
        for (EffectTag t : card.suppressedEffects()) {
            acc.remove(t);
        }
        acc.addAll(card.directEffects());
        return acc;
    }

    // ── 数值属性解析（2026-07-06 属性化改造）─────────────────────

    /**
     * 解析某饰品 id 的完整<b>数值属性集</b>（递归父级 + 替换 + 合并 + 环检测）。
     * <p>仿 {@link #dfs} 骨架，但累加器是 {@code Map<EffectTag, Float>}（携带 Card.values 数值），
     * 而非 {@code Set<EffectTag>}。suppress 时 remove 对应 tag（撕掉父级继承来的数值），
     * direct 阶段合并本节点 {@link TerrariaCards.Card#values()}。</p>
     */
    public static Map<EffectTag, Float> resolveValues(int terrariaId) {
        return dfsValues(terrariaId, new HashSet<>());
    }

    private static Map<EffectTag, Float> dfsValues(int id, Set<Integer> visited) {
        if (!visited.add(id)) return new EnumMap<>(EffectTag.class);   // 环检测 / 幂等
        TerrariaCards.Card card = TerrariaCards.byId(id);
        if (card == null) return new EnumMap<>(EffectTag.class);       // 非饰品材料
        Map<EffectTag, Float> acc = new EnumMap<>(EffectTag.class);
        for (int pid : card.parents()) {
            for (var e : dfsValues(pid, visited).entrySet()) {
                acc.merge(e.getKey(), e.getValue(), Float::sum);
            }
        }
        for (EffectTag t : card.suppressedEffects()) {
            acc.remove(t);
        }
        for (var e : card.values().entrySet()) {
            acc.put(e.getKey(), e.getValue());   // 本节点 values 覆盖父级同 tag
        }
        return acc;
    }

    /**
     * 取任意 {@link ItemStack} 的完整数值属性集（含继承链解析 + 组件注入）。
     * <p>读取优先级：</p>
     * <ol>
     *   <li>若是 {@link TerrariaAccessoryItem} → 先取 {@link #resolveValues} 的 Card.values 继承值</li>
     *   <li>再读 {@link JumpAttributes#getWithDefaults} 组件（指令注入），<b>组件覆盖</b> Card.values 同 tag</li>
     *   <li>空 stack / 既非泰拉饰品又无组件 → 空 map</li>
     * </ol>
     * <p>默认模板：物品带组件（声明任一属性）→ 缺失 key 用默认值填充；无组件 → 空 map。</p>
     */
    public static Map<EffectTag, Float> slotValues(ItemStack stack) {
        Map<EffectTag, Float> acc = new EnumMap<>(EffectTag.class);
        if (stack.isEmpty()) return acc;
        // 泰拉饰品：先取 Card.values 继承值
        if (stack.getItem() instanceof TerrariaAccessoryItem tai) {
            acc.putAll(resolveValues(tai.terrariaId()));
        }
        // 任意物品：组件注入覆盖（带默认填充）
        Map<EffectTag, Float> comp = JumpAttributes.getWithDefaults(stack);
        acc.putAll(comp);   // 组件覆盖 Card.values 同 tag
        return acc;
    }

    /**
     * 取玩家饰品栏某槽位的数值属性集。委托给 {@link #slotValues(ItemStack)}。
     */
    public static Map<EffectTag, Float> slotValues(Player player, int slot) {
        AccessoryContainer c = AccessoryContainer.getIfExists(player);
        if (c == null || slot < 0 || slot >= c.getContainerSize()) return new EnumMap<>(EffectTag.class);
        return slotValues(c.getItem(slot));
    }

    /**
     * 遍历<b>饰品槽 + 4 个盔甲槽</b>（HEAD/CHEST/LEGS/FEET），累加各槽位数值属性。
     * <p>供摔伤减免汇总（FALL_SAFE/FALL_REDUCE 求和）和总跳跃次数（JUMP_COUNT 求和）。
     * 不去重 terrariaId（同 id 多实例各算一次）。原版物品（靴子等）通过组件注入的属性也计入。</p>
     */
    public static Map<EffectTag, Float> sumValues(Player player) {
        Map<EffectTag, Float> sum = new EnumMap<>(EffectTag.class);
        // 饰品槽
        AccessoryContainer c = AccessoryContainer.getIfExists(player);
        if (c != null) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                mergeValues(sum, slotValues(c.getItem(i)));
            }
        }
        // 原版 4 盔甲槽
        for (net.minecraft.world.entity.EquipmentSlot es : ARMOR_SLOTS) {
            mergeValues(sum, slotValues(player.getItemBySlot(es)));
        }
        return sum;
    }

    private static void mergeValues(Map<EffectTag, Float> sum, Map<EffectTag, Float> contribution) {
        for (var e : contribution.entrySet()) {
            sum.merge(e.getKey(), e.getValue(), Float::sum);
        }
    }

    /** 原版盔甲槽（供 sumValues 遍历）。 */
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.HEAD,
        net.minecraft.world.entity.EquipmentSlot.CHEST,
        net.minecraft.world.entity.EquipmentSlot.LEGS,
        net.minecraft.world.entity.EquipmentSlot.FEET
    };
}
