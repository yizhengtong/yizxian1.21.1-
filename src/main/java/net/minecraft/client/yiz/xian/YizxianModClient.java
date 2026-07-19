package net.minecraft.client.yiz.xian;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.TargetFrameManager;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.command.YizxianClientCommand;
import net.minecraft.client.yiz.xian.effect.LockOnProvider;
import net.minecraft.client.yiz.xian.handler.BoostHandler;
import net.minecraft.client.yiz.xian.handler.HeartWingsKeyMappings;
import net.minecraft.client.yiz.xian.item.MuramasaItem;
import net.minecraft.client.yiz.xian.item.TerraBladeItem;
import net.minecraft.client.yiz.xian.item.TerraprismaScrollItem;
import net.minecraft.client.yiz.xian.render.AnimationPreviewRenderer;
import net.minecraft.client.yiz.xian.render.EnergyWaveRenderer;
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
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.client.yiz.xian.api.terraria.JumpAttributes;
import net.minecraft.client.yiz.xian.hud.BoostHud;
import net.minecraft.client.yiz.hud.HudEditorScreen;
import net.minecraft.client.yiz.xian.hud.HudManager;
import net.minecraft.client.yiz.xian.hud.HudPositionConfig;
import net.minecraft.client.yiz.xian.item.terraria.TerrariaAccessoryItem;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector4f;

import java.util.Map;

@Mod(value = YizxianMod.MODID, dist = Dist.CLIENT)
public class YizxianModClient {
    public YizxianModClient(IEventBus modEventBus) {
        // 加载 Blockbench 动画
        BlockbenchAnimParser.load("/assets/yizxianmod/models/animations/attack.bbmodel");

        // 心之翅可配置按键
        modEventBus.addListener(HeartWingsKeyMappings::register);

        // 锁定系统 — 属性驱动锁定框，高优先级
        TargetFrameManager.register(new LockOnProvider());

        // 属性卷轴交互：由 AttributeScrollScreenMixin 处理

        // 客户端命令：/yizxian panel ...
        NeoForge.EVENT_BUS.addListener(YizxianClientCommand::onRegisterClientCommands);

        // 心之翅推进：客户端按键（服务端恢复/悬停在 YizxianMod 中注册）
        NeoForge.EVENT_BUS.addListener(BoostHandler::onClientTick);

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
        HudManager.register(new BoostHud());
        NeoForge.EVENT_BUS.addListener(HudManager::onRenderGui);
        NeoForge.EVENT_BUS.addListener(YizxianModClient::onHudKeyTick);

        // 物品着色器描边 — 复用前置库星空着色器系统
        ShaderManager.registerItemPredicate(
            stack -> stack.getItem() instanceof TerraprismaScrollItem
                  || stack.getItem() instanceof TerraBladeItem
                  || stack.getItem() instanceof MuramasaItem);

        // ═══ 注册 glow_edge 着色器（含光影兼容保护）═══
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
                if (!key.id().getPath().contains("terraprisma_scroll")
                        && !key.id().getPath().contains("terra_blade")
                        && !key.id().getPath().contains("muramasa")) continue;
                if (model instanceof GlowEdgeBakedModel) continue;

                int level = 5; // 默认传说
                String path = key.id().getPath();
                int us = path.lastIndexOf('_');
                if (us >= 0) {
                    try { level = Integer.parseInt(path.substring(us + 1)); }
                    catch (NumberFormatException ignored) {}
                }

                Vector4f color = StagedItemHelper.glowColorForLevel(level);
                int uType = (color == null) ? 5 : 0;
                if (color == null) color = new Vector4f(1, 1, 1, 0.7f);
                GlowEdgeBakedModel glowModel = new GlowEdgeBakedModel(model, color, uType, 0.002f);
                event.getModels().put(key, glowModel);
            }
        });

        // 注意：不要在这里 setSyncCallback —— 它会覆盖前置库 NetworkHandler 注入的
        // 网络同步回调（PlayerDataAPI.set → 发 SyncPlayerDataPayload 到客户端），
        // 一旦覆盖，服务端所有 PlayerDataAPI 变更都不再同步到客户端（GUI 空、HUD 看不到恢复等）。

        // 任意物品的跳跃属性 tooltip（JUMP_ATTRIBUTES 组件注入的非泰拉饰品物品）
        NeoForge.EVENT_BUS.addListener(YizxianModClient::onItemTooltip);

        // 客户端断开服务器 → 清掉 _c 单例，避免跨重进/换世界携带脏数据。
        // _c 是只读镜像，重进后由服务端 SyncAccessoryPayload 重新填充。
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
                var p = Minecraft.getInstance().player;
                if (p != null) AccessoryContainer.discard(p);
            });
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

    // ── 跳跃属性 tooltip（ItemTooltipEvent）─────────────────────────

    /**
     * 给任意带 {@link JumpAttributes}（JUMP_ATTRIBUTES 组件）的非泰拉饰品物品
     * 追加跳跃属性 tooltip。泰拉饰品走 {@link TerrariaAccessoryItem#appendHoverText}，
     * 不在此处重复渲染。
     */
    private static void onItemTooltip(
            net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.isEmpty()) return;
        // 泰拉饰品已由 TerrariaAccessoryItem.appendHoverText 处理，不重复
        if (stack.getItem() instanceof TerrariaAccessoryItem) return;
        if (!JumpAttributes.hasAny(stack)) return;

        Map<EffectTag, Float> attrs = JumpAttributes.getWithDefaults(stack);
        TerrariaAccessoryItem.appendAttrStats(event.getToolTip(), attrs);
    }
}
