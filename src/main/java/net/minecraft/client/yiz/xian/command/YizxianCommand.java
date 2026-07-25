package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.BiConsumer;

public final class YizxianCommand {

    private YizxianCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("yizxian")
                .then(Commands.literal("bypass")
                    .executes(YizxianCommand::showBypass)
                    .then(Commands.argument("layer", IntegerArgumentType.integer(3, 4))
                        .executes(YizxianCommand::toggleBypass)
                    )
                )
                .then(Commands.literal("precision")
                    .then(Commands.literal("on")
                        .executes(ctx -> setPrecision(ctx, true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> setPrecision(ctx, false)))
                )
                .then(Commands.literal("attr")
                    .then(Commands.literal("set")
                        .then(Commands.argument("attr", StringArgumentType.word())
                            .then(Commands.argument("value", FloatArgumentType.floatArg(0f))
                                .executes(YizxianCommand::setAttr)
                            )
                        )
                    )
                )
        );
    }

    private static int showBypass(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
            () -> Component.literal(
                "③trueDamage=" + (TerraprismaRenderHandler.useTrueDamage ? "§aON" : "§cOFF")
                + "§r  ④modifyHealth=" + (TerraprismaRenderHandler.useModifyHealth ? "§aON" : "§cOFF")),
            true
        );
        return 1;
    }

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

    private static int setPrecision(CommandContext<CommandSourceStack> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        var inst = sp.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.PRECISION);
        if (inst == null) {
            ctx.getSource().sendFailure(Component.literal("PRECISION 属性未注册到玩家"));
            return 0;
        }
        if (on) {
            inst.setBaseValue(1.0);
        } else {
            inst.setBaseValue(0.0);
        }
        ctx.getSource().sendSuccess(
            () -> Component.literal("精准 PRECISION → " + (on ? "§aON" : "§cOFF")),
            true);
        return 1;
    }

    // ── /yizxian attr（仅库原生属性，terraria EffectTag 回退已删除）─────────

    /**
     * 库原生属性映射 —— 走库 {@code ItemAttributeHandler.addXxx}，写入原版
     * {@code ATTRIBUTE_MODIFIERS} 组件（{@code EquipmentSlotGroup.ANY}），主手/副手/4盔甲槽
     * 全部自动显示 tooltip + 自动生效。
     */
    private static final Map<String, BiConsumer<ItemStack, Double>> LIB_ATTR_BY_NAME = Map.ofEntries(
        java.util.Map.entry("crit_rate",           ItemAttributeHandler::addCritRate),
        java.util.Map.entry("crit_damage",         ItemAttributeHandler::addCritDamage),
        java.util.Map.entry("life_steal",          ItemAttributeHandler::addLifeSteal),
        java.util.Map.entry("splash_radius",       ItemAttributeHandler::addSplashRadius),
        java.util.Map.entry("splash_damage",       ItemAttributeHandler::addSplashDamage),
        java.util.Map.entry("splash_falloff",      ItemAttributeHandler::addSplashFalloff),
        java.util.Map.entry("huixin",              ItemAttributeHandler::addHuixin),
        java.util.Map.entry("kegong",              ItemAttributeHandler::addKegong),
        java.util.Map.entry("damage_block",        ItemAttributeHandler::addDamageBlock),
        java.util.Map.entry("damage_reduction",    ItemAttributeHandler::addDamageReduction),
        java.util.Map.entry("generic_damage",      ItemAttributeHandler::addGenericDamage),
        java.util.Map.entry("on_hurt",             ItemAttributeHandler::addOnHurt),
        java.util.Map.entry("counter_rate",        ItemAttributeHandler::addCounterRate),
        java.util.Map.entry("counter_value",       ItemAttributeHandler::addCounterValue),
        java.util.Map.entry("counter_count",       ItemAttributeHandler::addCounterCount),
        java.util.Map.entry("undying",             ItemAttributeHandler::addUndying),
        java.util.Map.entry("projectile_reflection", ItemAttributeHandler::addProjectileReflection),
        java.util.Map.entry("no_collision",        ItemAttributeHandler::addNoCollision),
        java.util.Map.entry("knockback_immunity",  ItemAttributeHandler::addKnockbackImmunity),
        java.util.Map.entry("projectile_immunity", ItemAttributeHandler::addProjectileImmunity)
    );

    /** /yizxian attr set <attr> <value> — 给主手物品设库原生属性。 */
    private static int setAttr(CommandContext<CommandSourceStack> ctx) {
        String attrName = StringArgumentType.getString(ctx, "attr");
        float value = FloatArgumentType.getFloat(ctx, "value");
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("这条指令只能玩家用"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("手上没物品"));
            return 0;
        }

        BiConsumer<ItemStack, Double> libSetter = LIB_ATTR_BY_NAME.get(attrName);
        if (libSetter == null) {
            source.sendFailure(Component.literal("未知属性: " + attrName
                + "（仅支持库原生属性，terraria 跳跃属性已随饰品槽系统删除）"));
            return 0;
        }
        libSetter.accept(held, (double) value);
        player.getInventory().setChanged();
        source.sendSuccess(
            () -> Component.literal("§a" + attrName + " = " + value + "  已写入 " + held.getDisplayName().getString()
                + " §7(主手/副手/盔甲自动生效)"),
            true
        );
        return 1;
    }
}
