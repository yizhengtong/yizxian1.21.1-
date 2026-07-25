package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端锁定状态机 + 交互距离管理。
 *
 * <p>从玩家实体属性读取会心(HUIXIN)/渴攻(KEGONG)，
 * 在服务端独立追踪充能进度。充能完成时给玩家挂载
 * ENTITY_INTERACTION_RANGE 修饰符使交互距离延至会心值格。
 * 攻击命中后移除修饰符并重置充能。</p>
 *
 * <h3>默认互补值</h3>
 * <ul>
 *   <li>有会心无渴攻 → 渴攻默认 30 tick（1.5 秒）</li>
 *   <li>有渴攻无会心 → 会心默认 12 格</li>
 * </ul>
 */
public final class LockOnHandler {

    private static final double DEFAULT_HUIXIN = 12.0;
    private static final double DEFAULT_KEGONG = 30.0;
    private static final double CONE_DOT = 0.5; // cos(60°) ≈ 0.5

    private static final ResourceLocation LOCK_RANGE_ID =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "entity_lock_range");

    private LockOnHandler() {}

    // ═══════════════════════════════════════════════════════════
    //  状态存储
    // ═══════════════════════════════════════════════════════════

    private record LockState(UUID targetUuid, int timer) {}
    private static final Map<UUID, LockState> STATES = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════
    //  Tick: 充能更新
    // ═══════════════════════════════════════════════════════════

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isDeadOrDying()) return;

        // 读取属性，应用默认互补
        double rawHuixin = readAttr(player, YizAttributes.HUIXIN);
        double rawKegong = readAttr(player, YizAttributes.KEGONG);
        if (rawHuixin <= 0 && rawKegong <= 0) {
            reset(player);
            return;
        }
        double range = rawHuixin > 0 ? rawHuixin : DEFAULT_HUIXIN;
        int chargeTicks = rawKegong > 0 ? (int) rawKegong : (int) DEFAULT_KEGONG;

        // 找目标
        LivingEntity target = findTarget(player, range);
        UUID puid = player.getUUID();
        LockState state = STATES.get(puid);

        if (target == null || !target.isAlive()) {
            if (state != null) reset(player);
            return;
        }

        UUID tuid = target.getUUID();
        int timer;
        boolean wasReady = state != null && state.timer >= chargeTicks;
        if (state != null && state.targetUuid.equals(tuid)) {
            timer = Math.min(state.timer + 1, chargeTicks);
        } else {
            // 换目标：先移除旧修饰符再重新开始
            if (state != null) removeRangeModifier(player);
            timer = 1;
        }

        STATES.put(puid, new LockState(tuid, timer));
        boolean ready = timer >= chargeTicks;

        // 刚完成充能 → 挂距离修饰符
        if (ready && !wasReady) {
            applyRangeModifier(player, range);
        }

        // 同步到客户端（辅助通道，主渲染靠 LockOnProvider 独立完成）
        EntityLockAPI.lock(player, target, (float) timer / chargeTicks, ready);
    }

    // ═══════════════════════════════════════════════════════════
    //  攻击: 重置
    // ═══════════════════════════════════════════════════════════

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        LockState state = STATES.get(player.getUUID());
        if (state == null) return;

        // 任何攻击都重置充能
        reset(player);
    }

    // ═══════════════════════════════════════════════════════════
    //  公开清理入口
    // ═══════════════════════════════════════════════════════════

    /** 玩家退出时清理 */
    public static void onPlayerLogout(ServerPlayer player) {
        reset(player);
    }

    /** 玩家死亡时清理 */
    public static void onPlayerDeath(Player player) {
        reset(player);
    }

    // ═══════════════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════════════

    private static void reset(Player player) {
        UUID puid = player.getUUID();
        STATES.remove(puid);
        removeRangeModifier(player);
        EntityLockAPI.unlock(player);
        // 同步清理客户端 LockOnProvider 状态
        LockOnProvider.reset(player);
    }

    private static void applyRangeModifier(Player player, double range) {
        var inst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (inst == null) return;
        inst.removeModifier(LOCK_RANGE_ID);
        inst.addPermanentModifier(
            new AttributeModifier(LOCK_RANGE_ID, range, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeRangeModifier(Player player) {
        var inst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (inst != null) inst.removeModifier(LOCK_RANGE_ID);
    }

    /** 60°锥内找最近注视方向的实体，范围由 range 参数限定。 */
    private static LivingEntity findTarget(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        var look = player.getLookAngle();
        LivingEntity best = null;
        double bestDot = CONE_DOT, bestDist = Double.MAX_VALUE;
        for (var entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range))) {
            if (entity == player || !entity.isAlive()) continue;
            Vec3 to = entity.position().subtract(eye);
            double d2 = to.lengthSqr();
            if (d2 > range * range) continue;
            double dot = look.dot(to) / Math.sqrt(d2);
            if (best != null && Math.abs(dot - bestDot) < 0.05) {
                if (d2 < bestDist) { bestDot = dot; best = entity; bestDist = d2; }
            } else if (dot > bestDot) {
                bestDot = dot; best = entity; bestDist = d2;
            }
        }
        return best;
    }

    private static double readAttr(Player player,
                                    net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var inst = player.getAttribute(attr);
        return inst != null ? inst.getValue() : 0.0;
    }
}
