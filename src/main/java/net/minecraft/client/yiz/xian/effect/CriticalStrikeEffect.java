package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.xian.item.TalentLevelTracker;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

public class CriticalStrikeEffect extends AbstractEffect {

    private static final String MODID = "yizxianmod";
    private static final String EFF_ID = "critical_strike";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MODID, EFF_ID);

    public static final String DATA_TIMER = "yizxianmod:crit_timer";
    public static final String DATA_TARGET = "yizxianmod:crit_target";
    private static final int LOCK_TICKS = 50; // 2.5秒
    private static final double RANGE = 8.0;

    public CriticalStrikeEffect(int level) {
        super(
            ID,
            "effect." + MODID + "." + EFF_ID,
            "会心一击",
            ParentType.ECHO,
            Math.max(1, Math.min(level, 3)),
            Set.of(new EntityPerception()),
            ctx -> {
                if (!(ctx.entity() instanceof Player player)) return false;
                return findTarget(player) != null;
            },
            Rarity.EPIC
        );
    }

    @Override
    public void execute(EffectContext context) {
        if (!(context.entity() instanceof Player player)) return;

        LivingEntity target = findTarget(player);
        int timer = PlayerDataAPI.get(player, DATA_TIMER);

        if (target == null || !target.isAlive()) {
            if (timer > 0) EntityLockAPI.unlock(player);
            reset(player);
            return;
        }

        int newTimer = Math.min(timer + 1, LOCK_TICKS);
        PlayerDataAPI.set(player, DATA_TIMER, newTimer);
        PlayerDataAPI.set(player, DATA_TARGET, target.getUUID().toString());
        EntityLockAPI.lock(player, target, (float) newTimer / LOCK_TICKS, newTimer >= LOCK_TICKS);
        // 充能完成：音效 + 攻击距离 +5
        if (timer < LOCK_TICKS && newTimer >= LOCK_TICKS) {
            if (player instanceof ServerPlayer sp)
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(player,
                net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
                "crit_range", 15.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        }
    }

    @Override
    public List<String> getTalentDetailLines(LivingEntity entity) {
        return List.of(
            "§5会心一击",
            "§7注视目标 §e2.5秒 §7后攻击触发",
            "§7造成 §e2倍 §7伤害 + §e" + (getLevel() * 5) + "% §7改血伤害"
        );
    }

    // ==================== 公开工具方法 ====================

    /** 检查玩家锁定是否就绪 */
    public static boolean isReady(Player player) {
        int timer = PlayerDataAPI.get(player, DATA_TIMER);
        return timer >= LOCK_TICKS;
    }

    /** 获取锁定的目标 UUID */
    public static String getTargetUuid(Player player) {
        return PlayerDataAPI.get(player, DATA_TARGET);
    }

    /** 获取玩家当前等级 */
    public static int getPlayerLevel(Player player) {
        int lvl = TalentLevelTracker.getLevel(player, ID);
        return lvl > 0 ? lvl : 1;
    }

    /** 重置玩家锁定状态 */
    public static void reset(Player player) {
        PlayerDataAPI.set(player, DATA_TIMER, 0);
        PlayerDataAPI.set(player, DATA_TARGET, "");
        EntityLockAPI.unlock(player);
        var inst = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        if (inst != null) inst.removeModifier(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("yizmodqzk", "entity_crit_range"));
    }

    /** 60°锥内最接近屏幕中心的实体（与客户端 CriticalStrikeProvider 一致） */
    public static LivingEntity findTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        var look = player.getLookAngle();
        double range = 32.0;
        LivingEntity best = null;
        double bestDot = 0.5, bestDist = Double.MAX_VALUE;
        for (var entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range))) {
            if (entity == player || !entity.isAlive()) continue;
            Vec3 to = entity.position().subtract(eye);
            double d2 = to.lengthSqr();
            if (d2 > range * range) continue;
            double dot = look.dot(to) / Math.sqrt(d2);
            // 同方向优先选近的实体
            if (best != null && Math.abs(dot - bestDot) < 0.05) {
                if (d2 < bestDist) { bestDot = dot; best = entity; bestDist = d2; }
            } else if (dot > bestDot) {
                bestDot = dot; best = entity; bestDist = d2;
            }
        }
        return best;
    }
}
