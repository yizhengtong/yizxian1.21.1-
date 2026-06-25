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

    /** 动画 A：左→右平砍（KF1~4 来自用户 1~4.bbmodel，首尾为待机帧）。 */
    private static final float[][] ANIM_A = {
        {-81, -98, -157, -17.12f, 2.95f,   1.38f,  1, 1, 0.44f, 0.00f},  // 待机
        {-66, -34,  160, -19.87f, 2.45f,  -2.37f,  1, 1, 0.44f, 0.14f},  // KF1 拔刀起手
        {-66,  -1,   64, -19.87f, 4.45f, -10.37f,  1, 1, 0.44f, 0.38f},  // KF2
        {-66,  -1,   50, -11.12f, 4.45f, -15.37f,  1, 1, 0.44f, 0.60f},  // KF3
        {-66,  -1,   12,   6.63f, 4.45f, -10.37f,  1, 1, 0.44f, 0.82f},  // KF4 收尾
        {-81, -98, -157, -17.12f, 2.95f,   1.38f,  1, 1, 0.44f, 1.00f},  // 回待机
    };

    /** 4 套动画：A=左→右平砍。B/C/D 暂复用 A，待用户提供关键帧后替换。 */
    private static final float[][][] ANIMS = { ANIM_A, ANIM_A, ANIM_A, ANIM_A };

    private static final float[] BUF = new float[9];

    /**
     * 在已 translate 到基础右手位后调用，应用插值后的 firstperson_righthand 变换。
     *
     * @param ps       PoseStack（已 translate(0.56,-0.52,-0.72)）
     * @param animIdx  动画索引 0~3
     * @param progress 攻击进度 0→1（getAttackAnim）
     */
    public static void applyTransform(PoseStack ps, int animIdx, float progress) {
        if (animIdx < 0 || animIdx >= ANIMS.length) animIdx = 0;
        interpolate(ANIMS[animIdx], Mth.clamp(progress, 0f, 1f), BUF);

        // 复刻原版 ItemTransform.apply（右手，非镜像）
        ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
        ps.mulPose(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(BUF[0]),
            (float) Math.toRadians(BUF[1]),
            (float) Math.toRadians(BUF[2])));
        ps.scale(BUF[6], BUF[7], BUF[8]);
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
