package net.minecraft.client.yiz.xian;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.TargetFrameManager;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.xian.command.YizxianClientCommand;
import net.minecraft.client.yiz.xian.effect.LockOnProvider;
import net.minecraft.client.yiz.xian.item.MuramasaItem;
import net.minecraft.client.yiz.xian.item.TerraBladeItem;
import net.minecraft.client.yiz.xian.item.TerraprismaScrollItem;
import net.minecraft.client.yiz.xian.item.WupinItem;
import net.minecraft.client.yiz.xian.render.AnimationPreviewRenderer;
import net.minecraft.client.yiz.xian.render.EnergyWaveRenderer;
import net.minecraft.client.yiz.xian.render.ZhaoMingLightClientManager;
import net.minecraft.client.yiz.xian.render.ZhaoMingCastHandler;
import net.minecraft.client.yiz.xian.render.ZhaoMingLightShaders;
import net.minecraft.client.yiz.xian.render.ZhaoMingLightWorldRenderer;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.client.yiz.xian.render.glow.GlowEdgeBakedModel;
import net.minecraft.client.yiz.xian.render.glow.OutlineShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.util.StagedItemHelper;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.yiz.hud.HudEditorScreen;
import net.minecraft.client.yiz.xian.hud.HudManager;
import net.minecraft.client.yiz.xian.hud.HudPositionConfig;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector4f;

@Mod(value = YizxianMod.MODID, dist = Dist.CLIENT)
public class YizxianModClient {
    public YizxianModClient(IEventBus modEventBus) {
        // 加载 Blockbench 动画
        BlockbenchAnimParser.load("/assets/yizxianmod/models/animations/attack.bbmodel");

        // 锁定系统 — 属性驱动锁定框，高优先级
        TargetFrameManager.register(new LockOnProvider());

        // 客户端命令：/yizxian panel ...
        NeoForge.EVENT_BUS.addListener(YizxianClientCommand::onRegisterClientCommands);

        // 心之翅推进(BoostHandler/HeartWingsKeyMappings)已删除（阶段3C）

        // 泰拉棱镜渲染 — 直接在世界中绘制浮游剑
        NeoForge.EVENT_BUS.addListener(TerraprismaRenderHandler::onRenderLevel);
        // 剑气能量波渲染
        NeoForge.EVENT_BUS.addListener(EnergyWaveRenderer::onRenderLevel);

        // 动画预览 — /yizxian animpreview 命令循环播放 BB 动画
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                AnimationPreviewRenderer.render(
                    event.getPoseStack(),
                    Minecraft.getInstance().renderBuffers().bufferSource(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });

        // ═══ HUD 系统：DEL+ALT 打开编辑器，RenderGuiEvent 分发 ═══
        HudPositionConfig.load();
        // 昭明法杖发射点偏移配置（/yizxian zhaoming 指令调整）
        net.minecraft.client.yiz.xian.core.ZhaoMingLaunchConfig.load();
        // BoostHud/ExtraJumpHud 已随心之翅/多段跳系统删除（阶段3B/3C）
        NeoForge.EVENT_BUS.addListener(HudManager::onRenderGui);
        NeoForge.EVENT_BUS.addListener(YizxianModClient::onHudKeyTick);

        // 物品着色器描边 — 复用前置库星空着色器系统
        ShaderManager.registerItemPredicate(
            stack -> stack.getItem() instanceof TerraprismaScrollItem
                  || stack.getItem() instanceof TerraBladeItem
                  || stack.getItem() instanceof MuramasaItem
                  || stack.getItem() instanceof WupinItem);

        // ═══ 注册 glow_edge + zhaoming_plasma 着色器（含光影兼容保护）═══
        modEventBus.addListener(RegisterShadersEvent.class, event -> {
            try {
                OutlineShaders.onRegisterShaders(event);
            } catch (Exception e) {
                YizxianMod.LOGGER.error("Failed to register glow_edge shader", e);
            }
            try {
                ZhaoMingLightShaders.onRegisterShaders(event);
            } catch (Exception e) {
                YizxianMod.LOGGER.error("Failed to register zhaoming_plasma shader", e);
            }
        });

        // ═══ 紫昭明光本地模拟 + 服务端校准（ClientTickEvent 驱动）═══
        NeoForge.EVENT_BUS.addListener(ZhaoMingLightClientManager::onClientTick);
        // ═══ 紫昭明光右键连发（检测右键按住，越按越快施法）═══
        NeoForge.EVENT_BUS.addListener(ZhaoMingCastHandler::onClientTick);
        // 客户端进世界时清空本地特效（防跨存档残留）
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                e -> ZhaoMingLightClientManager.getInstance().clear());
        // ═══ 紫昭明光世界渲染（读本地模拟 FX，RenderLevelStageEvent 绘制）═══
        NeoForge.EVENT_BUS.addListener(ZhaoMingLightWorldRenderer::onRenderLevelStage);

        // ═══ 全首者 Boss 渲染注册 ═══
        modEventBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class, e ->
            e.registerEntityRenderer(
                net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.QUANSHOUZHE.get(),
                net.minecraft.client.yiz.xian.client.renderer.QuanshouzheRenderer::new));
        modEventBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class, e ->
            e.registerLayerDefinition(net.minecraft.client.yiz.xian.client.renderer.QuanshouzheRenderer.LAYER,
                net.minecraft.client.yiz.xian.client.model.QuanshouzheModel::createBodyLayer));

        // ═══ 模型烘焙修饰 — 分级发光色 ═══
        modEventBus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
            for (var entry : event.getModels().entrySet()) {
                ModelResourceLocation key = entry.getKey();
                BakedModel model = entry.getValue();
                if (!key.id().getPath().contains("terraprisma_scroll")
                        && !key.id().getPath().contains("terra_blade")
                        && !key.id().getPath().contains("muramasa")
                        && !key.id().getPath().contains("wupin")) continue;
                if (model instanceof GlowEdgeBakedModel) continue;

                int level = 5; // 默认传说
                String path = key.id().getPath();
                if (path.contains("wupin")) {
                    level = 4; // 物品 → 史诗
                } else {
                    int us = path.lastIndexOf('_');
                    if (us >= 0) {
                        try { level = Integer.parseInt(path.substring(us + 1)); }
                        catch (NumberFormatException ignored) {}
                    }
                }

                Vector4f color = StagedItemHelper.glowColorForLevel(level);
                int uType = (color == null) ? 5 : 0;
                if (color == null) color = new Vector4f(1, 1, 1, 0.7f);
                GlowEdgeBakedModel glowModel = new GlowEdgeBakedModel(model, color, uType, 0.002f);
                event.getModels().put(key, glowModel);
            }
        });

        // ═══ 光明指南针 GUI：Menu → Screen 绑定 ═══
        modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, event -> {
            event.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.LIGHT_COMPASS_MENU.get(),
                net.minecraft.client.yiz.xian.client.screen.LightCompassScreen::new
            );
            event.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.ENTITY_ATTRIBUTE_EDIT_MENU.get(),
                net.minecraft.client.yiz.xian.client.screen.EntityAttributeEditScreen::new
            );
        });

        // 注意：不要在这里 setSyncCallback —— 它会覆盖前置库 NetworkHandler 注入的
        // 网络同步回调（PlayerDataAPI.set → 发 SyncPlayerDataPayload 到客户端），
        // 一旦覆盖，服务端所有 PlayerDataAPI 变更都不再同步到客户端（GUI 空、HUD 看不到恢复等）。

        // 跳跃属性 tooltip + 饰品槽登出清理已随 terraria 子系统删除（阶段3D）
    }

    // ── HUD 编辑器：DEL+ALT 边沿触发打开 ──

    private static boolean delAltWasDown = false;

    private static void onHudKeyTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long window = mc.getWindow().getWindow();
        boolean alt = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                   || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean del = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DELETE);
        boolean both = alt && del;
        if (both && !delAltWasDown && mc.screen == null) {
            mc.setScreen(new HudEditorScreen());
        }
        delAltWasDown = both;
    }

    // onItemTooltip(跳跃属性tooltip)已随 terraria 子系统删除（阶段3D）
}
