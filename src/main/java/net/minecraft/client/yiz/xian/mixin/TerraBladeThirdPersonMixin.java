package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称：ILeftHandRender 武器强制 LEFT 手管线 + 攻击关键帧动画。
 * 关键帧来自用户 1~4.bbmodel 的 thirdperson_lefthand display transform。
 */
@Mixin(ItemInHandLayer.class)
public abstract class TerraBladeThirdPersonMixin {

    @Invoker("renderArmWithItem")
    public abstract void invokeRenderArmWithItem(
        LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
        PoseStack ps, MultiBufferSource buf, int light
    );

    private static final float[][] KF = {
        {-5,  89, 155,   0.25f, -15.00f, -1.25f, 1.70f, 1.70f, 0.79f, 0.00f},
        {12,  -1, -99,  13.00f,  14.25f,  6.75f, 1.70f, 1.70f, 0.79f, 0.35f},
        { 5,   6, -45,  -5.25f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 0.65f},
        { 5,   6,  -3, -24.50f,  21.95f,  9.00f, 1.70f, 1.70f, 0.44f, 1.00f},
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

        Player player = entity instanceof Player p ? p : null;

        // ── 攻击关键帧 ──
        if (player != null && player.swinging) {
            long now = System.currentTimeMillis();
            if (swingStartMs == 0) swingStartMs = now;
            float cd = player.getCurrentItemAttackStrengthDelay();
            if (cd <= 0f) cd = 20f;
            float elapsed = (now - swingStartMs) / 1000f;
            if (elapsed < cd / 20f) {
                float s = (float) Math.sin((elapsed / (cd / 20f)) * Math.PI);
                if (s > 0f) {
                    int idx = ComboStateMachine.getCurrentAnimIndex(player);
                    if (idx < 0) idx = 0;
                    interpolate(KF, s, BUF);
                    ps.translate(BUF[3] / 16f, BUF[4] / 16f, BUF[5] / 16f);
                    ps.mulPose(new Quaternionf().rotationXYZ(
                        (float)Math.toRadians(BUF[0]), (float)Math.toRadians(BUF[1]), (float)Math.toRadians(BUF[2])));
                    ps.scale(BUF[6], BUF[7], BUF[8]);
                }
            } else {
                swingStartMs = 0;
            }
        }

        invokeRenderArmWithItem(entity, stack,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT,
            ps, buf, light);
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
