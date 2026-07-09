package net.minecraft.client.yiz.xian.handler;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.xian.api.terraria.AccessoryFlags;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 装备保护态管理：DODGE_CHANCE（闪避率）+ INVINCIBILITY_MULT（无敌帧）。
 *
 * <h3>闪避率（预存机制）</h3>
 * <ol>
 *   <li>受伤后掷 DODGE_CHANCE → 成功则存入 {@code hasDodge} flag</li>
 *   <li>下次受击前 {@link MixinDodgeHurt} 检测到 flag → 先开无敌 → 放行攻击（被吞）→ 消耗 flag</li>
 * </ol>
 *
 * <h3>无敌帧（每次受伤后进无敌）</h3>
 * <ol>
 *   <li>每次受到有效伤害后 → 开无敌（{@link YizModQZKAPI#enableProtection}）</li>
 *   <li>持续 {@code INVINCIBILITY_MULT × 2} tick，到期后关无敌</li>
 *   <li>如已在无敌中 → 延长时间</li>
 * </ol>
 *
 * <p>两者共用同一套 {@link #PROTECTION_TICKS} 计时器。闪避预存成功后也进无敌 10 tick。</p>
 */
public final class AccessoryProtectionHandler {

    /** 各玩家保护态剩余 tick 数 */
    private static final WeakHashMap<ServerPlayer, Integer> PROTECTION_TICKS = new WeakHashMap<>();
    /** 各玩家是否预存了一次闪避 */
    static final WeakHashMap<ServerPlayer, Boolean> HAS_DODGE = new WeakHashMap<>();

    private AccessoryProtectionHandler() {}

    // ── 闪避消费（由 MixinDodgeHurt 在 hurt() HEAD 调用） ──

    /** 消费预存闪避并进入无敌 10 tick。返回 true = 已消费（攻击可被吞，不用再算伤害）。 */
    public static boolean consumeDodgeIfPresent(Player player) {
        if (!(player instanceof ServerPlayer sp)) return false;
        Boolean stored = HAS_DODGE.remove(sp);
        if (stored == null || !stored) return false;

        // 进入 yiz 无敌并延长至少 10 tick
        if (YizModQZKAPI.isProtected(sp)) {
            YizModQZKAPI.disableProtection(sp);   // 重开确保计时准确
        }
        YizModQZKAPI.enableProtection(sp);
        int cur = PROTECTION_TICKS.getOrDefault(sp, 0);
        PROTECTION_TICKS.put(sp, Math.max(cur, 10));
        return true;
    }

    // ── 受伤后处理（LivingDamageEvent） ──

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 先看是否消费了闪避（MixinDodgeHurt 已在 hurt() HEAD 调过）
        // 这里只处理"受伤后"的逻辑

        Map<EffectTag, Float> attrs = AccessoryFlags.sumValues(player);
        float dodgePct = attrs.getOrDefault(EffectTag.DODGE_CHANCE, 0f);
        float invMult   = attrs.getOrDefault(EffectTag.INVINCIBILITY_MULT, 0f);
        if (dodgePct <= 0 && invMult <= 0) return;

        // 无敌帧：每次受伤后进保护态
        int duration = Math.round(invMult * 2f);   // 10 属性 = 20 tick
        if (duration > 0) {
            extendProtection(player, duration);
        }

        // 闪避预存：受伤后掷骰，为下次攻击存储闪避机会
        if (dodgePct > 0 && Math.random() * 100 < dodgePct) {
            HAS_DODGE.put(player, true);
        }
    }

    // ── tick 调度：到期关无敌 ──

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        var it = PROTECTION_TICKS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayer player = entry.getKey();
            int ticks = entry.getValue() - 1;
            if (ticks <= 0) {
                it.remove();
                if (YizModQZKAPI.isProtected(player)) {
                    YizModQZKAPI.disableProtection(player);
                }
            } else {
                entry.setValue(ticks);
            }
        }
    }

    /** 延长保护态到至少 {@code extraTicks} 之后。如当前未在保护态则开。 */
    private static void extendProtection(ServerPlayer player, int extraTicks) {
        int cur = PROTECTION_TICKS.getOrDefault(player, 0);
        PROTECTION_TICKS.put(player, Math.max(cur, extraTicks));
        if (!YizModQZKAPI.isProtected(player)) {
            YizModQZKAPI.enableProtection(player);
        }
    }
}
