package net.minecraft.client.yiz.xian.render.glow;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * 发光描边 BakedModel 包装器。
 * <p>
 * 将原始模型包装后设置 {@code isCustomRenderer() = true}，
 * 由 {@link ItemRendererGlowMixin} 接管渲染，依次绘制：
 * <ol>
 *   <li>8 方向偏移着色描边 quad</li>
 *   <li>原始模型</li>
 *   <li>Cosmic 星空效果（如果配置了 mask 纹理）</li>
 * </ol>
 */
public class GlowEdgeBakedModel implements BakedModel {

    private final BakedModel original;
    private final Vector4f edgeColor;
    private final int glowType;
    private final float edgeWidth;

    /** 自定义 ItemOverrides：override 解析后仍返回 GlowEdgeBakedModel */
    private final ItemOverrides overrideList;

    /** 3D 8 方向 */
    private static final Vector3f[] DIRECTIONS_3D = {
            new Vector3f( 1,  1,  1), new Vector3f(-1,  1,  1),
            new Vector3f( 1, -1,  1), new Vector3f( 1,  1, -1),
            new Vector3f(-1, -1,  1), new Vector3f(-1,  1, -1),
            new Vector3f( 1, -1, -1), new Vector3f(-1, -1, -1)
    };

    /** GUI 4 方向 + 4 对角线 */
    private static final Vector3f[] DIRECTIONS_GUI = {
            new Vector3f( 1,  0, 0), new Vector3f(-1,  0, 0),
            new Vector3f( 0,  1, 0), new Vector3f( 0, -1, 0),
            new Vector3f( 1,  1, 0), new Vector3f(-1,  1, 0),
            new Vector3f( 1, -1, 0), new Vector3f(-1, -1, 0)
    };

    public GlowEdgeBakedModel(BakedModel original, Vector4f edgeColor, int glowType, float edgeWidth) {
        this.original = original;
        this.edgeColor = edgeColor;
        this.glowType = glowType;
        this.edgeWidth = edgeWidth;
        this.overrideList = new ItemOverrides() {
            @Override
            public BakedModel resolve(@NotNull BakedModel model, @NotNull ItemStack stack,
                                      @Nullable net.minecraft.client.multiplayer.ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel resolved = original.getOverrides().resolve(model, stack, level, entity, seed);
                if (resolved == null || resolved == original) return GlowEdgeBakedModel.this;
                return new GlowEdgeBakedModel(resolved, edgeColor, glowType, edgeWidth);
            }
        };
    }

    // ── 访问器 ──
    public BakedModel getOriginal() { return original; }
    public Vector4f getEdgeColor()  { return edgeColor; }
    public int getGlowType()        { return glowType; }
    public float getEdgeWidth()     { return edgeWidth; }

    public static Vector3f[] getDirections(boolean isGui) {
        return isGui ? DIRECTIONS_GUI : DIRECTIONS_3D;
    }

    public float getOffset(boolean isGui) {
        return isGui ? edgeWidth * 4.5f : edgeWidth;
    }

    // ── isCustomRenderer = true → ItemRenderer 跳过标准管线，由 Mixin 接管 ──
    @Override public boolean isCustomRenderer() { return true; }

    // ── BakedModel 接口 —— 全部委托给原始模型 ──

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                              @NotNull RandomSource random) {
        return original.getQuads(state, direction, random);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                              @NotNull RandomSource random, @NotNull ModelData data,
                                              @Nullable RenderType renderType) {
        return original.getQuads(state, direction, random, data, renderType);
    }

    @Override public boolean useAmbientOcclusion()             { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d()                         { return original.isGui3d(); }
    @Override public boolean usesBlockLight()                  { return original.usesBlockLight(); }
    @Override public @NotNull TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public @NotNull ItemTransforms getTransforms()   { return original.getTransforms(); }
    @Override public @NotNull ItemOverrides getOverrides()     { return overrideList; }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return original.getParticleIcon(data);
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state,
                                                       @NotNull RandomSource random,
                                                       @NotNull ModelData data) {
        return original.getRenderTypes(state, random, data);
    }

    @Override
    public @NotNull List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
        return original.getRenderTypes(stack, fabulous);
    }

    @Override
    public @NotNull List<BakedModel> getRenderPasses(@NotNull ItemStack stack, boolean fabulous) {
        return original.getRenderPasses(stack, fabulous);
    }
}
