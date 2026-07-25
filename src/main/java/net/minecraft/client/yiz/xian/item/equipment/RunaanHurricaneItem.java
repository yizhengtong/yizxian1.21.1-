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
 * 卢安娜的飓风 — +1 连击次数；连击倍率 普通 50% / 光明 100%。
 */
public class RunaanHurricaneItem extends Item implements IEquipmentItem {

    private final boolean bright;

    public RunaanHurricaneItem(boolean bright) {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers(bright)));
        this.bright = bright;
    }

    private static ItemAttributeModifiers buildModifiers(boolean bright) {
        double m = bright ? 2.0 : 1.0;
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.COOLDOWN_REDUCTION,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rh_cdr"),
                    10.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.SPELL_DEFENSE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rh_sd"),
                    4.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.COMBO_RATE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rh_cr"),
                    100.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.COMBO_COUNT,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rh_cc"),
                    1.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.COMBO_VALUE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:rh_cv"),
                    50.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·连击"));
        tooltip.add(Component.literal("§7攻击额外触发 §f1§7 次连击（连击倍率 §f" + (bright ? "100%" : "50%") + "§7）"));
    }

    @Override public String getUniqueEquipmentGroup() { return ""; }
    @Override public String getUniquePassiveGroup() { return ""; }

    public boolean isBright() { return bright; }
}
