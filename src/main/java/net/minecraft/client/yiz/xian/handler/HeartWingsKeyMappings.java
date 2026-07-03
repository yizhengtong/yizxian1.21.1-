package net.minecraft.client.yiz.xian.handler;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 心之翅可配置按键绑定。
 */
public final class HeartWingsKeyMappings {

    public static final String CATEGORY = "key.categories.yizxianmod";

    public static final KeyMapping BOOST = new KeyMapping(
        "key.yizxianmod.heart_wings_boost",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_TAB,
        CATEGORY
    );

    public static final KeyMapping HOVER = new KeyMapping(
        "key.yizxianmod.heart_wings_hover",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(BOOST);
        event.register(HOVER);
    }
}
