package net.minecraft.client.yiz.xian;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.TargetFrameManager;
import net.minecraft.client.yiz.xian.command.YizxianClientCommand;
import net.minecraft.client.yiz.xian.effect.CriticalStrikeProvider;
import net.minecraft.client.yiz.xian.item.TerraprismaScrollItem;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.client.yiz.util.StagedItemHelper;
import net.minecraft.client.yiz.xian.render.glow.GlowEdgeBakedModel;
import net.minecraft.client.yiz.xian.render.glow.OutlineShaders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector4f;

@Mod(value = YizxianMod.MODID, dist = Dist.CLIENT)
public class YizxianModClient {
    public YizxianModClient(IEventBus modEventBus) {
        // 会心一击锁定框 — 高优先级，覆盖母效果
        TargetFrameManager.register(new CriticalStrikeProvider());

        // 客户端命令：/yizxian panel ...
        NeoForge.EVENT_BUS.addListener(YizxianClientCommand::onRegisterClientCommands);

        // 泰拉棱镜渲染 — 直接在世界中绘制浮游剑
        NeoForge.EVENT_BUS.addListener(TerraprismaRenderHandler::onRenderLevel);

        // 调试 HUD — 屏幕左侧显示每剑阶段 (A~G)
        NeoForge.EVENT_BUS.addListener(TerraprismaRenderHandler::onRenderGui);

        // 泰拉棱镜物品着色器描边 — 复用前置库星空着色器系统
        ShaderManager.registerItemPredicate(
            stack -> stack.getItem() instanceof TerraprismaScrollItem);

        // ═══ 注册 glow_edge 着色器 ═══
        modEventBus.addListener(RegisterShadersEvent.class, event -> {
            try {
                OutlineShaders.onRegisterShaders(event);
            } catch (Exception e) {
                YizxianMod.LOGGER.error("Failed to register glow_edge shader", e);
            }
        });

        // ═══ 模型烘焙修饰 — 分级发光色 ═══
        modEventBus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
            for (var entry : event.getModels().entrySet()) {
                ModelResourceLocation key = entry.getKey();
                BakedModel model = entry.getValue();
                if (!key.id().getPath().contains("terraprisma_scroll")) continue;
                if (model instanceof GlowEdgeBakedModel) continue;

                // 从注册名 _N 后缀提取等级
                int level = 5; // 默认传说
                String path = key.id().getPath();
                int us = path.lastIndexOf('_');
                if (us >= 0) {
                    try { level = Integer.parseInt(path.substring(us + 1)); }
                    catch (NumberFormatException ignored) {}
                }

                Vector4f color = StagedItemHelper.glowColorForLevel(level);
                // Lv5 传说 null → uType=5 galaxyDynamic 动画色板
                int uType = (color == null) ? 5 : 0;
                if (color == null) color = new Vector4f(1, 1, 1, 0.7f);
                GlowEdgeBakedModel glowModel = new GlowEdgeBakedModel(model, color, uType, 0.002f);
                event.getModels().put(key, glowModel);
            }
        });
    }
}