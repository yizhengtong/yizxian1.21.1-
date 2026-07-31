package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 紫昭明光 — 世界渲染（无实体系统，仿闪电）。
 * <p>在世界渲染阶段（AFTER_PARTICLES）读取客户端 FX 数据 {@link ZhaoMingLightClientData}，
 * 画真正的 3D 等离子球体。位置由服务端 S2C 同步，所有视角可见。</p>
 * <p>顶点 Position = 世界坐标（相对相机），Color = 球面法线方向（fsh 的 3D 坐标）。</p>
 */
public final class ZhaoMingLightWorldRenderer {

    /** 球体半径（世界单位） */
    private static final float SIZE = 0.55f;
    private static final int LAT_SEG = 10;
    private static final int LON_SEG = 16;

    private ZhaoMingLightWorldRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ZhaoMingLightClientManager.getInstance().all().isEmpty()) return;
        ShaderInstance shader = ZhaoMingLightShaders.getPlasma();
        if (shader == null) return;

        Vec3 cam = event.getCamera().getPosition();
        var uTime = shader.getUniform("time");
        if (uTime != null) {
            uTime.set((float) ((System.currentTimeMillis() % 60000L) / 1000.0));
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        Matrix4f mat = event.getPoseStack().last().pose();
        BufferBuilder bb = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        // 客户端本地模拟（泰拉棱镜模式）：位置全本地计算，无网络跳变
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var mc = net.minecraft.client.Minecraft.getInstance();
        var level = mc.level;
        int HOVERING = 2; // LocalFX.state

        // 盘旋阶段：按 owner 分组、id 排序分配角度（每帧本地绕圈平滑）
        java.util.Map<java.util.UUID, java.util.List<ZhaoMingLightClientManager.LocalFX>> hoverGroups = new java.util.HashMap<>();
        if (level != null) {
            for (var f : ZhaoMingLightClientManager.getInstance().all().values()) {
                if (f.state == HOVERING) {
                    hoverGroups.computeIfAbsent(f.owner, k -> new java.util.ArrayList<>()).add(f);
                }
            }
            for (var list : hoverGroups.values()) {
                list.sort(java.util.Comparator.comparingInt(f -> f.id));
            }
        }

        boolean wrote = false;
        for (ZhaoMingLightClientManager.LocalFX f : ZhaoMingLightClientManager.getInstance().all().values()) {
            Vec3 center;
            if (level != null && f.state == HOVERING) {
                net.minecraft.world.entity.player.Player owner = level.getPlayerByUUID(f.owner);
                if (owner != null) {
                    // 泰拉棱镜式：玩家插值位置 + 本地绕圈（每帧平滑）
                    var group = hoverGroups.getOrDefault(f.owner, java.util.List.of());
                    int idx = group.indexOf(f);
                    int count = Math.max(1, group.size());
                    double radius = 0.7 + 0.4 * (count - 1);
                    double angle = 2 * Math.PI * idx / count + (level.getGameTime() + partial) * 0.05;
                    double ox = net.minecraft.util.Mth.lerp(partial, owner.xo, owner.getX());
                    double oy = net.minecraft.util.Mth.lerp(partial, owner.yo, owner.getY());
                    double oz = net.minecraft.util.Mth.lerp(partial, owner.zo, owner.getZ());
                    double y = oy + owner.getEyeHeight() + 1.8;
                    center = new Vec3(ox + radius * Math.cos(angle), y, oz + radius * Math.sin(angle));
                } else {
                    center = lerpFX(f, partial);
                }
            } else {
                center = lerpFX(f, partial);
            }
            center = center.subtract(cam);
            emitSphere(bb, mat, center);
            wrote = true;
        }
        if (wrote) {
            BufferUploader.drawWithShader(bb.buildOrThrow());
        } else {
            bb.build();
        }

        RenderSystem.depthMask(true);
    }

    /** 本地模拟位置插值（两 tick 之间）。 */
    private static Vec3 lerpFX(ZhaoMingLightClientManager.LocalFX f, float partial) {
        return new Vec3(
                net.minecraft.util.Mth.lerp(partial, f.prevPosition.x, f.position.x),
                net.minecraft.util.Mth.lerp(partial, f.prevPosition.y, f.position.y),
                net.minecraft.util.Mth.lerp(partial, f.prevPosition.z, f.position.z));
    }

    /** 在世界坐标 center 处构建 UV 球体（QUADS）。 */
    private static void emitSphere(BufferBuilder bb, Matrix4f mat, Vec3 center) {
        for (int i = 0; i < LAT_SEG; i++) {
            double lat0 = Math.PI * i / LAT_SEG;
            double lat1 = Math.PI * (i + 1) / LAT_SEG;
            for (int j = 0; j < LON_SEG; j++) {
                double lon0 = 2 * Math.PI * j / LON_SEG;
                double lon1 = 2 * Math.PI * (j + 1) / LON_SEG;
                sphereV(bb, mat, center, lat0, lon0);
                sphereV(bb, mat, center, lat1, lon0);
                sphereV(bb, mat, center, lat1, lon1);
                sphereV(bb, mat, center, lat0, lon1);
            }
        }
    }

    private static void sphereV(BufferBuilder bb, Matrix4f mat, Vec3 center, double lat, double lon) {
        // 标准球参数化：lat 0→π 时 y=cos(lat) 从 +1 到 -1，保证完整球体（上下半球）
        float nx = (float) (Math.sin(lat) * Math.cos(lon));
        float ny = (float) Math.cos(lat);
        float nz = (float) (Math.sin(lat) * Math.sin(lon));
        float x = (float) (center.x + nx * SIZE);
        float y = (float) (center.y + ny * SIZE);
        float z = (float) (center.z + nz * SIZE);
        bb.addVertex(mat, x, y, z)
          .setColor(nx, ny, nz, 1f)   // Color = 球面法线 → fsh 的 localPos（3D 等离子）
          .setUv(0f, 0f)
          .setUv1(0, 0)
          .setUv2(240, 240)           // 满亮，等离子自发光
          .setNormal(nx, ny, nz);
    }
}
