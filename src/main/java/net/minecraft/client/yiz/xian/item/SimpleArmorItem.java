package net.minecraft.client.yiz.xian.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 简化护甲 — 只指定耐久值，不提供护甲值/韧性/附魔。
 * <p>穿戴渲染仍走 {@link ArmorItem} 标准管线，穿戴层纹理：
 * <pre>textures/models/armor/&lt;材质名&gt;_layer_1.png （胸甲/护腿/靴子）
 * textures/models/armor/&lt;材质名&gt;_layer_2.png （头盔）</pre>
 * 材质名由 {@link ArmorSet#material} 决定。</p>
 */
public class SimpleArmorItem extends ArmorItem {

    private final int durability;

    public SimpleArmorItem(Holder<ArmorMaterial> material, Type type, int durability) {
        super(material, type, new Item.Properties().stacksTo(1));
        this.durability = durability;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return durability;
    }
}
