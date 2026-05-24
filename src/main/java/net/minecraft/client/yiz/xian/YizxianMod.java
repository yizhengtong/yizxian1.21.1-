package net.minecraft.client.yiz.xian;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
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
        // Register data keys required by library mixins
        PlayerDataAPI.register("yizxgmod:star_body", Codec.BOOL, false);
        PlayerDataAPI.register("yizxgmod:star_level", Codec.intRange(0, 10), 0);
        // Register effects, damage hooks, health modifiers, etc. here via YizModQZKAPI
    }
}
