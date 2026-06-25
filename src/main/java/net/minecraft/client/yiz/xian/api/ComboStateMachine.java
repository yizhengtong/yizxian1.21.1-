package net.minecraft.client.yiz.xian.api;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.world.entity.player.Player;

/**
 * 轻量连招状态机 — 基于 PlayerDataAPI 的 tick 计数系统。
 *
 * <p>借鉴 SlashBlade ComboState 设计，每条链定义了动画序列和过渡窗口。
 * 玩家在窗口期内攻击会推进到下一招，超时则重置。</p>
 *
 * <h3>链定义</h3>
 * <pre>
 * CHAIN_A (地面左键): 左平砍(A) → 左上→右下(D) → 左下→左上(C) → 右平砍(B) → 循环...
 * 动画索引映射: A=0, B=1, C=2, D=3
 * </pre>
 */
public final class ComboStateMachine {

    private ComboStateMachine() {}

    /** 每个 combo 步的 tick 时长 */
    public static final int COMBO_DURATION = 20;

    /** 过渡窗口：攻击需在 [WINDOW_START, WINDOW_END] ticks 内触发才算连招 */
    public static final int WINDOW_START = 4;
    public static final int WINDOW_END = 16;

    /** 超时：超过此 tick 未攻击自动回到 IDLE */
    public static final int TIMEOUT = 30;

    /** 链 A 的动画序列：左平砍(A=0)→左上→右下(D=3)→左下→左上(C=2)→右平砍(B=1)→... */
    private static final int[] CHAIN_A = {0, 3, 2, 1};

    // ── PlayerData keys ──
    private static final String KEY_STEP = "yizxianmod:combo_step";
    private static final String KEY_TICK = "yizxianmod:combo_tick";

    /**
     * 每次 tick 调用（服务端）。
     */
    public static void tick(Player player) {
        int tick = (int) PlayerDataAPI.get(player, KEY_TICK);
        tick++;
        PlayerDataAPI.set(player, KEY_TICK, tick);

        // 超时回 IDLE
        if (tick > TIMEOUT) {
            PlayerDataAPI.set(player, KEY_STEP, -1);
        }
    }

    /**
     * 玩家攻击时调用（服务端）。返回应该播放的动画索引。
     *
     * @return 动画索引 (0/1/2)
     */
    public static int onAttack(Player player) {
        int step = (int) PlayerDataAPI.get(player, KEY_STEP);
        int tick = (int) PlayerDataAPI.get(player, KEY_TICK);

        boolean inWindow = tick >= WINDOW_START && tick <= WINDOW_END;

        int nextStep;
        if (step < 0 || !inWindow) {
            // 不在窗口内 → 重新开始链
            nextStep = 0;
        } else {
            // 窗口内 → 推进到下个 combo
            nextStep = (step + 1) % CHAIN_A.length;
        }

        PlayerDataAPI.set(player, KEY_STEP, nextStep);
        PlayerDataAPI.set(player, KEY_TICK, 0);
        return CHAIN_A[nextStep];
    }

    /**
     * 获取当前应该显示的动画索引（客户端，用于渲染）。
     * 不回 IDLE — 即使超时也显示最后一招的动画。
     */
    public static int getCurrentAnimIndex(Player player) {
        int step = (int) PlayerDataAPI.get(player, KEY_STEP);
        if (step < 0 || step >= CHAIN_A.length) return 0;
        return CHAIN_A[step];
    }

    /**
     * 重置状态（玩家切换物品或登出时调用）。
     */
    public static void reset(Player player) {
        PlayerDataAPI.set(player, KEY_STEP, -1);
        PlayerDataAPI.set(player, KEY_TICK, 0);
    }
}
