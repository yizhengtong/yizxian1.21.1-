package net.minecraft.client.yiz.xian.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.xian.render.glow.GlowEdgeBakedModel;
import net.minecraft.client.yiz.xian.render.glow.OutlineRenderType;
import net.minecraft.client.yiz.xian.render.glow.OutlineShaders;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管 {@link GlowEdgeBakedModel} 的渲染。
 * <p>使用 glow_edge 着色器（Perlin 噪声星系 / 彩虹流光 / 折射），
 * 颜色由 Item_5005.png 提取的 35 色调色板驱动，平滑插值无闪烁。</p>
 *
 * <ul>
 *   <li><b>非星空物品</b>（未通过 ShaderManager 谓词）：仅画着色描边，不 cancel</li>
 *   <li><b>星空物品</b>（通过谓词）：cancel 原版，完整管线（描边 → 原版 → Cosmic）</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlowMixin {

    @Shadow
    private void renderModelLists(BakedModel model, ItemStack stack, int packedLight,
                                   int packedOverlay, PoseStack poseStack,
                                   VertexConsumer vertexConsumer) {}

    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "ZLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void yizxian$renderGlowEdge(
            ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack ps, MultiBufferSource source, int light, int overlay,
            BakedModel model,
            CallbackInfo ci) {

        if (!(model instanceof GlowEdgeBakedModel g)) return;

        boolean isGui = (context == ItemDisplayContext.GUI);
        boolean isStar = ShaderManager.hasItemEffect(stack);

        // ── 设置 glow_edge 着色器 uniform ──
        ShaderInstance shader = OutlineShaders.getGlowEdge();
        if (shader != null) {
            int uType = g.getGlowType();
            // Lv5 传说(uType=5) → 动画色板; 其余 → 模型静态色
            Vector4f c = (uType == 5) ? paletteColor() : g.getEdgeColor();
            shader.safeGetUniform("uColor").set(c.x, c.y, c.z, c.w);
            shader.safeGetUniform("uType").set(uType);
            shader.safeGetUniform("time").set(
                    (System.currentTimeMillis() % 60000L) / 1000.0F);
            var ss = OutlineShaders.getScreenSize();
            shader.safeGetUniform("screenSize").set(ss.x, ss.y);
        }

        // ── 共用：渲染 glow_edge 着色描边 ──
        renderGlowEdge(stack, context, leftHand, ps, source, light, overlay, g, isGui, shader);

        if (isStar) {
            // ═══ 星空物品：cancel，手动渲染原版 + Cosmic ═══
            ci.cancel();
            renderOriginalAndCosmic(stack, context, leftHand, ps, source, light, overlay, g, isGui);
        }
        // 非星空：不 cancel → 原版 ItemRenderer 继续渲染原始模型盖在描边上
    }

    // ═══════════════════════════════════════════════════════════════=
    //  描边渲染 — glow_edge 着色器
    // ═══════════════════════════════════════════════════════════════=

    private void renderGlowEdge(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                 PoseStack ps, MultiBufferSource source, int light, int overlay,
                                 GlowEdgeBakedModel g, boolean isGui, ShaderInstance shader) {
        if (shader == null) return;

        ps.pushPose();

        BakedModel resolved = ClientHooks.handleCameraTransforms(ps, g, context, leftHand);
        ps.translate(-0.5F, -0.5F, -0.5F);
        resolved = resolved.getOverrides().resolve(resolved, stack, null, null, 0);
        if (resolved == null) resolved = g;

        if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch();

        RenderSystem.assertOnRenderThread();

        Vector4f baseColor = g.getEdgeColor();
        float baseOffset = g.getOffset(isGui);
        Vector3f[] directions = GlowEdgeBakedModel.getDirections(isGui);
        int uType = g.getGlowType();

        VertexConsumer consumer = source.getBuffer(OutlineRenderType.GLOW_EDGE);

        for (int di = 0; di < directions.length; di++) {
            Vector3f dir = directions[di];
            // 非传说 → 均匀描边；传说 → 保持原色
            Vector4f color = (uType == 5) ? baseColor : evenGlow(baseColor);

            ps.pushPose();
            ps.translate(dir.x() * baseOffset, dir.y() * baseOffset, dir.z() * baseOffset);
            for (BakedModel pass : resolved.getRenderPasses(stack, true)) {
                for (BakedQuad quad : pass.getQuads(
                        (BlockState) null, (Direction) null,
                        RandomSource.create(), ModelData.EMPTY, null)) {
                    if (isGui || shouldRenderQuad(quad, dir)) {
                        consumer.putBulkData(
                                ps.last(), quad,
                                color.x, color.y, color.z, color.w,
                                15728880, overlay, true); // 满亮 = 无视环境光
                    }
                }
            }
            ps.popPose();
        }

        if (source instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(OutlineRenderType.GLOW_EDGE);
        }

        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════════=
    //  星空物品：原版模型 + Cosmic
    // ═══════════════════════════════════════════════════════════════=

    private void renderOriginalAndCosmic(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                          PoseStack ps, MultiBufferSource source, int light, int overlay,
                                          GlowEdgeBakedModel g, boolean isGui) {
        ps.pushPose();

        BakedModel resolved = ClientHooks.handleCameraTransforms(ps, g, context, leftHand);
        ps.translate(-0.5F, -0.5F, -0.5F);
        resolved = resolved.getOverrides().resolve(resolved, stack, null, null, 0);
        if (resolved == null) resolved = g;

        // ═══ 渲染原始模型 ═══
        for (BakedModel pass : resolved.getRenderPasses(stack, true)) {
            for (RenderType rt : pass.getRenderTypes(stack, true)) {
                this.renderModelLists(pass, stack, light, overlay, ps, source.getBuffer(rt));
            }
        }

        // ═══ 渲染 Cosmic 星空效果 ═══
        ShaderInstance shader = ShaderManager.getActiveItemShader();
        if (shader != null) {
            if (source instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
            var uTime = shader.getUniform("iTime");
            if (uTime != null)
                uTime.set((float) (System.currentTimeMillis() % 100000L) / 1000f);
            ShaderManager.applyCosmicUVs(shader);

            RenderType starRt = isGui ? ShaderManager.getItemGuiRenderType()
                    : context.firstPerson() ? ShaderManager.getItemDirectRenderType()
                    : ShaderManager.getItemEntityRenderType();

            for (BakedModel pass : resolved.getRenderPasses(stack, true)) {
                this.renderModelLists(pass, stack, light, overlay, ps, source.getBuffer(starRt));
            }

            if (source instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(starRt);
            }
        }

        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════════=
    //  色板 — 从 Item_5005.png 提取的 35 个不重复 RGBA
    //  每 150ms 步进一色，帧间 lerp 平滑过渡（无闪烁）
    // ═══════════════════════════════════════════════════════════════=

    private static final Vector4f[] PALETTE = {
        v( 69,136,103), v(117,167,209), v(133,115,205), v(168,208,193),
        v(183,130,211), v(194,148,218), v(194,221,237), v(204,198,139),
        v(207,194,237), v(212,241,234), v(215,210,161), v(218,181,148),
        v(221,239,247), v(225,251,246), v(226,197,168), v(228,245,250),
        v(230,219,246), v(237,226,249), v(238,252,253), v(239,221,248),
        v(243,228,250), v(246,235,253), v(248,236,253), v(248,245,227),
        v(250,243,228), v(253,249,236), v(253,250,236), v(255,252,240),
    };

    private static Vector4f v(int r, int g, int b) {
        return new Vector4f(r / 255f, g / 255f, b / 255f, 1f);
    }

    /** 调色板当前色（帧间平滑插值，无闪烁） */
    private static Vector4f paletteColor() {
        int len = PALETTE.length;
        long ms = System.currentTimeMillis();
        int idx = (int) ((ms / 500) % len);
        int nxt = (idx + 1) % len;
        float frac = (ms % 500) / 500f;
        return lerp(PALETTE[idx], PALETTE[nxt], frac);
    }

    private static Vector4f lerp(Vector4f a, Vector4f b, float t) {
        float st = t * t * (3f - 2f * t);  // smoothstep
        return new Vector4f(
            a.x + (b.x - a.x) * st,
            a.y + (b.y - a.y) * st,
            a.z + (b.z - a.z) * st,
            a.w + (b.w - a.w) * st
        );
    }

    // ═══════════════════════════════════════════════════════════════=
    //  均匀描边 — 8 方向同色同粗，平滑环绕物品
    // ═══════════════════════════════════════════════════════════════=

    private static Vector4f evenGlow(Vector4f base) { return base; }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    // ═══════════════════════════════════════════════════════════════=
    //  工具
    // ═══════════════════════════════════════════════════════════════=

    private static boolean shouldRenderQuad(BakedQuad quad, Vector3f dir) {
        var normal = quad.getDirection();
        if (normal == null) return true;
        return dir.x() * normal.getStepX() + dir.y() * normal.getStepY() + dir.z() * normal.getStepZ() > 0;
    }
}
