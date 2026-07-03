package net.minecraft.client.yiz.xian.api;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.world.entity.player.Player;

/**
 * 通用突进数据（玩家全局池，与具体来源解耦）。
 *
 * <h3>突进逻辑</h3>
 * <ol>
 *   <li>TAB 键消耗 1 → 沿视线推进（消耗后进入 {@link #COOLDOWN_TICKS} tick 冷却）</li>
 *   <li>自动恢复：按 provider 间隔 tick +1（充能唯一来源，不受起飞/落地影响）</li>
 *   <li>充能跨飞行/落地持续保留</li>
 * </ol>
 *
 * <p>PlayerDataAPI key：{@code yizxianmod:boost} / {@code yizxianmod:boost_regen} / {@code yizxianmod:boost_cooldown}。</p>
 */
public final class BoostData {

    public static final String KEY_BOOST = "yizxianmod:boost";
    public static final String KEY_REGEN = "yizxianmod:boost_regen";
    public static final String KEY_COOLDOWN = "yizxianmod:boost_cooldown";

    /** 默认推进上限。 */
    public static final int DEFAULT_MAX = 3;
    /** 默认恢复间隔（tick，40 = 2 秒）。 */
    public static final int DEFAULT_REGEN = 40;
    /** 使用后冷却（tick，30 = 1.5 秒），期间无法再次消耗突进。 */
    public static final int COOLDOWN_TICKS = 30;

    private BoostData() {}

    /** PlayerDataAPI 注册（服务端入口调用一次）。 */
    public static void register() {
        PlayerDataAPI.register(KEY_BOOST, Codec.INT, 0);
        PlayerDataAPI.register(KEY_REGEN, Codec.INT, DEFAULT_REGEN);
        PlayerDataAPI.register(KEY_COOLDOWN, Codec.INT, 0);
    }

    // ── 当前 active provider 的数值（无 provider 时用默认）──

    public static int currentMax(Player player) {
        BoostProvider p = BoostRegistry.getActive(player);
        return p != null ? p.getMaxBoosts(player) : DEFAULT_MAX;
    }

    public static int currentInterval(Player player) {
        BoostProvider p = BoostRegistry.getActive(player);
        return p != null ? p.getRegenInterval(player) : DEFAULT_REGEN;
    }

    // ── 推进次数 ──

    public static int getBoosts(Player player) {
        Integer v = PlayerDataAPI.get(player, KEY_BOOST);
        return v != null ? v : 0;
    }

    public static void setBoosts(Player player, int count) {
        PlayerDataAPI.set(player, KEY_BOOST, Math.min(currentMax(player), Math.max(0, count)));
    }

    // ── 恢复计时 ──

    public static int getRegenTicks(Player player) {
        Integer v = PlayerDataAPI.get(player, KEY_REGEN);
        return v != null ? v : DEFAULT_REGEN;
    }

    public static void setRegenTicks(Player player, int ticks) {
        PlayerDataAPI.set(player, KEY_REGEN, Math.max(0, ticks));
    }

    /** 恢复进度 0.0 → 1.0（用于 HUD）。 */
    public static float getRegenProgress(Player player) {
        int interval = currentInterval(player);
        if (interval <= 0) return 1f;
        return 1f - (float) getRegenTicks(player) / interval;
    }

    // ── 冷却计时 ──

    public static int getCooldown(Player player) {
        Integer v = PlayerDataAPI.get(player, KEY_COOLDOWN);
        return v != null ? v : 0;
    }

    public static void setCooldown(Player player, int ticks) {
        PlayerDataAPI.set(player, KEY_COOLDOWN, Math.max(0, ticks));
    }

    /** 冷却进度 0.0 → 1.0（1.0 = 冷却完毕可用）。 */
    public static float getCooldownProgress(Player player) {
        int cd = getCooldown(player);
        if (cd <= 0) return 1f;
        return 1f - (float) cd / COOLDOWN_TICKS;
    }

    // ── 生命周期 ──

    /** 每 tick 调用：自动恢复 + 冷却递减。 */
    public static void tickRegen(Player player) {
        int cur = getBoosts(player);
        int max = currentMax(player);
        int interval = currentInterval(player);

        // 冷却递减
        int cd = getCooldown(player);
        if (cd > 0) setCooldown(player, cd - 1);

        if (cur >= max) return;
        int regen = getRegenTicks(player) - 1;
        if (regen <= 0) {
            setBoosts(player, cur + 1);
            setRegenTicks(player, interval);
        } else {
            setRegenTicks(player, regen);
        }
    }

    /** 起飞时调用（当前版本已取消起飞 +1，充能仅由恢复计时驱动）。 */
    public static void onTakeoff(Player player) {
        // 不再 +1。充能仅通过 tickRegen 自动恢复。
    }

    /** 落地时调用（当前版本已取消落地归零，充能跨飞行保留）。 */
    public static void onLand(Player player) {
        // 不再归零。充能跨飞行/落地持续保留。
    }

    /** 消耗 1 次推进（需不在冷却中且有可用充能），同时重置恢复计时。 */
    public static boolean tryConsume(Player player) {
        if (getCooldown(player) > 0) return false;
        int cur = getBoosts(player);
        if (cur <= 0) return false;
        PlayerDataAPI.set(player, KEY_BOOST, cur - 1);
        setCooldown(player, COOLDOWN_TICKS);
        // 重置恢复计时，防止消耗后立即被 tickRegen 补回
        setRegenTicks(player, currentInterval(player));
        return true;
    }
}
