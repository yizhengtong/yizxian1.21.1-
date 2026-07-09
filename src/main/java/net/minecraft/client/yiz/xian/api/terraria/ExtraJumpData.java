package net.minecraft.client.yiz.xian.api.terraria;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附加跳（多段跳）数据 —— <b>属性化模型 C（2026-07-06 重构）</b>：按饰品槽顺序消耗，
 * 数值从物品 {@link TerrariaCards.Card#values} 读取，不再硬编码三档 switch。
 *
 * <h3>四个属性 tag（挂在 Card.values 上）</h3>
 * <ul>
 *   <li>{@link EffectTag#JUMP_COUNT} — 该饰品提供几次跳（按槽位顺序消耗）</li>
 *   <li>{@link EffectTag#JUMP_HEIGHT} — 该饰品每次跳的高度（格），驱动 {@link #velocityFromHeight}</li>
 *   <li>{@link EffectTag#FALL_SAFE} — 摔伤安全距离贡献（跨饰品累加，常驻）</li>
 *   <li>{@link EffectTag#FALL_REDUCE} — 摔伤伤害减免贡献（跨饰品累加，常驻）</li>
 * </ul>
 *
 * <h3>消耗规则</h3>
 * <ul>
 *   <li>跳跃按饰品槽顺序消耗（槽 0 先用完，再槽 1…），每次跳用<b>当前消耗饰品</b>的 JUMP_HEIGHT</li>
 *   <li>摔伤减免常驻：{@code safe = Σ FALL_SAFE}、{@code reduce = Σ FALL_REDUCE}（与本下落用没用跳无关）</li>
 *   <li>落地恢复全部次数</li>
 * </ul>
 *
 * <h3>默认值（物品未声明某属性时）</h3>
 * <p>{@code JUMP_COUNT=1, JUMP_HEIGHT=4, FALL_SAFE=4, FALL_REDUCE=2}。</p>
 *
 * <h3>剩余次数存储</h3>
 * <p>{@link #KEY_REMAINING} 存 {@code List<Integer>}（按槽位的剩余次数），落地整体重写恢复。
 * 同 id 多实例各槽独立消耗（装两个云朵瓶 = 2 次跳）。</p>
 *
 * <h3>多段跳内置 CD（防长按）</h3>
 * <p>{@link #JUMP_COOLDOWN_TICKS}（5 tick）纯客户端手感限制，不进网络同步。</p>
 *
 * <h3>客户端/服务端分工</h3>
 * <ul>
 *   <li>客户端：{@link #pickNextSlot} 只读判定 + {@link #velocityFromHeight} 乐观预测速度 + 发 C2S</li>
 *   <li>服务端：{@link #tryConsume} 权威消耗 + {@code ExtraJumpHandler} 充能，自动 S2C 同步纠正</li>
 * </ul>
 */
public final class ExtraJumpData {

    /** 按槽位的剩余跳跃次数（List<Integer>，index = 饰品槽位）。落地整体重写恢复。 */
    public static final String KEY_REMAINING = "yizxianmod:extra_jump_remaining";
    /** 多段跳突进（鞘翅飞行时 TAB）独立冷却 tick。 */
    public static final String KEY_BOOST_CD  = "yizxianmod:extra_jump_boost_cd";

    /** 多段跳突进冷却（tick，16 = 0.8 秒）。 */
    public static final int BOOST_COOLDOWN_TICKS = 16;
    /** 多段跳内置冷却（tick，5 = 0.25 秒），防长按快速消耗。纯客户端。 */
    public static final int JUMP_COOLDOWN_TICKS = 5;

    // ── 默认属性值（物品未声明时回退） ──
    private static final int DEFAULT_JUMP_COUNT  = 1;
    private static final int DEFAULT_JUMP_HEIGHT = 4;
    private static final int DEFAULT_FALL_SAFE   = 4;
    private static final int DEFAULT_FALL_REDUCE = 2;

    private ExtraJumpData() {}

    /** PlayerDataAPI 注册（服务端入口调用一次）。 */
    public static void register() {
        PlayerDataAPI.register(KEY_REMAINING, Codec.INT.listOf(), List.of());
        PlayerDataAPI.register(KEY_BOOST_CD, Codec.INT, 0);
    }

    // ── 跳跃物理：高度 → Y 初速（反解 + 缓存）──────────────────

    /** heightBlocks → Y 初速 缓存（避免每次跳跃重复二分反解）。 */
    private static final Map<Integer, Float> HEIGHT_TO_VELOCITY = new ConcurrentHashMap<>();

    /**
     * 由跳跃高度（格）反解 Y 向初速。MC 垂直运动方程 {@code v_{n+1}=(v_n-0.08)*0.98}
     * （重力 0.08、阻力 0.98）反解，二分法 + 缓存。
     * <p>校准：4→0.803、5→0.910、7→1.099（与历史 switch 完全一致）。
     * 原版基础跳 0.42→1.25 格作为 sanity check。</p>
     */
    public static float velocityFromHeight(int heightBlocks) {
        if (heightBlocks <= 0) return 0f;
        return HEIGHT_TO_VELOCITY.computeIfAbsent(heightBlocks, ExtraJumpData::solveVelocity);
    }

    /** 二分反解：找 v0 使模拟高度 ≈ target。 */
    private static float solveVelocity(int target) {
        double lo = 0.05, hi = 3.0;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (simulateHeight(mid) < target) lo = mid; else hi = mid;
        }
        return (float) ((lo + hi) / 2);
    }

    /** 模拟 MC 垂直运动累加位移，返回总上升高度（格）。 */
    private static double simulateHeight(double v0) {
        double v = v0, total = 0;
        int t = 0;
        while (v > 0 && t < 400) {
            total += v;
            v = (v - 0.08) * 0.98;
            t++;
        }
        return total;
    }

    // ── 槽位属性查询（统一序列：饰品槽 + 4 盔甲槽）───────────

    /** 原版盔甲槽顺序（HEAD→CHEST→LEGS→FEET），排在饰品槽之后。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 统一槽位总数 = 饰品栏大小 + 4 盔甲槽。 */
    public static int slotCount(Player player) {
        AccessoryContainer c = AccessoryContainer.getIfExists(player);
        return (c != null ? c.getContainerSize() : 0) + ARMOR_SLOTS.length;
    }

    /**
     * 按统一槽位 index 取物品栈。
     * <p>index 0..(accSize-1) → 饰品槽；accSize..accSize+3 → HEAD/CHEST/LEGS/FEET。</p>
     */
    static ItemStack itemByUnifiedSlot(Player player, int index) {
        AccessoryContainer c = AccessoryContainer.getIfExists(player);
        int accSize = c != null ? c.getContainerSize() : 0;
        if (index < accSize) {
            return c != null ? c.getItem(index) : ItemStack.EMPTY;
        }
        int armorIdx = index - accSize;
        if (armorIdx >= 0 && armorIdx < ARMOR_SLOTS.length) {
            return player.getItemBySlot(ARMOR_SLOTS[armorIdx]);
        }
        return ItemStack.EMPTY;
    }

    /** 统一槽位查跳跃次数。无则 0。 */
    public static int jumpCountOfSlot(Player player, int slot) {
        Map<EffectTag, Float> v = AccessoryFlags.slotValues(itemByUnifiedSlot(player, slot));
        float raw = v.getOrDefault(EffectTag.JUMP_COUNT, 0f);
        return raw > 0 ? Math.round(raw) : 0;
    }

    /** 统一槽位查跳跃高度（格）。无则默认 4。 */
    public static int jumpHeightOfSlot(Player player, int slot) {
        Map<EffectTag, Float> v = AccessoryFlags.slotValues(itemByUnifiedSlot(player, slot));
        return Math.round(v.getOrDefault(EffectTag.JUMP_HEIGHT, (float) DEFAULT_JUMP_HEIGHT));
    }

    // ── 剩余次数（按槽位数组）──────────────────────────────────

    /** 当前各槽位剩余跳跃次数（index = 统一槽位：饰品槽 + 4 盔甲槽）。 */
    public static List<Integer> getRemaining(Player player) {
        List<Integer> v = PlayerDataAPI.get(player, KEY_REMAINING);
        return v != null ? v : List.of();
    }

    /** 服务端写入剩余次数数组，自动 S2C 同步。 */
    public static void setRemaining(Player player, List<Integer> remaining) {
        PlayerDataAPI.set(player, KEY_REMAINING, remaining);
    }

    /**
     * 按槽位顺序找第一个"还有剩余次数"的跳瓶槽。
     * @return 槽位 index；-1 = 全部用完或无跳瓶。客户端只读判定，服务端权威消耗。
     */
    public static int pickNextSlot(Player player) {
        List<Integer> remaining = getRemaining(player);
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i) > 0 && jumpCountOfSlot(player, i) > 0) return i;
        }
        return -1;
    }

    /** 客户端只读：是否还能多段跳（有可用槽位）。 */
    public static boolean hasJump(Player player) {
        return pickNextSlot(player) >= 0;
    }

    /**
     * 按槽位顺序消耗一次。<b>仅服务端调用</b>（C2S 包处理里）。
     * @return 被消耗的槽位 index；-1 = 无可用
     */
    public static int tryConsume(Player player) {
        int slot = pickNextSlot(player);
        if (slot < 0) return -1;
        List<Integer> remaining = new ArrayList<>(getRemaining(player));
        // 防御：数组长度与当前栏位不符时补齐
        while (remaining.size() <= slot) remaining.add(0);
        remaining.set(slot, remaining.get(slot) - 1);
        setRemaining(player, remaining);
        return slot;
    }

    // ── 常驻摔伤减免（按装备总能力实时查询）─────────────────────

    /** 常驻安全距离 = Σ 所有饰品的 FALL_SAFE。 */
    public static int getFallSafeByGear(Player player) {
        return Math.round(AccessoryFlags.sumValues(player).getOrDefault(EffectTag.FALL_SAFE, 0f));
    }

    /** 常驻伤害减免 = Σ 所有饰品的 FALL_REDUCE。 */
    public static int getFallReduceByGear(Player player) {
        return Math.round(AccessoryFlags.sumValues(player).getOrDefault(EffectTag.FALL_REDUCE, 0f));
    }

    // ── 多段跳突进（鞘翅飞行时 TAB，优先度低于心之翅）─────────

    public static int getBoostCooldown(Player player) {
        Integer v = PlayerDataAPI.get(player, KEY_BOOST_CD);
        return v != null ? v : 0;
    }

    public static void setBoostCooldown(Player player, int ticks) {
        PlayerDataAPI.set(player, KEY_BOOST_CD, Math.max(0, ticks));
    }

    /** 客户端只读：是否可触发多段跳突进（有可用跳槽 且 不在冷却）。 */
    public static boolean canBoost(Player player) {
        return getBoostCooldown(player) <= 0 && hasJump(player);
    }

    /**
     * 鞘翅飞行时 TAB 触发的"多段跳突进"：按槽位消耗 1 次，
     * 进入 {@link #BOOST_COOLDOWN_TICKS} tick 冷却。<b>仅服务端调用</b>。
     * @return 被消耗的槽位 index；-1 = 无可用
     */
    public static int tryConsumeForBoost(Player player) {
        if (getBoostCooldown(player) > 0) return -1;
        int slot = tryConsume(player);   // 复用槽位消耗
        if (slot < 0) return -1;
        setBoostCooldown(player, BOOST_COOLDOWN_TICKS);
        return slot;
    }

    /** 服务端每 tick 调用：递减多段跳突进冷却。多段跳内置 CD 是纯客户端，不在此递减。 */
    public static void tickCooldown(Player player) {
        int cd = getBoostCooldown(player);
        if (cd > 0) setBoostCooldown(player, cd - 1);
    }
}
