package net.minecraft.client.yiz.xian.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.client.yiz.xian.api.terraria.JumpAttributes;
import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
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
                .then(Commands.literal("attr")
                    .then(Commands.literal("set")
                        .then(Commands.argument("attr", StringArgumentType.word())
                            .then(Commands.argument("value", FloatArgumentType.floatArg(0f))
                                .executes(YizxianCommand::setJumpAttr)
                            )
                        )
                    )
                    .then(Commands.literal("clear")
                        .executes(YizxianCommand::clearJumpAttr)
                    )
                    .then(Commands.literal("show")
                        .executes(YizxianCommand::showJumpAttr)
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

    // ── /yizxian attr ──────────────────────────────────────────────

    private static final java.util.Map<String, EffectTag> ATTR_BY_NAME = java.util.Map.ofEntries(
        java.util.Map.entry("jump_count", EffectTag.JUMP_COUNT),
        java.util.Map.entry("jump_height", EffectTag.JUMP_HEIGHT),
        java.util.Map.entry("fall_safe", EffectTag.FALL_SAFE),
        java.util.Map.entry("fall_reduce", EffectTag.FALL_REDUCE),
        java.util.Map.entry("move_speed", EffectTag.MOVE_SPEED),
        java.util.Map.entry("max_run_speed", EffectTag.MAX_RUN_SPEED),
        java.util.Map.entry("jump_strength", EffectTag.JUMP_STRENGTH),
        java.util.Map.entry("air_speed", EffectTag.AIR_SPEED),
        java.util.Map.entry("armor", EffectTag.ARMOR),
        java.util.Map.entry("damage_reduction", EffectTag.DAMAGE_REDUCTION),
        java.util.Map.entry("dodge_chance", EffectTag.DODGE_CHANCE),
        java.util.Map.entry("invincibility_mult", EffectTag.INVINCIBILITY_MULT),
        java.util.Map.entry("lava_immune_time", EffectTag.LAVA_IMMUNE_TIME),
        java.util.Map.entry("lava_damage_reduction", EffectTag.LAVA_DAMAGE_REDUCTION),
        java.util.Map.entry("life_regen_rate", EffectTag.LIFE_REGEN_RATE),
        java.util.Map.entry("life_regen_pct", EffectTag.LIFE_REGEN_PCT),
        java.util.Map.entry("generic_damage", EffectTag.GENERIC_DAMAGE),
        java.util.Map.entry("melee_damage", EffectTag.MELEE_DAMAGE),
        java.util.Map.entry("ranged_damage", EffectTag.RANGED_DAMAGE),
        java.util.Map.entry("magic_damage", EffectTag.MAGIC_DAMAGE),
        java.util.Map.entry("summon_damage", EffectTag.SUMMON_DAMAGE),
        java.util.Map.entry("crit_rate", EffectTag.CRIT_RATE),
        java.util.Map.entry("armor_penetration", EffectTag.ARMOR_PENETRATION),
        java.util.Map.entry("attack_speed", EffectTag.ATTACK_SPEED),
        java.util.Map.entry("attack_knockback", EffectTag.ATTACK_KNOCKBACK),
        java.util.Map.entry("attack_range", EffectTag.ATTACK_RANGE),
        java.util.Map.entry("flight_time", EffectTag.FLIGHT_TIME),
        java.util.Map.entry("jump_speed", EffectTag.JUMP_SPEED),
        java.util.Map.entry("max_fall_safe", EffectTag.MAX_FALL_SAFE),
        java.util.Map.entry("luck", EffectTag.LUCK),
        java.util.Map.entry("max_minions", EffectTag.MAX_MINIONS),
        java.util.Map.entry("max_sentries", EffectTag.MAX_SENTRIES),
        java.util.Map.entry("water_breath_time", EffectTag.WATER_BREATH_TIME),
        java.util.Map.entry("arrow_damage", EffectTag.ARROW_DAMAGE),
        java.util.Map.entry("arrow_speed", EffectTag.ARROW_SPEED),
        java.util.Map.entry("arrow_save_chance", EffectTag.ARROW_SAVE_CHANCE)
    );

    /**
     * 库原生属性映射 —— 这些属性走库 {@code ItemAttributeHandler.addXxx}，写入原版
     * {@code ATTRIBUTE_MODIFIERS} 组件（{@code EquipmentSlotGroup.ANY}），在主手/副手/4盔甲槽
     * 全部自动显示 tooltip + 自动生效（防御力同款体验）。
     * <p>{@code setJumpAttr} 优先匹配此表；命中则走原生属性路径，未命中再走 {@link #ATTR_BY_NAME} 的 EffectTag 路径。</p>
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
        java.util.Map.entry("on_attack",           ItemAttributeHandler::addOnAttack),
        java.util.Map.entry("on_tick",             ItemAttributeHandler::addOnTick),
        java.util.Map.entry("counter_rate",        ItemAttributeHandler::addCounterRate),
        java.util.Map.entry("counter_value",       ItemAttributeHandler::addCounterValue),
        java.util.Map.entry("counter_count",       ItemAttributeHandler::addCounterCount),
        java.util.Map.entry("undying",             ItemAttributeHandler::addUndying),
        java.util.Map.entry("projectile_reflection", ItemAttributeHandler::addProjectileReflection),
        java.util.Map.entry("no_collision",        ItemAttributeHandler::addNoCollision),
        java.util.Map.entry("knockback_immunity",  ItemAttributeHandler::addKnockbackImmunity),
        java.util.Map.entry("projectile_immunity", ItemAttributeHandler::addProjectileImmunity)
    );

    /** /yizxian attr set <attr> <value> — 给主手物品设属性。 */
    private static int setJumpAttr(CommandContext<CommandSourceStack> ctx) {
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

        // 优先：库原生属性 → 写原版 ATTRIBUTE_MODIFIERS（ANY 槽位，主手/副手/盔甲自动显示+生效）
        BiConsumer<ItemStack, Double> libSetter = LIB_ATTR_BY_NAME.get(attrName);
        if (libSetter != null) {
            libSetter.accept(held, (double) value);
            player.getInventory().setChanged();
            source.sendSuccess(
                () -> Component.literal("§a[库属性] " + attrName + " = " + value + "  已写入 " + held.getDisplayName().getString()
                    + " §7(主手/副手/盔甲自动生效)"),
                true
            );
            return 1;
        }

        // 回退：EffectTag 属性 → 旧 NBT 路径（跳跃/移动等下游属性）
        EffectTag tag = ATTR_BY_NAME.get(attrName);
        if (tag == null) {
            source.sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }
        JumpAttributes.setOne(held, tag, value);
        player.getInventory().setChanged();
        source.sendSuccess(
            () -> Component.literal("§a" + attrName + " = " + value + "  已写入 " + held.getDisplayName().getString()),
            true
        );
        return 1;
    }

    /** /yizxian attr clear — 清除主手物品全部跳跃属性。 */
    private static int clearJumpAttr(CommandContext<CommandSourceStack> ctx) {
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
        JumpAttributes.remove(held);
        player.getInventory().setChanged();
        source.sendSuccess(() -> Component.literal("§e已清除跳跃属性"), true);
        return 1;
    }

    /** /yizxian attr show — 显示主手物品当前跳跃属性。 */
    private static int showJumpAttr(CommandContext<CommandSourceStack> ctx) {
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
        if (!JumpAttributes.hasAny(held)) {
            source.sendSuccess(() -> Component.literal("§7无跳跃属性"), false);
            return 1;
        }
        java.util.Map<EffectTag, Float> attrs = JumpAttributes.get(held);
        source.sendSuccess(
            () -> Component.literal("§b" + held.getDisplayName().getString() + " 跳跃属性："
                + " count=" + attrs.getOrDefault(EffectTag.JUMP_COUNT, 0f)
                + " height=" + attrs.getOrDefault(EffectTag.JUMP_HEIGHT, 0f)
                + " safe=" + attrs.getOrDefault(EffectTag.FALL_SAFE, 0f)
                + " reduce=" + attrs.getOrDefault(EffectTag.FALL_REDUCE, 0f)),
            false
        );
        return 1;
    }
}
