package net.minecraft.client.yiz.xian.skill;

import net.minecraft.client.yiz.api.ISkillItem;
import net.minecraft.client.yiz.api.SkillCastMode;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 奔雷疾 — 向前突进，获得一次免疫掉落伤害。
 */
public class BenLeiJiItem extends Item implements ISkillItem {

    public BenLeiJiItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers()));
    }

    private static ItemAttributeModifiers buildModifiers() {
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.COOLDOWN_VALUE, mod("blj_cv", 80), EquipmentSlotGroup.ANY)
            .add(YizAttributes.MANA_COST,      mod("blj_mc", 20), EquipmentSlotGroup.ANY)
            .build();
    }

    private static AttributeModifier mod(String id, double val) {
        return new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath("yizmodqzk", id),
            val, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public SkillCastMode getCastMode(ItemStack stack) { return SkillCastMode.INSTANT; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9奔雷疾"));
        tooltip.add(Component.literal("§7向前突进冲刺"));
        tooltip.add(Component.literal("§7获得一次免疫掉落伤害"));
        tooltip.add(Component.literal("§8冷却: 80tick  蓝耗: 20"));
    }

    @Override
    public void onCast(Player player, ItemStack stack) {
        if (player.level().isClientSide()) return;

        // 服务端权威：先校验蓝量，不足则整个施法不发生（突进/伤害/强化都不触发）。
        float reduction = (float) player.getAttributeValue(YizAttributes.MANA_COST_REDUCTION);
        if (!net.minecraft.client.yiz.tool.health.ManaTracker.consume(player, Math.max(0, 20 - reduction)))
            return;

        // 蓝量充足 → 突进。方向由客户端捕获、服务端读取；无输入时用视线方向。
        Vec3 dir = net.minecraft.client.yiz.handler.CastDirectionTracker.consume();
        if (dir == null) dir = player.getLookAngle();
        player.push(dir.x * 2.0, dir.y * 0.4, dir.z * 2.0);
        player.hurtMarked = true; // 同步 motion 给客户端

        // 一次免疫掉落伤害
        net.minecraft.client.yiz.handler.FallImmunityTracker.grant(player);
        // 突进期间体表电流（纯视觉，无链式闪电无传染）
        net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockVisualOnly(player, 0f, 10);

        // 开启入口：分发 ACTIVATE 时机强化标签（雷震千里击退等在此触发）。
        int slot = net.minecraft.client.yiz.handler.LastCastSlotTracker.get();
        onActivate(slot, player, stack);
    }
}
