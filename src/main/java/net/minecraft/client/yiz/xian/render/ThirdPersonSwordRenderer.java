package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * 第三人称剑攻击动画 — 关键帧插值渲染（thirdperson_lefthand display transform）。
 *
 * <p>与第一人称同理：用户 Blockbench 摆好关键帧，运行时按攻击进度插值。
 */
public final class ThirdPersonSwordRenderer {

    private ThirdPersonSwordRenderer() {}

    // 关键帧格式: {rotX, rotY, rotZ, transX, transY, transZ, scaleX, scaleY, scaleZ, time}

    /** 待机姿态（模型自带 thirdperson_lefthand）。 */
    private static final float[] IDLE =
        {-5, 89, 149, 0, -10.75f, 1.25f, 1, 1.19f, 0.79f, 0f};

    /** 动画 A：左→右平砍（来自用户 1~4.bbmodel 的 thirdperson_lefthand）。 */
    private static final float[][] ANIM_A = {
        {-5,  89, 155,   0.25f, -15.00f, -1.25f, 1.70f, 1.70f, 0.79f, 0.00f},  // KF1
        {12,  -1, -99,  13.00f,  14.25f,  6.75f, 1.70f, 1.70f, 0.79f, 0.35f},  // KF2
        { 5,   6, -45,  -5.25f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 0.65f},  // KF3
        { 5,   6,  -3, -24.50f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 1.00f},  // KF4
    };

    private static final float[][][] ANIMS = { ANIM_A, ANIM_A, ANIM_A, ANIM_A };
    private static final float[] BUF = new float[9];

    /** 攻击动画总时长（秒），与 WeaponAnimMixin 一致 */
    public static final float SWING_DURATION = 1.2f;
    private static long swingStartMs = 0;

    /**
     * 渲染第三人称武器攻击动画。
     * 在 renderArmWithItem 取消后调用，PS 已在实体模型 body 中心坐标系。
     *
     * @param mc      Minecraft 实例
     * @param entity  玩家实体
     * @param stack   武器 ItemStack
     * @param ps      当前 PoseStack（已在实体 body 中心）
     * @param buf     渲染 buffer
     * @param light   光照
     */
    public static void render(net.minecraft.client.Minecraft mc,
                              net.minecraft.world.entity.LivingEntity entity,
                              ItemStack stack, PoseStack ps,
                              net.minecraft.client.renderer.MultiBufferSource buf, int light) {
        if (!(stack.getItem() instanceof ILeftHandRender)) return;
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) return;

        // swing timer
        long now = System.currentTimeMillis();
        if (player.swinging && swingStartMs == 0) swingStartMs = now;
        float elapsed = (now - swingStartMs) / 1000f;
        if (elapsed >= SWING_DURATION) { swingStartMs = 0; return; }

        float t = elapsed / SWING_DURATION;
        float swing = (float) Math.sin(t * Math.PI);
        if (swing <= 0f) return;

        int animIdx = ComboStateMachine.getCurrentAnimIndex(player);
        if (animIdx < 0 || animIdx >= ANIMS.length) animIdx = 0;

        ps.pushPose();

        // ── 手臂姿势角度焊死在 0（无 limb swing 影响）──
        //    在模型 body 中心，直接转到左肩位置（不参与 swing 动画）
        ps.translate(-0.31, 0.75, 0.125);  // 左臂 pivot 近似偏移（-5/16, 12/16, 2/16）

        // ── 关键帧 display transform ──
        interpolate(ANIMS[animIdx], swing, BUF);
        ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
        ps.mulPose(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(BUF[0]),
            (float) Math.toRadians(BUF[1]),
            (float) Math.toRadians(BUF[2])));
        ps.scale(BUF[6], BUF[7], BUF[8]);

        // ── element 朝向（2D贴图→3D）──
        ps.mulPose(new Quaternionf()
            .rotateZ((float) Math.toRadians(BlockbenchAnimParser.elemRotZ))
            .rotateY((float) Math.toRadians(BlockbenchAnimParser.elemRotY))
            .rotateX((float) Math.toRadians(BlockbenchAnimParser.elemRotX)));

        // ── 渲染 ──
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
            light, 0, ps, buf, entity.level(), entity.getId());

        ps.popPose();
    }

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
