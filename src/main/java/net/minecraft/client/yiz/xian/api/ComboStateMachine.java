package net.minecraft.client.yiz.xian.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量连招状态机 — 纯内存实现（不落盘、不走 PlayerDataAPI）。
 *
 * <p>借鉴 SlashBlade ComboState 设计，每条链定义了动画序列和过渡窗口。
 * 玩家在窗口期内攻击会推进到下一招，超时则重置。</p>
 *
 * <h3>为什么纯内存</h3>
 * <p>combo 是「账号临时态」：退出即弃、无需存盘、无需每 tick 同步。
 * 旧实现把 combo_step/combo_tick 写进 PlayerDataAPI，导致服务端每 tick 全量 NBT 解析
 * + 每 tick 把整个玩家数据 root S2C 同步到客户端，仅为了给一个 int +1。现在改为：
 * <ul>
 *   <li>服务端：内存 Map 存 {step, lastAtkGameTick}，惰性判断超时（访问时才算，不每 tick 写）</li>
 *   <li>客户端：动画索引由 {@code S2CComboAnimPayload} 在攻击事件时下发并缓存，渲染每帧读缓存</li>
 * </ul>
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
    public static final int WINDOW_START = 2;
    public static final int WINDOW_END = 24;

    /** 超时：超过此 tick 未攻击自动回到 IDLE */
    public static final int TIMEOUT = 40;

    /** 链 A 的动画序列：挥砍1(0)→挥砍2(1)→挥砍3(2)→挥砍4(3) 循环 */
    private static final int[] CHAIN_A = {0, 1, 2, 3};

    /** 服务端连招状态：step=-1 表 IDLE，lastAtkTick 为最近一次攻击的 gameTick。 */
    private record ComboState(int step, long lastAtkTick) {}
    private static final ConcurrentHashMap<UUID, ComboState> SERVER_STATE = new ConcurrentHashMap<>();

    /** 客户端动画索引缓存（由 S2CComboAnimPayload 写入，渲染每帧读）。 */
    private static volatile int clientAnimIndex = 0;

    /**
     * 玩家攻击时调用（服务端）。推进连招、计算动画索引，并下发客户端。
     *
     * @return 动画索引 (0/1/2/3)
     */
    public static int onAttack(Player player) {
        long now = player.level().getGameTime();
        ComboState cur = SERVER_STATE.get(player.getUUID());

        int step;
        if (cur == null) {
            step = 0;
        } else {
            long elapsed = now - cur.lastAtkTick;
            boolean inWindow = elapsed >= WINDOW_START && elapsed <= WINDOW_END;
            // 超时（elapsed > TIMEOUT）或不在窗口内 → 重新开始链
            step = (cur.step < 0 || !inWindow) ? 0 : (cur.step + 1) % CHAIN_A.length;
        }

        SERVER_STATE.put(player.getUUID(), new ComboState(step, now));
        int animIdx = CHAIN_A[step];

        // 仅服务端玩家可下发；客户端调用（理论上不会进这里）时跳过
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CComboAnimPayload.sendTo(sp, animIdx);
        }
        return animIdx;
    }

    /**
     * 获取当前应该显示的动画索引（客户端渲染用，读本地缓存）。
     * 不回 IDLE — 即使超时也显示最后一招的动画。
     */
    public static int getCurrentAnimIndex(Player player) {
        return clientAnimIndex;
    }

    /** 客户端专用：由 S2CComboAnimPayload.handle 写入缓存。 */
    public static void setClientAnimIndex(int animIdx) {
        clientAnimIndex = animIdx;
    }

    /**
     * 重置状态（玩家切换物品或登出时调用，服务端）。
     * 下发 animIdx=0 让客户端回到起手动画。
     */
    public static void reset(Player player) {
        SERVER_STATE.remove(player.getUUID());
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CComboAnimPayload.sendTo(sp, 0);
        }
    }

    /** 玩家退出/维度切换清理：移除服务端内存状态（不发包，客户端会随断线自然失效）。 */
    public static void clear(UUID playerId) {
        SERVER_STATE.remove(playerId);
    }
}
