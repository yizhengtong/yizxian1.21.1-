package net.minecraft.client.yiz.xian;

import net.minecraft.client.yiz.api.TargetFrameManager;
import net.minecraft.client.yiz.xian.command.YizxianClientCommand;
import net.minecraft.client.yiz.xian.effect.CriticalStrikeProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = YizxianMod.MODID, dist = Dist.CLIENT)
public class YizxianModClient {
    public YizxianModClient() {
        // 会心一击锁定框 — 高优先级，覆盖母效果
        TargetFrameManager.register(new CriticalStrikeProvider());

        // 客户端命令：/yizxian panel ...
        NeoForge.EVENT_BUS.addListener(YizxianClientCommand::onRegisterClientCommands);
    }
}
