package net.minecraft.client.yiz.xian.handler.terraria;

import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * 附加跳服务端权威处理（属性化模型 C，2026-07-06）：剩余次数按<b>饰品槽位数组</b>存储，
 * 落地整体恢复，消耗顺序 = 槽位顺序。数值从物品 {@code Card.values} 读，不硬编码。
 *
 * <h3>落地充满（三路径）</h3>
 * <ul>
 *   <li>{@link #onLivingFall}（{@link LivingFallEvent}）：有 fallDistance 的正常摔落</li>
 *   <li>{@link #onPlayerTick} 里 {@code isFallFlying()} 下降沿：心之翅/鞘翅平滑落地</li>
 *   <li>{@link #onPlayerTick} 里<b>稳定地面充满</b>（连续 3 tick）：装备饰品后行走恢复、
 *       落地后稳定行走恢复。过滤空中 onGround 单 tick 误判（垂直速度≈0 + 连续 3 tick）。</li>
 * </ul>
 *
 * <h3>空中 cap（每 tick）</h3>
 * <p>按槽位 {@code remaining[i] = min(remaining[i], jumpCountOfSlot(i))}，处理装备变动：
 * 卸瓶 → cap 到 0；装新瓶 → 不立即充满（等落地）；数组长度与栏位不符时重整。</p>
 *
 * <p>消耗由 {@code C2SExtraJumpPayload.handle} → {@link ExtraJumpData#tryConsume} 处理。
 * 仅在值变化时写入（避免每 tick 刷同步包）。</p>
 */
public final class ExtraJumpHandler {

    /**
     * 上一 tick 各玩家是否处于鞘翅飞行（按 Player 记录，WeakHashMap 防维度切换/重连残留）。
     * 用于检测 {@code isFallFlying()} 下降沿（true→false）—— 心之翅/鞘翅飞行结束的可靠信号。
     */
    private static final WeakHashMap<Player, Boolean> WAS_FLYING = new WeakHashMap<>();

    /**
     * 各玩家连续处于"稳定地面"的 tick 计数（WeakHashMap 防维度切换/重连残留）。
     * 用于"地面行走充满"路径：连续 {@link #STABLE_GROUND_TICKS} tick 满足稳定地面条件才充满一次。
     */
    private static final WeakHashMap<Player, Integer> GROUND_TICKS = new WeakHashMap<>();

    /** 稳定地面连续 tick 阈值（3 tick = 150ms），用于过滤空中 onGround 单 tick 误判。 */
    private static final int STABLE_GROUND_TICKS = 3;

    private ExtraJumpHandler() {}

    /**
     * 服务端每 tick：
     * <ol>
     *   <li>空中 cap（按槽位 {@code min(remaining, jumpCountOfSlot)}，处理装备变动）</li>
     *   <li>多段跳突进冷却递减</li>
     *   <li>{@code isFallFlying()} 下降沿检测 → 飞行结束时充满</li>
     *   <li>稳定地面充满（连续 3 tick）</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // 空中 cap + 装备变动整队：按当前栏位大小重整 remaining 数组，每槽 cap 到该槽应有次数
        capToGear(player);

        // 多段跳突进冷却递减（鞘翅飞行时 TAB 消耗进入的 16 tick 冷却）
        ExtraJumpData.tickCooldown(player);

        // isFallFlying() 下降沿：心之翅/鞘翅飞行结束（true→false）→ 充满。
        boolean nowFlying = player.isFallFlying();
        boolean wasFlying = WAS_FLYING.getOrDefault(player, false);
        WAS_FLYING.put(player, nowFlying);
        if (wasFlying && !nowFlying) {
            rechargeAll(player);
            GROUND_TICKS.put(player, 0);
            return;
        }

        // 稳定地面充满：连续 3 tick "在地面 + 未飞行 + 垂直速度≈0" 才充满，过滤空中擦边
        boolean onGround = player.onGround();
        boolean verticalCalm = Math.abs(player.getDeltaMovement().y()) < 0.1;
        if (onGround && !nowFlying && verticalCalm) {
            int ticks = GROUND_TICKS.getOrDefault(player, 0) + 1;
            GROUND_TICKS.put(player, ticks);
            if (ticks >= STABLE_GROUND_TICKS) {
                rechargeAll(player);
                GROUND_TICKS.put(player, STABLE_GROUND_TICKS);   // 固定阈值，避免每 tick 重复 recharge
            }
        } else {
            GROUND_TICKS.put(player, 0);   // 离开稳定地面 → 重置
        }
    }

    /**
     * 玩家真正坠落落地（{@link LivingFallEvent}）时充满。
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        rechargeAll(player);
    }

    /** 玩家登录 / 重生时充满。 */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        rechargeAll(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        rechargeAll(player);
    }

    // ── 槽位模型核心：cap 与充满（统一序列：饰品槽 + 4 盔甲槽）─

    /**
     * 空中 cap + 装备变动整队：按统一槽位总数重整 remaining 数组，每槽 cap 到该槽应有次数。
     * <p>处理三类装备变动：</p>
     * <ul>
     *   <li>卸瓶（槽位变空/换非跳瓶）→ jumpCountOfSlot=0 → remaining cap 到 0</li>
     *   <li>换瓶（同槽不同饰品）→ remaining cap 到新饰品 jumpCount（不超过）</li>
     *   <li>栏位大小变化 → 数组扩缩容</li>
     * </ul>
     */
    private static void capToGear(Player player) {
        int slots = ExtraJumpData.slotCount(player);
        List<Integer> remaining = new ArrayList<>(ExtraJumpData.getRemaining(player));
        boolean changed = false;
        while (remaining.size() < slots) { remaining.add(0); changed = true; }
        while (remaining.size() > slots) { remaining.remove(remaining.size() - 1); changed = true; }
        for (int i = 0; i < slots; i++) {
            int full = ExtraJumpData.jumpCountOfSlot(player, i);
            int cur = remaining.get(i);
            if (cur > full) { remaining.set(i, full); changed = true; }
        }
        if (changed) ExtraJumpData.setRemaining(player, remaining);
    }

    /** 把每个统一槽位的剩余次数充满到该槽饰品应有的 JUMP_COUNT。 */
    private static void rechargeAll(Player player) {
        int slots = ExtraJumpData.slotCount(player);
        List<Integer> remaining = new ArrayList<>(ExtraJumpData.getRemaining(player));
        while (remaining.size() < slots) remaining.add(0);
        while (remaining.size() > slots) remaining.remove(remaining.size() - 1);
        boolean changed = false;
        for (int i = 0; i < slots; i++) {
            int full = ExtraJumpData.jumpCountOfSlot(player, i);
            if (remaining.get(i) != full) { remaining.set(i, full); changed = true; }
        }
        if (changed) ExtraJumpData.setRemaining(player, remaining);
    }
}
