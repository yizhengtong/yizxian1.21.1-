package net.minecraft.client.yiz.xian.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 容器 MenuType 注册中心。
 * 沿用项目命令式风格：在 YizxianMod 构造器里调用 register(bus)。
 */
public final class YizxianMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, "yizxianmod");

    public static final Supplier<MenuType<LightCompassMenu>> LIGHT_COMPASS_MENU =
        MENUS.register("light_compass", () -> new MenuType<>(
            (net.neoforged.neoforge.network.IContainerFactory<LightCompassMenu>)
                (containerId, playerInventory, data) -> new LightCompassMenu(containerId, playerInventory),
            net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS
        ));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }

    private YizxianMenus() {}
}
