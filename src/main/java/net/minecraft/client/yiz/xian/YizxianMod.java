package net.minecraft.client.yiz.xian;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(YizxianMod.MODID)
public class YizxianMod {
    public static final String MODID = "yizxianmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YizxianMod(IEventBus modEventBus) {
        LOGGER.info("Yiz Xian Mod initializing...");

        // ---- yiz-qzk integration ----
        // Register effects, damage hooks, health modifiers, etc. here via YizModQZKAPI
    }
}
