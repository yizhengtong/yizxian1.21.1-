package net.minecraft.client.yiz.xian.api;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.world.entity.player.Player;

/**
 * 通用突进数据（玩家全局池，与具体来源解耦）。
 *
 * <h3>突进逻辑</h3>
 * <ol>
 *   <li>起飞时 +1（仅当有 active {@link BoostProvider}）</li>
 *   <li>TAB 键消耗 1 → 沿视线推进</li>
 *   <li>自动恢复：每 {@link #DEFAULT_REGEN} tick +1（上限由当前 active provider 决定）</li>
 *   <li>落地时：归零</li>
 * </ol>
 *
 * <p>PlayerDataAPI key：{@code yizxianmod:boost} / {@code yizxianmod:boost_regen}。</p>
 */
public final class BoostData {

    public static final String KEY_BOOST = "yizxianmod:boost";
    public static final String KEY_REGEN = "yizxianmod:boost_regen";

    /** 默认推进上限。 */
    public static final int DEFAULT_MAX = 3;
    /** 默认恢复间隔（tick，40 = 2 秒）。 */
    public static final int DEFAULT_REGEN = 40;

    private BoostData() {}

    /** PlayerDataAPI 注册（服务端入口调用一次）。 */
    public static void register() {
        PlayerDataAPI.register(KEY_BOOST, Codec.INT, 0);
        PlayerDataAPI.register(KEY_REGEN, Codec.INT, DEFAULT_REGEN);
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

    // ── 生命周期 ──

    /** 每 tick 调用：自动恢复。 */
    public static void tickRegen(Player player) {
        int cur = getBoosts(player);
        int max = currentMax(player);
        int interval = currentInterval(player);
        int regen = getRegenTicks(player);
        // 采样日志：每 20 tick 打印一次倒计时，定位恢复间隔
        if (player.tickCount % 20 == 0) {
            net.minecraft.client.yiz.xian.YizxianMod.LOGGER.info(
                "[BoostRegen] player={} cur={}/{} regen={}/{} interval={}",
                player.getName().getString(), cur, max, regen, interval, interval);
        }
        if (cur >= max) return;
        regen = regen - 1;
        if (regen <= 0) {
            setBoosts(player, cur + 1);
            setRegenTicks(player, interval);
            net.minecraft.client.yiz.xian.YizxianMod.LOGGER.info(
                "[BoostRegen] player={} recovered +1 -> {}/{}", player.getName().getString(), cur + 1, max);
        } else {
            setRegenTicks(player, regen);
        }
    }

    /** 起飞时调用：+1（不超上限）。 */
    public static void onTakeoff(Player player) {
        int cur = getBoosts(player);
        if (cur < currentMax(player)) {
            setBoosts(player, cur + 1);
        }
    }

    /** 落地时调用：归零。 */
    public static void onLand(Player player) {
        if (getBoosts(player) > 0) {
            setBoosts(player, 0);
        }
    }

    /** TAB 键触发：消耗 1 次推进。 */
    public static boolean tryConsume(Player player) {
        int cur = getBoosts(player);
        if (cur <= 0) return false;
        PlayerDataAPI.set(player, KEY_BOOST, cur - 1);
        return true;
    }
}
