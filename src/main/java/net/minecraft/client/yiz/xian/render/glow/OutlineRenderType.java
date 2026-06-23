package net.minecraft.client.yiz.xian.render.glow;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * glow_edge 描边的 {@link RenderType}。
 * 使用 {@link OutlineShaders#getGlowEdge} 着色器，
 * POSITION_COLOR_TEX_LIGHTMAP 顶点格式（与 putBulkData 兼容）。
 */
public final class OutlineRenderType extends RenderType {

    public static final RenderType GLOW_EDGE;

    private OutlineRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                              int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setupTask, Runnable clearTask) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupTask, clearTask);
    }

    static {
        GLOW_EDGE = RenderType.create(
            "yizxian_glow_edge",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            CompositeState.builder()
                .setShaderState(new ShaderStateShard(OutlineShaders::getGlowEdge))
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .createCompositeState(false)
        );
    }
}
