package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer.PanelInfo;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;
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
}
