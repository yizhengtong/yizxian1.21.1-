package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.yiz.api.ShaderEnvironmentAPI;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * 紫昭明光 — 3D 等离子着色器注册 + RenderType。
 * 由 {@link net.minecraft.client.yiz.xian.YizxianModClient} 订阅 RegisterShadersEvent 加载。
 */
public final class ZhaoMingLightShaders {

    public static ShaderInstance plasma;
    private static RenderType plasmaType;

    private ZhaoMingLightShaders() {}

    public static ShaderInstance getPlasma() { return plasma; }
    public static RenderType getPlasmaType() { return plasmaType; }

    /**
     * 注册 zhaoming_plasma 着色器并构建 RenderType。
     * 顶点格式 POSITION_COLOR_TEX_LIGHTMAP（与 ShaderInstance 匹配）。
     */
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ShaderEnvironmentAPI.ensureShaderCompatibility();
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("yizxianmod", "zhaoming_plasma"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
            shader -> {
                plasma = shader;
                plasmaType = RenderType.create(
                    "zhaoming_plasma",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> plasma))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false)
                );
            }
        );
    }
}
