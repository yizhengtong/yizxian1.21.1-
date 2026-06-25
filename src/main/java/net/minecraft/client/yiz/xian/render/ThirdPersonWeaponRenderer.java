package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/**
 * 第三人称武器独立渲染器 — 在玩家手部世界坐标渲染武器 + 攻击动画。
 *
 * <p>与第一人称同理：取消手持渲染后，由本渲染器接管，在世界空间渲染。
 * 武器与手臂模型完全解耦，手臂姿势不受影响。
 */
public final class ThirdPersonWeaponRenderer {

    private ThirdPersonWeaponRenderer() {}

    // ── 关键帧 (thirdperson_lefthand) ──
    private static final float[][] KF = {
        {-5,  89, 155,   0.25f, -15.00f, -1.25f, 1.70f, 1.70f, 0.79f, 0.00f},
        {12,  -1, -99,  13.00f,  14.25f,  6.75f, 1.70f, 1.70f, 0.79f, 0.35f},
        { 5,   6, -45,  -5.25f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 0.65f},
        { 5,   6,  -3, -24.50f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 1.00f},
    };
    private static final float[] BUF = new float[9];

    // ── 手部定位参数（可调）──
    public static float handOffsetY = 1.2f;   // 从脚底到手的高度
    public static float handOffsetSide = -0.35f; // 左右（负=左）

    /** swing 计时 */
    private static long swingStartMs = 0;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ItemRenderer ir = mc.getItemRenderer();
        Camera cam = mc.gameRenderer.getMainCamera();

        for (Player player : mc.level.players()) {
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ILeftHandRender)) continue;

            // ── swing timer ──
            long now = System.currentTimeMillis();
            if (player.swinging && swingStartMs == 0) swingStartMs = now;
            float cd = player.getCurrentItemAttackStrengthDelay();
            if (cd <= 0f) cd = 20f;
            float duration = cd / 20f;
            float elapsed = (now - swingStartMs) / 1000f;
            if (elapsed >= duration) { swingStartMs = 0; continue; }
            float swing = (float) Math.sin((elapsed / duration) * Math.PI);
            if (swing <= 0f) continue;

            int idx = ComboStateMachine.getCurrentAnimIndex(player);
            if (idx < 0) idx = 0;
            interpolate(KF, swing, BUF);

            // ── 玩家世界位置 ──
            double px = Mth.lerp(partial, player.xo, player.getX());
            double py = Mth.lerp(partial, player.yo, player.getY());
            double pz = Mth.lerp(partial, player.zo, player.getZ());
            float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);

            ps.pushPose();
            // ① 定位到玩家世界位置
            ps.translate(px - cam.getPosition().x, py - cam.getPosition().y, pz - cam.getPosition().z);
            // ② 旋转到玩家朝向
            ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
            // ③ 偏移到手部位置（局部坐标：+X左, +Y上, -Z前）
            ps.translate(handOffsetSide, handOffsetY, 0.2f);
            // ④ 手臂基础旋转
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            // ⑤ 关键帧 display transform
            ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
            ps.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(BUF[0]),
                (float) Math.toRadians(BUF[1]),
                (float) Math.toRadians(BUF[2])));
            ps.scale(BUF[6], BUF[7], BUF[8]);
            // ⑥ element 朝向
            ps.mulPose(new Quaternionf()
                .rotateZ((float) Math.toRadians(BlockbenchAnimParser.elemRotZ))
                .rotateY((float) Math.toRadians(BlockbenchAnimParser.elemRotY))
                .rotateX((float) Math.toRadians(BlockbenchAnimParser.elemRotX)));
            // ⑦ 渲染
            ir.renderStatic(stack, ItemDisplayContext.NONE,
                15728880, 0, ps, buf, player.level(), player.getId());
            ps.popPose();
        }
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
