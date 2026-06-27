package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * 第一人称剑攻击动画 — 关键帧插值渲染。
 *
 * <p>关键帧来自用户在 Blockbench 中用 {@code firstperson_righthand} display transform
 * 摆出的姿态（1.bbmodel ~ 4.bbmodel）。运行时按攻击进度 0→1 在关键帧间线性插值。
 *
 * <p>渲染方式与原版一致：基础右手位 translate(0.56,-0.52,-0.72)
 * → 插值后的 display transform（translate/rotate/scale）
 * → 物品几何（NONE 上下文，不再叠加 display 变换）。
 */
public final class FirstPersonSwordRenderer {

    private FirstPersonSwordRenderer() {}

    // 关键帧格式: {rotX, rotY, rotZ, transX, transY, transZ, scaleX, scaleY, scaleZ, time}
    //   rot 单位度；trans 单位像素(渲染时 /16)；time 为该帧在 0→1 进度上的位置。
    //   待机姿态 = 模型自带 firstperson_righthand [-81,-98,-157] [-17.12,2.95,1.38]，
    //   作为动画首尾帧：静止显示待机，攻击时 待机→KF1→…→KF4→待机。

    /** 待机姿态（模型自带 firstperson_righthand）。 */
    private static final float[] IDLE =
        {-52, -69, -180, -22.87f, -1.3f, -1.12f, 1, 1, 0.44f, 0f};

    /** 动画 A：挥砍1 — 左→右平砍（来自用户 1~4.bbmodel）。 */
    private static final float[][] ANIM_A = {
        {-52, -69, 180, -22.87f, -1.30f,  -1.12f, 1.00f, 1.00f, 0.44f, 0.00f},  // KF1
        {-66,  -1, 108, -33.75f,  4.45f, -16.00f, 1.70f, 1.70f, 0.44f, 0.35f},  // KF2
        {-82,  -1,  50,  -8.75f,  9.20f, -17.50f, 1.70f, 1.70f, 0.44f, 0.65f},  // KF3
        {-66,  -1,   1,  20.13f,  4.45f, -16.00f, 1.70f, 1.77f, 0.44f, 1.00f},  // KF4
    };

    /** 动画 B：挥砍2 — 左下→右上撩击（来自用户 22/33/44.bbmodel，更新版）。 */
    private static final float[][] ANIM_B = {
        {-54, -11, 180, -23.37f,  5.00f, -17.62f, 1.70f, 1.70f, 0.44f, 0.00f},  // KF1 左下
        {-51, -22, 137,  -2.62f, 16.75f, -18.12f, 1.70f, 1.70f, 0.44f, 0.50f},  // KF2 中段
        {-51, -22, 137,  13.88f, 25.00f, -18.12f, 1.70f, 1.70f, 0.44f, 1.00f},  // KF3 右上
    };

    /** 动画数组：A=挥砍1, B=挥砍2。C/D 暂复用 A。 */
    private static final float[][][] ANIMS = { ANIM_A, ANIM_B, ANIM_A, ANIM_A };

    private static final float[] BUF = new float[9];

    /** 动画速度倍率（1.0=与冷却同步, >1=加快, <1=放慢） */
    public static float speedMultiplier = 1.0f;

    /** swing 计时：攻击开始时刻（系统毫秒），0 表示无攻击 */
    private static long swingStartMs = 0;

    /**
     * 在已 translate 到基础右手位后调用，应用插值后的 firstperson_righthand 变换。
     *
     * <p>动画时长自动从武器攻速推算：duration = getCurrentItemAttackStrengthDelay / 20 * speedMultiplier。
     * 改变武器攻速属性 → 冷却和动画同步变化。</p>
     *
     * @param ps       PoseStack（已 translate(0.56,-0.52,-0.72)）
     * @param animIdx  动画索引 0~3
     * @param player   本地玩家（用于检测 swinging 和读攻速）
     */
    public static void applyTransform(PoseStack ps, int animIdx, net.minecraft.client.player.LocalPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (animIdx < 0 || animIdx >= ANIMS.length) animIdx = 0;

        long now = System.currentTimeMillis();
        if (player.swinging && swingStartMs == 0) {
            swingStartMs = now;
        }

        // 动画时长从武器自身的攻速推算（不依赖主手）
        float cooldownTicks = getCooldownTicks(stack);
        if (cooldownTicks <= 0f) cooldownTicks = 20f;
        float duration = (cooldownTicks / 20f) * speedMultiplier;

        float elapsed = (now - swingStartMs) / 1000f;
        if (elapsed >= duration) {
            swingStartMs = 0;
            applyIdle(ps);
            return;
        }

        float t = elapsed / duration;
        float swing = (float) Math.sin(t * Math.PI);

        interpolate(ANIMS[animIdx], swing, BUF);

        ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
        ps.mulPose(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(BUF[0]),
            (float) Math.toRadians(BUF[1]),
            (float) Math.toRadians(BUF[2])));
        ps.scale(BUF[6], BUF[7], BUF[8]);
    }

    /** 从 ItemStack 的攻速属性推算冷却 tick 数 */
    private static float getCooldownTicks(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.client.yiz.xian.item.MeleeWeaponItem mw) {
            double speed = mw.getAttackSpeed();
            if (speed > 0) return (float)(20.0 / speed);
        }
        return 20f; // 默认 1.0s
    }

    private static void applyIdle(PoseStack ps) {
        ps.translate(IDLE[3] / 16f, IDLE[4] / 16f, IDLE[5] / 16f);
        ps.mulPose(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(IDLE[0]),
            (float) Math.toRadians(IDLE[1]),
            (float) Math.toRadians(IDLE[2])));
        ps.scale(IDLE[6], IDLE[7], IDLE[8]);
    }

    /** 按 time 在关键帧间线性插值，结果写入 out[0..8]。 */
    private static void interpolate(float[][] kfs, float t, float[] out) {
        int n = kfs.length;
        if (t <= kfs[0][9]) { System.arraycopy(kfs[0], 0, out, 0, 9); return; }
        if (t >= kfs[n - 1][9]) { System.arraycopy(kfs[n - 1], 0, out, 0, 9); return; }
        for (int i = 0; i < n - 1; i++) {
            float t0 = kfs[i][9], t1 = kfs[i + 1][9];
            if (t >= t0 && t <= t1) {
                float f = (t1 - t0) < 1e-5f ? 0f : (t - t0) / (t1 - t0);
                for (int k = 0; k < 9; k++)
                    out[k] = Mth.lerp(f, kfs[i][k], kfs[i + 1][k]);
                return;
            }
        }
        System.arraycopy(kfs[n - 1], 0, out, 0, 9);
    }
}
