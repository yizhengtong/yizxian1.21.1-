package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer.PanelInfo;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimLoader;
import net.minecraft.client.yiz.xian.render.AnimationPreviewRenderer;
import net.minecraft.client.yiz.xian.render.TerraprismaConfigScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

/**
 * 客户端命令 — /yizxian panel ...
 *
 * <p>纯客户端命令（{@link RegisterClientCommandsEvent}），管理本地 DLL 捕获 + 渲染。</p>
 */
public final class YizxianClientCommand {

    private YizxianClientCommand() {}

    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("yizxian")
                .then(Commands.literal("panel")
                    .then(Commands.literal("open")
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                            .executes(YizxianClientCommand::openPanel)))
                    .then(Commands.literal("close")
                        .executes(YizxianClientCommand::closeAll)
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                            .executes(YizxianClientCommand::closeOne)))
                    .then(Commands.literal("list")
                        .executes(YizxianClientCommand::listWindows))
                    .then(Commands.literal("status")
                        .executes(YizxianClientCommand::status))
                    .then(Commands.literal("fix")
                        .executes(YizxianClientCommand::toggleFix))
                )
                .then(Commands.literal("terra")
                    .then(Commands.literal("gui")
                        .executes(YizxianClientCommand::openTerraGui))
                )
                .then(Commands.literal("outline")
                    .then(Commands.argument("preset", IntegerArgumentType.integer(0, 5))
                        .executes(YizxianClientCommand::setOutline))
                )
                .then(Commands.literal("animpreview")
                    .then(Commands.argument("anim", IntegerArgumentType.integer(0, 3))
                        .executes(YizxianClientCommand::startAnimPreview))
                    .then(Commands.literal("stop")
                        .executes(YizxianClientCommand::stopAnimPreview)))
                .then(Commands.literal("pivot")
                    .then(Commands.argument("x", FloatArgumentType.floatArg(-2f, 2f))
                        .then(Commands.argument("y", FloatArgumentType.floatArg(-2f, 2f))
                            .then(Commands.argument("z", FloatArgumentType.floatArg(-2f, 2f))
                                .executes(YizxianClientCommand::setPivot)))))
        );
    }

    private static int openPanel(CommandContext<CommandSourceStack> ctx) {
        String title = StringArgumentType.getString(ctx, "title");
        CommandSourceStack src = ctx.getSource();
        if (!WindowCaptureManager.isAvailable()) {
            src.sendFailure(Component.literal("WindowCapture.dll 未加载，面板不可用"));
            return 0;
        }
        int id = HandheldPanelRenderer.openByTitle(title);
        if (id > 0) {
            src.sendSuccess(() -> Component.literal("已打开面板 #" + id + "（FOLLOW）"), false);
            return 1;
        }
        src.sendFailure(Component.literal("未找到包含 \"" + title + "\" 的窗口，或初始化失败"));
        return 0;
    }

    private static int closeOne(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        boolean ok = HandheldPanelRenderer.close(id);
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.literal("已关闭面板 #" + id), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("面板 #" + id + " 不存在"));
        return 0;
    }

    private static int closeAll(CommandContext<CommandSourceStack> ctx) {
        int n = HandheldPanelRenderer.closeAll();
        if (n == 0) {
            ctx.getSource().sendFailure(Component.literal("当前没有打开的面板"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("已关闭 " + n + " 个面板"), false);
        return 1;
    }

    private static int listWindows(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!WindowCaptureManager.isAvailable()) {
            src.sendFailure(Component.literal("WindowCapture.dll 未加载"));
            return 0;
        }
        String[] raw = WindowCaptureManager.enumWindows();
        if (raw == null || raw.length == 0) {
            src.sendSuccess(() -> Component.literal("没有可见窗口"), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("可用窗口（" + raw.length + "）："), false);
        int count = Math.min(raw.length, 30);
        for (int i = 0; i < count; i++) {
            String s = raw[i];
            int sep = s.indexOf(':');
            String title = sep > 0 ? s.substring(sep + 1) : s;
            final int idx = i;
            src.sendSuccess(() -> Component.literal("  [" + idx + "] " + title), false);
        }
        if (raw.length > count) {
            int rest = raw.length - count;
            src.sendSuccess(() -> Component.literal("  ... 还有 " + rest + " 个未显示"), false);
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<PanelInfo> infos = HandheldPanelRenderer.listPanels();
        if (infos.isEmpty()) {
            src.sendSuccess(() -> Component.literal("无活跃面板"), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal("活跃面板（" + infos.size() + "）："), false);
        for (PanelInfo info : infos) {
            src.sendSuccess(() -> Component.literal(
                "  #" + info.id + " [" + info.mode + "] " + info.width + "x" + info.height
                + " — " + info.title), false);
        }
        return 1;
    }

    /** /yizxian terra gui — 打开泰拉棱镜实时调参界面 */
    private static int openTerraGui(CommandContext<CommandSourceStack> ctx) {
        Minecraft.getInstance().execute(() ->
            Minecraft.getInstance().setScreen(new TerraprismaConfigScreen()));
        return 1;
    }

    /** /yizxian outline <0-5> — 设置所有物品描边壳颜色 */
    private static int setOutline(CommandContext<CommandSourceStack> ctx) {
        int p = IntegerArgumentType.getInteger(ctx, "preset");
        String[] names = {"白", "彩虹", "红", "紫", "蓝", "绿"};
        System.setProperty("yizxian.outline.preset", String.valueOf(p));
        ctx.getSource().sendSuccess(
            () -> Component.literal("描边壳 → " + p + "（" + names[p] + "）"), false);
        return 1;
    }

    /** 命令行触发跟随↔固定切换（除了 Ctrl+C 快捷键外的另一种方式） */
    private static int toggleFix(CommandContext<CommandSourceStack> ctx) {
        int id = HandheldPanelRenderer.toggleFixCurrent();
        if (id < 0) {
            ctx.getSource().sendFailure(Component.literal("没有可切换的面板"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("面板 #" + id + " 已切换模式"), false);
        return 1;
    }

    /** /yizxian animpreview <0|1|2|3> — 在面前 2 格处循环播放动画预览 */
    private static int startAnimPreview(CommandContext<CommandSourceStack> ctx) {
        int idx = IntegerArgumentType.getInteger(ctx, "anim");
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;

        // 查找手中有 ILeftHandRender 武器的物品，否则用快捷栏第一个 TerraBlade
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof net.minecraft.client.yiz.xian.api.ILeftHandRender)) {
            for (int i = 0; i < 9; i++) {
                var s = player.getInventory().getItem(i);
                if (s.getItem() instanceof net.minecraft.client.yiz.xian.api.ILeftHandRender) {
                    stack = s;
                    break;
                }
            }
        }

        var pos = player.getEyePosition().add(player.getLookAngle().scale(2.0));
        AnimationPreviewRenderer.start(idx, stack, pos);

        String[] names = {"左平砍","右平砍","左下→左上","左上→右下"};
        ctx.getSource().sendSuccess(
            () -> Component.literal("动画预览: §e" + names[idx] + " §7(绕圈观察 /stop 停止)"), false);
        return 1;
    }

    /** /yizxian animpreview stop */
    private static int stopAnimPreview(CommandContext<CommandSourceStack> ctx) {
        AnimationPreviewRenderer.stop();
        ctx.getSource().sendSuccess(
            () -> Component.literal("动画预览已停止"), false);
        return 1;
    }

    /** /yizxian pivot <x> <y> <z> — 设置旋转枢轴（方块单位，相对模型中心） */
    private static int setPivot(CommandContext<CommandSourceStack> ctx) {
        float x = FloatArgumentType.getFloat(ctx, "x");
        float y = FloatArgumentType.getFloat(ctx, "y");
        float z = FloatArgumentType.getFloat(ctx, "z");
        BlockbenchAnimLoader.pivotX = x;
        BlockbenchAnimLoader.pivotY = y;
        BlockbenchAnimLoader.pivotZ = z;
        ctx.getSource().sendSuccess(
            () -> Component.literal(String.format(
                "枢轴 → (%.2f, %.2f, %.2f) §7绕此点旋转", x, y, z)), false);
        return 1;
    }

}
