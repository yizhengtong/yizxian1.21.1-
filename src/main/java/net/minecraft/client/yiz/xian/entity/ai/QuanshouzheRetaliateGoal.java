package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.core.StatusEffectDispatcher;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 全首者反击 Goal — 中立生物：不主动索敌，被攻击后锁定攻击者反击。
 * <p>优先规则：若最近攻击过本 Boss 的实体中有<b>玩家</b>，则优先锁定玩家（多攻击者场景），
 * 否则锁定 vanilla 的最后攻击者（{@link LivingEntity#getLastHurtByMob}）。</p>
 */
public class QuanshouzheRetaliateGoal extends Goal {

    private final QuanshouzheEntity mob;
    private LivingEntity target;

    public QuanshouzheRetaliateGoal(QuanshouzheEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // 1) 攻击者中有玩家 → 优先锁定玩家（跳过创造模式/无敌，避免 Boss 盯着打不到的玩家发呆）
        LivingEntity playerAttacker = mob.getRecentPlayerAttacker();
        if (playerAttacker != null && isValidTarget(playerAttacker)) {
            this.target = playerAttacker;
            return true;
        }
        // 2) 否则锁定 vanilla 的最后攻击者（被其他生物攻击时正常还手）
        LivingEntity lastHurt = mob.getLastHurtByMob();
        if (lastHurt != null && isValidTarget(lastHurt)) {
            this.target = lastHurt;
            return true;
        }
        return false;
    }

    /** 有效反击目标：存活、非无敌、非创造模式玩家。 */
    private static boolean isValidTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()) return false;
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(this.target);
        this.target = null;
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return t != null && isValidTarget(t)
            && !StatusEffectDispatcher.hasHardControl(mob);
    }

    @Override
    public void stop() {
        // 当前目标无效（创造/无敌/死亡）时清空，避免 Boss 一直盯着打不到的实体发呆
        LivingEntity t = mob.getTarget();
        if (t != null && !isValidTarget(t)) {
            mob.setTarget(null);
        }
    }
}
