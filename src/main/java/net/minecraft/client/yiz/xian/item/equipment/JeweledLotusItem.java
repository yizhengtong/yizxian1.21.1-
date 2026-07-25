package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 珠光莲花 — 精准词条：所有伤害可暴击。
 *
 * <pre>
 *            普通版    光明版
 *  暴击率    25%      50%
 *  法术强度  2.5      5
 *  精准      ✓       ✓
 * </pre>
 */
public class JeweledLotusItem extends Item implements IEquipmentItem {

    private final boolean bright;

    public JeweledLotusItem(boolean bright) {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers(bright)));
        this.bright = bright;
    }

    private static ItemAttributeModifiers buildModifiers(boolean bright) {
        double m = bright ? 2.0 : 1.0;
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.CRIT_RATE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:jl_cr"),
                    25.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.ATTACK_STRENGTH,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:jl_as"),
                    2.5 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.PRECISION,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:jl_prec"),
                    1.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·精准"));
        tooltip.add(Component.literal("§7所有伤害均可暴击"));
        tooltip.add(Component.literal("§8唯一被动"));
    }

    @Override public String getUniqueEquipmentGroup() { return ""; }
    @Override public String getUniquePassiveGroup() { return "jeweled_lotus"; }

    public boolean isBright() { return bright; }
}
