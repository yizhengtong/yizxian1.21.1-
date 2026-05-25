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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

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
        // 充能进度 + 就绪状态传给渲染
        float charge = (float) newTimer / LOCK_TICKS;
        boolean ready = newTimer >= LOCK_TICKS;
        EntityLockAPI.lock(player, target, charge, ready);
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
    }

    /** 8格内最近存活且有视线的目标 */
    public static LivingEntity findTarget(Player player) {
        AABB box = player.getBoundingBox().inflate(RANGE);
        LivingEntity nearest = null;
        double closestDist = RANGE * RANGE;

        for (var entity : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == player || !entity.isAlive()) continue;
            if (!player.hasLineOfSight(entity)) continue;
            double dist = player.distanceToSqr(entity);
            if (dist < closestDist) {
                closestDist = dist;
                nearest = entity;
            }
        }
        return nearest;
    }
}
