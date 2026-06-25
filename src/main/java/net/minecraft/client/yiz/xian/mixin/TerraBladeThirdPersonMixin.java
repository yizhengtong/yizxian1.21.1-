package net.minecraft.client.yiz.xian.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称：强制 LEFT 手管线，攻击时用第一人称同款关键帧数据。
 *
 * <p>复刻第一人称的渲染管线适配到第三人称：
 * <pre>
 * [左臂 pose] → [同款 firstperson_righthand 关键帧] → [element rotation] → NONE 渲染
 * </pre>
 */
@Mixin(ItemInHandLayer.class)
public abstract class TerraBladeThirdPersonMixin {

    @Invoker("renderArmWithItem")
    public abstract void invokeRenderArmWithItem(
        LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
        PoseStack ps, MultiBufferSource buf, int light
    );

    /** 与 FirstPersonSwordRenderer.ANIM_A 完全相同的关键帧数据 */
    private static final float[][] KF = {
        {-52, -69, 180, -22.87f, -1.30f,  -1.12f, 1.00f, 1.00f, 0.44f, 0.00f},
        {-66,  -1, 108, -33.75f,  4.45f, -16.00f, 1.70f, 1.70f, 0.44f, 0.35f},
        {-82,  -1,  50,  -8.75f,  9.20f, -17.50f, 1.70f, 1.70f, 0.44f, 0.65f},
        {-66,  -1,   1,  20.13f,  4.45f, -16.00f, 1.70f, 1.77f, 0.44f, 1.00f},
    };
    private static final float[] BUF = new float[9];
    private static long swingStartMs = 0;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void yizxian_tp(
            LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
            PoseStack ps, MultiBufferSource buf, int light, CallbackInfo ci
    ) {
        if (!(stack.getItem() instanceof ILeftHandRender)) return;
        if (arm == HumanoidArm.LEFT) return;
        ci.cancel();

        // ── 计算攻击进度 ──
        float swing = 0f;
        if (entity instanceof Player player && player.swinging) {
            long now = System.currentTimeMillis();
            if (swingStartMs == 0) swingStartMs = now;
            float cd = player.getCurrentItemAttackStrengthDelay();
            if (cd <= 0f) cd = 20f;
            float elapsed = (now - swingStartMs) / 1000f;
            if (elapsed < cd / 20f) {
                swing = (float) Math.sin((elapsed / (cd / 20f)) * Math.PI);
            } else {
                swingStartMs = 0;
            }
        }

        if (swing <= 0f) {
            // 待机 → 原版 LEFT 手管线
            invokeRenderArmWithItem(entity, stack,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
                ps, buf, light);
            return;
        }

        int idx = 0;
        if (entity instanceof Player p) {
            idx = ComboStateMachine.getCurrentAnimIndex(p);
            if (idx < 0) idx = 0;
        }
        interpolate(KF, swing, BUF);

        // ── 渲染：复刻第一人称管线到第三人称 ──
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        ps.pushPose();

        // ① 左臂 ITEM pose 定位
        ps.translate(5.0f / 16.0f, 2.0f / 16.0f, 3.0f / 16.0f);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.translate(0, 0, -1.0f / 16.0f);
        ps.mulPose(Axis.YP.rotationDegrees(180));

        // ② 同款 firstperson_righthand 关键帧
        ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
        ps.mulPose(new Quaternionf().rotationXYZ(
            (float)Math.toRadians(BUF[0]), (float)Math.toRadians(BUF[1]), (float)Math.toRadians(BUF[2])));
        ps.scale(BUF[6], BUF[7], BUF[8]);

        // ③ element 朝向（2D→3D）
        ps.mulPose(new Quaternionf()
            .rotateZ((float)Math.toRadians(BlockbenchAnimParser.elemRotZ))
            .rotateY((float)Math.toRadians(BlockbenchAnimParser.elemRotY))
            .rotateX((float)Math.toRadians(BlockbenchAnimParser.elemRotX)));

        // ④ 渲染
        ir.renderStatic(stack, ItemDisplayContext.NONE, light, 0,
            ps, buf, entity.level(), entity.getId());
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
                    out[k] = net.minecraft.util.Mth.lerp(f, kfs[i][k], kfs[i + 1][k]);
                return;
            }
        }
        System.arraycopy(kfs[n - 1], 0, out, 0, 9);
    }
}
