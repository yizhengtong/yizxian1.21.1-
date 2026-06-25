package net.minecraft.client.yiz.xian.api;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/**
 * 攻击动画 PoseStack 引擎。
 *
 * <p>优先使用 BlockbenchAnimParser 解析的 .bbmodel 动画数据
 * （用户在 Blockbench 中编辑的关键帧），
 * 未加载时回退到旧 AnimConfigData 7 帧系统。</p>
 *
 * <p>旋转顺序：Z→Y→X，与 Blockbench 默认一致。</p>
 */
public final class BlockbenchAnimLoader {

    private BlockbenchAnimLoader() {}

    /** 复用避免每帧 new */
    private static final float[] ROT_BUF = new float[3];

    /** 枢轴偏移（方块单位，在 element 旋转后的空间）。
     *  R_elem=[90,180,0] 将原始 (0,y,0) → (0,0,-y)，
     *  剑柄原在模型下方 y≈-0.5 → 旋转后在 z≈-0.5。
     *  枢轴 (0,0,-0.5) 把剑柄移到原点 = 旋转中心。 */
    public static float pivotX = 0f, pivotY = 0f, pivotZ = -0.5f;

    /**
     * swing 0→1，从 Blockbench 动画关键帧插值旋转并应用到 PoseStack。
     */
    public static void applyAttack(PoseStack ps, int idx, float swing) {
        if (swing <= 0.01f || idx < 0 || idx >= BlockbenchAnimParser.ANIM_COUNT) return;

        if (BlockbenchAnimParser.loaded) {
            BlockbenchAnimParser.getRotation(idx, swing, ROT_BUF);
            float erx = BlockbenchAnimParser.elemRotX;
            float ery = BlockbenchAnimParser.elemRotY;
            float erz = BlockbenchAnimParser.elemRotZ;

            // R_elem 把 2D 贴图翻了 90°，原始 Y 轴(剑柄方向)变到了 Z 轴。
            // 枢轴必须在 R_elem 之后、R_anim 之前施加（旋转后的空间）。
            // 矩阵: T(-p') * R_anim * T(p') * R_elem
            // 其中 p' = R_elem(原始剑柄位置)

            ps.translate(-pivotX, -pivotY, -pivotZ);         // T(-p): 枢轴移回
            ps.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(ROT_BUF[2]))
                .rotateY((float) Math.toRadians(ROT_BUF[1]))
                .rotateX((float) Math.toRadians(ROT_BUF[0])));
            ps.translate(pivotX, pivotY, pivotZ);            // T(p): 枢轴移到原点
            ps.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(erz))
                .rotateY((float) Math.toRadians(ery))
                .rotateX((float) Math.toRadians(erx)));
        } else {
            // ── Fallback: 旧 AnimConfigData 7 帧 Catmull-Rom ──
            legacyApply(ps, idx, swing);
        }
    }

    // ── Fallback: 旧系统 ──

    private static final int FRAMES = 7;

    private static void legacyApply(PoseStack ps, int idx, float swing) {
        float fp = swing * (FRAMES - 1);
        int seg = (int) fp;
        if (seg >= FRAMES - 1) seg = FRAMES - 2;
        float t = fp - seg;

        int i0 = Math.max(0, seg - 1);
        int i1 = seg;
        int i2 = Math.min(FRAMES - 1, seg + 1);
        int i3 = Math.min(FRAMES - 1, seg + 2);

        float[] p0 = AnimConfigData.KEYFRAMES[idx][i0];
        float[] p1 = AnimConfigData.KEYFRAMES[idx][i1];
        float[] p2 = AnimConfigData.KEYFRAMES[idx][i2];
        float[] p3 = AnimConfigData.KEYFRAMES[idx][i3];

        float rx = cr(p0[0], p1[0], p2[0], p3[0], t);
        float ry = cr(p0[1], p1[1], p2[1], p3[1], t);
        float rz = cr(p0[2], p1[2], p2[2], p3[2], t);
        float px = cr(p0[3], p1[3], p2[3], p3[3], t);
        float py = cr(p0[4], p1[4], p2[4], p3[4], t);
        float pz = cr(p0[5], p1[5], p2[5], p3[5], t);
        float sc = cr(p0[6], p1[6], p2[6], p3[6], t);

        ps.scale(sc, sc, sc);
        ps.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(rz))
                                    .rotateY((float) Math.toRadians(ry))
                                    .rotateX((float) Math.toRadians(rx)));
        ps.translate(px, py, pz);
    }

    private static float cr(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2f*p1) + (-p0+p2)*t + (2f*p0-5f*p1+4f*p2-p3)*t2 + (-p0+3f*p1-3f*p2+p3)*t3);
    }
}
