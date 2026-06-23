package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.RealmStage;
import net.minecraft.client.yiz.xian.item.TalentCoreItem;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

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
                .then(Commands.literal("bypass")
                    .executes(YizxianCommand::showBypass)
                    .then(Commands.argument("layer", IntegerArgumentType.integer(3, 4))
                        .executes(YizxianCommand::toggleBypass)
                    )
                )
                .then(Commands.literal("talent")
                    .then(Commands.argument("effect_id", StringArgumentType.string())
                        .then(Commands.argument("max_level", IntegerArgumentType.integer(1, 100))
                            .executes(YizxianCommand::giveTalent)
                        )
                    )
                )
        );
    }

    /** /yizxian talent <effect_id> <max_level> — 给玩家一个含指定效果的 talent_core */
    private static int giveTalent(CommandContext<CommandSourceStack> ctx) {
        String effectIdStr = StringArgumentType.getString(ctx, "effect_id");
        int maxLevel = IntegerArgumentType.getInteger(ctx, "max_level");
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("这条指令只能玩家用"));
            return 0;
        }

        ResourceLocation effectId = ResourceLocation.parse(effectIdStr);
        ItemStack stack = new ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(ResourceLocation.fromNamespaceAndPath("yizxianmod", "talent_core"))
        );
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("talent_core 物品未注册"));
            return 0;
        }

        TalentCoreItem.setContainedEffect(stack, effectId, maxLevel);
        player.getInventory().add(stack);
        source.sendSuccess(
            () -> Component.literal("已给予 talent_core [%s] 最大等级 %d".formatted(effectIdStr, maxLevel)),
            true
        );
        return 1;
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

    /** /yizxian bypass — 显示当前开关状态 */
    private static int showBypass(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal(
                "③trueDamage=" + (TerraprismaRenderHandler.useTrueDamage ? "§aON" : "§cOFF")
                + "§r  ④modifyHealth=" + (TerraprismaRenderHandler.useModifyHealth ? "§aON" : "§cOFF")),
            true
        );
        return 1;
    }

    /** /yizxian bypass 3|4 — 独立切换指定层 */
    private static int toggleBypass(CommandContext<CommandSourceStack> ctx) {
        int layer = IntegerArgumentType.getInteger(ctx, "layer");
        boolean now;
        if (layer == 3) {
            TerraprismaRenderHandler.useTrueDamage = !TerraprismaRenderHandler.useTrueDamage;
            now = TerraprismaRenderHandler.useTrueDamage;
        } else {
            TerraprismaRenderHandler.useModifyHealth = !TerraprismaRenderHandler.useModifyHealth;
            now = TerraprismaRenderHandler.useModifyHealth;
        }
        String label = layer == 3 ? "③trueDamage" : "④modifyHealth";
        boolean finalNow = now;
        ctx.getSource().sendSuccess(
            () -> Component.literal(label + " → " + (finalNow ? "§aON" : "§cOFF")),
            true
        );
        return 1;
    }
}
