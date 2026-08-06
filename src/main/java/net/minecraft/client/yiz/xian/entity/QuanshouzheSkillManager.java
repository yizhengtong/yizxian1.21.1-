package net.minecraft.client.yiz.xian.entity;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 辖界者（同人坚守者）技能执行器 — 套用原版 Warden 的攻击：
 * 咆哮（AoE 穿甲伤害 50）+ 音爆（复刻 Warden SonicBoom：射线粒子 + 音效 + 伤害 + 击退）。
 * 具体逻辑在 {@link QuanshouzheEntity#performRoar} / {@link QuanshouzheEntity#performSonicBoom}。
 */
public final class QuanshouzheSkillManager {

    private QuanshouzheSkillManager() {}

    /** 技能执行入口（当前无远程技能，纯近战）。 */
    public static void execute(QuanshouzheEntity boss, int phase, LivingEntity target) {
        if (boss.level().isClientSide() || !boss.isAlive()) return;
        // 当前辖界者仅近战（MeleeGoal 直接 doHurtTarget），无远程技能
    }

    /** 预留清理入口（当前无持久状态）。 */
    public static void clear(UUID uuid) {}
}
