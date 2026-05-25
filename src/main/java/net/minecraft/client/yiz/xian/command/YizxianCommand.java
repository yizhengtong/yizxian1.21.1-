package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.RealmStage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /yizxian jj 1-4 — 测试用，直接设置玩家境界。
 *
 * 1 = 筑命, 2 = 谌我, 3 = 揖别, 4 = 证我
 */
public final class YizxianCommand {

    private YizxianCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("yizxian")
                .then(Commands.literal("jj")
                    .then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
                        .executes(YizxianCommand::setRealmStage)
                    )
                )
        );
    }

    private static int setRealmStage(CommandContext<CommandSourceStack> ctx) {
        int index = IntegerArgumentType.getInteger(ctx, "stage");
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("这条指令只能玩家用"));
            return 0;
        }

        List<RealmStage> stages = RealmProgressionAPI.getAllStages();
        if (stages.size() < 4) {
            source.sendFailure(Component.literal("境界还没注册全，先确认 RealmStages.register() 调用没"));
            return 0;
        }

        // index 1→排序0（筑命），2→1（谌我），3→2（揖别），4→3（证我）
        RealmStage target = stages.get(index - 1);
        ResourceLocation id = target.id();

        boolean ok = RealmProgressionAPI.breakthrough(player, id);
        if (ok) {
            source.sendSuccess(
                () -> Component.literal("境界已设为 " + target.displayName() + "（" + id + "）"),
                true
            );
        } else {
            source.sendFailure(Component.literal("设置失败，可能已经是 " + target.displayName()));
        }
        return 1;
    }
}
