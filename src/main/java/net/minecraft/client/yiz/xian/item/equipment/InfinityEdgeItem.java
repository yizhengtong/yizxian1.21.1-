package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.api.IPassiveItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 无尽之刃 — 暴击率超过 100% 的部分 1:1 转化为暴击效果。
 *
 * <pre>
 *              普通版      光明版
 *  攻击强度    35%        70%   (按百分比增幅伤害)
 *  暴击率     75%        150%
 * </pre>
 */
public class InfinityEdgeItem extends Item implements IEquipmentItem, IPassiveItem {

    private static final ResourceLocation OVERFLOW_ID = ResourceLocation.parse("yizxianmod:ie_overflow");

    private final boolean bright;

    public InfinityEdgeItem(boolean bright) {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers(bright)));
        this.bright = bright;
    }

    private static ItemAttributeModifiers buildModifiers(boolean bright) {
        double m = bright ? 2.0 : 1.0;
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.ATTACK_STRENGTH,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:ie_as"),
                    35.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.CRIT_RATE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:ie_cr"),
                    75.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·溢出"));
        tooltip.add(Component.literal("§7暴击率超过 §f100%§7 的部分 §f1:1§7 转化为暴击伤害"));
        tooltip.add(Component.literal("§8唯一装备 · 唯一被动"));
    }

    // ── 唯一限制 ──────────────────────────────────

    @Override public String getUniqueEquipmentGroup() { return "infinity_edge"; }
    @Override public String getUniquePassiveGroup() { return "infinity_edge"; }

    // ── 暴击溢出 → 暴伤 ──────────────────────────

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        syncOverflow(player);
    }

    @Override
    public void onEquip(Player player, ItemStack stack, int slot) {
        syncOverflow(player);
    }

    @Override
    public void onUnequip(Player player, ItemStack stack, int slot) {
        var cdi = player.getAttribute(YizAttributes.CRIT_DAMAGE);
        if (cdi != null) cdi.removeModifier(OVERFLOW_ID);
    }

    /**
     * 暴击溢出 → 暴伤：暴击率超过 100% 的部分 <b>固定 1:1</b> 转化为暴击伤害。
     * <p>光明版<b>不享受倍率</b>（不两倍转化）——仅暴击率数值本身随 2× 倍率更高、溢出量更大，
     * 但每点溢出仍只换 1 点暴伤。此比率固定，勿改成 2×。</p>
     */
    private void syncOverflow(Player player) {
        var cri = player.getAttribute(YizAttributes.CRIT_RATE);
        var cdi = player.getAttribute(YizAttributes.CRIT_DAMAGE);
        if (cri == null || cdi == null) return;
        double excess = Math.max(0, cri.getValue() - 100.0);
        cdi.removeModifier(OVERFLOW_ID);
        if (excess > 0) {
            cdi.addTransientModifier(new AttributeModifier(
                OVERFLOW_ID, excess, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {}
}
