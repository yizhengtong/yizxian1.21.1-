package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 疾射火炮 — 攻速 / 手长 / 自动攻击装。
 * <pre>
 *              普通版          光明版
 *  冷却缩减      25%            50%
 *  交互距离      +2             +4
 *  自动攻击      ✓              ✓
 *  连击          —              100%率 / +1次 / 30%倍率（光明专属）
 * </pre>
 */
public class RapidFirecannonItem extends Item implements IEquipmentItem {

    private final boolean bright;

    public RapidFirecannonItem(boolean bright) {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers(bright)));
        this.bright = bright;
    }

    private static ItemAttributeModifiers buildModifiers(boolean bright) {
        double m = bright ? 2.0 : 1.0;
        var builder = ItemAttributeModifiers.builder()
            .add(YizAttributes.COOLDOWN_REDUCTION,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_cdr"),
                    25.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            // +2 格交互距离（亮版翻倍为 +4），经 mirrorAttackRange 镜像到 ENTITY/BLOCK_INTERACTION_RANGE
            .add(YizAttributes.ATTACK_RANGE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_range"),
                    2.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            // 自动攻击：装备即生效，无需 auto_attack 附魔
            .add(YizAttributes.AUTO_ATTACK,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_autoatk"),
                    1.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY);
        // 光明版额外：连击（自动攻击 + 连击 = 全自动多段）
        if (bright) {
            builder.add(YizAttributes.COMBO_RATE,
                    new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_combo_rate"),
                        100.0, AttributeModifier.Operation.ADD_VALUE),
                    net.minecraft.world.entity.EquipmentSlotGroup.ANY)
                .add(YizAttributes.COMBO_COUNT,
                    new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_combo_count"),
                        1.0, AttributeModifier.Operation.ADD_VALUE),
                    net.minecraft.world.entity.EquipmentSlotGroup.ANY)
                .add(YizAttributes.COMBO_VALUE,
                    new AttributeModifier(ResourceLocation.parse("yizxianmod:rfc_combo_value"),
                        30.0, AttributeModifier.Operation.ADD_VALUE),
                    net.minecraft.world.entity.EquipmentSlotGroup.ANY);
        }
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·自动攻击"));
        tooltip.add(Component.literal("§7按住攻击键自动攻击（无需自动攻击附魔）"));
        if (bright) {
            tooltip.add(Component.literal("§9被动·连击"));
            tooltip.add(Component.literal("§7攻击额外触发连击（§f100%§7率 / §f+1§7次 / §f30%§7倍率）"));
        }
    }

    @Override public String getUniqueEquipmentGroup() { return ""; }
    @Override public String getUniquePassiveGroup() { return ""; }

    public boolean isBright() { return bright; }
}
