package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 辖界者近战挥砍 Goal — 贴近目标即时结算伤害（动画与伤害同帧，原版 Warden 逻辑）。
 * 攻击动画 = Warden 关键帧（broadcastEntityEvent byte 4 触发）；狂暴时攻击间隔减半。
 */
public class QuanshouzheMeleeGoal extends Goal {

    /** 攻击距离（格）—— 随体积放大 1.5 倍（原 3.5 → 5.25）。 */
    private static final double ATTACK_RANGE = 5.25;
    /** 基础攻击间隔 12 tick（0.6 秒）；狂暴 6 tick（0.3 秒），狂暴感明显。 */
    private static final int ATTACK_INTERVAL = 12;
    private static final int ATTACK_INTERVAL_RAGE = 6;

    private final QuanshouzheEntity mob;
    private int attackCooldown;

    public QuanshouzheMeleeGoal(QuanshouzheEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
            && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
            && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
    }

    @Override
    public void tick() {
        // C 重击期间：原地（不追击），播放放缓动画，推进重击计时
        if (this.mob.isHeavyAttacking()) {
            if (this.attackCooldown > 0) this.attackCooldown--;
            // 重击推进由实体 aiStep 负责（防止目标丢失/Goal 停止时重击状态残留卡住）
            LivingEntity t = this.mob.getTarget();
            if (t != null) this.mob.getLookControl().setLookAt(t, 30f, 30f);
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;
        this.mob.getLookControl().setLookAt(target, 30f, 30f);
        double distSq = this.mob.distanceToSqr(target);
        // 目标在攻击范围外 → 追击（vanilla MeleeAttackGoal 同款）
        if (distSq > ATTACK_RANGE * ATTACK_RANGE) {
            this.mob.getNavigation().moveTo(target, 1.0);
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }
        if (distSq <= ATTACK_RANGE * ATTACK_RANGE) {
            this.attackCooldown = this.mob.isRaging() ? ATTACK_INTERVAL_RAGE : ATTACK_INTERVAL;
            this.mob.swing(InteractionHand.MAIN_HAND);
            // A/B/C 攻击逻辑（含重击）在实体侧执行；攻击动画由 attackTarget 内部广播
            this.mob.attackTarget(target);
        }
    }
}
