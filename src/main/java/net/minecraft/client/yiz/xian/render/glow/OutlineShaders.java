package net.minecraft.client.yiz.xian.render.glow;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Vector2f;

import java.io.IOException;

/**
 * 着色器管理器 — 注册 yizxianmod 的 glow_edge 着色器。
 * 由 {@link net.minecraft.client.yiz.xian.YizxianModClient} 订阅 RegisterShadersEvent 加载。
 */
public final class OutlineShaders {

    public static ShaderInstance glowEdge;

    private OutlineShaders() {}

    public static ShaderInstance getGlowEdge() { return glowEdge; }

    public static Vector2f getScreenSize() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Window w = mc.getWindow();
            if (w.getWidth() > 0 && w.getHeight() > 0)
                return new Vector2f(w.getWidth(), w.getHeight());
        } catch (Exception ignored) {}
        return new Vector2f(1920, 1080);
    }

    /**
     * Mod 总线订阅 — 加载 glow_edge 着色器。
     * 由 {@link net.minecraft.client.yiz.xian.YizxianModClient} 在构造函数中调用。
     */
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("yizxianmod", "glow_edge"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
            shader -> glowEdge = shader
        );
    }
}
