package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 剑气能量波。使用手中武器模型 + glow_edge 着色器渲染。
 */
public final class EnergyWaveRenderer {

    private static final List<Wave> WAVES = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUF =
        MultiBufferSource.immediate(new ByteBufferBuilder(512));

    private EnergyWaveRenderer() {}

    public static void spawn(Vec3 pos, float yawDeg, float pitchDeg, ItemStack item, int level) {
        WAVES.add(new Wave(pos, yawDeg, pitchDeg, item, level, System.currentTimeMillis()));
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (WAVES.isEmpty()) return;

        var mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        double cx = cam.getPosition().x, cy = cam.getPosition().y, cz = cam.getPosition().z;
        var stack = event.getPoseStack();
        var edgeRt = net.minecraft.client.yiz.xian.render.glow.OutlineRenderType.GLOW_EDGE;
        var shader = net.minecraft.client.yiz.xian.render.glow.OutlineShaders.getGlowEdge();
        if (shader == null) return;

        shader.safeGetUniform("uScreenOffset").set(0f, 0f);
        shader.safeGetUniform("uType").set(0);
        var ss = net.minecraft.client.yiz.xian.render.glow.OutlineShaders.getScreenSize();
        shader.safeGetUniform("screenSize").set(ss.x, ss.y);

        long now = System.currentTimeMillis();
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            if (now - w.born > 3000) { it.remove(); continue; }

            float life = (now - w.born) / 3000f;
            float alpha = 1f - life;

            shader.safeGetUniform("time").set((now % 60000L) / 1000.0F);

            BakedModel model = mc.getItemRenderer().getModel(w.item, mc.level, mc.player, 0);
            if (model == null) continue;

            var tint = net.minecraft.client.yiz.util.StagedItemHelper.glowColorForLevel(w.level);
            if (tint == null) tint = new org.joml.Vector4f(0.8f, 0.2f, 1.0f, 1f);
            shader.safeGetUniform("uColor").set(tint.x * 2f, tint.y * 2f, tint.z * 2f, alpha);

            stack.pushPose();
            stack.translate(w.pos.x - cx, w.pos.y - cy, w.pos.z - cz);
            stack.mulPose(Axis.YP.rotationDegrees(-w.yaw));
            stack.mulPose(Axis.XP.rotationDegrees(w.pitch));
            stack.translate(0, 0, -3);
            stack.scale(2f, 2f, 2f);

            var vc = BUF.getBuffer(edgeRt);
            for (BakedModel pass : model.getRenderPasses(w.item, true)) {
                for (var quad : pass.getQuads(null, null,
                        net.minecraft.util.RandomSource.create(),
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
                    vc.putBulkData(stack.last(), quad,
                        tint.x * 2f, tint.y * 2f, tint.z * 2f, alpha,
                        15728880, OverlayTexture.NO_OVERLAY, true);
                }
            }
            BUF.endBatch(edgeRt);
            stack.popPose();
        }
    }

    private record Wave(Vec3 pos, float yaw, float pitch, ItemStack item, int level, long born) {}
}
