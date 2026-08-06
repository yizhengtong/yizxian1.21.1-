package net.minecraft.client.yiz.xian.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 简化护甲套装注册工具 — 只指定耐久值，护甲值/韧性/附魔全 0，修复材料空。
 * <p>每套护甲：1 个 {@link ArmorMaterial} + 4 件 {@link SimpleArmorItem}（头/胸/腿/靴）。
 * 穿戴层纹理：<code>textures/models/armor/&lt;材质名&gt;_layer_1.png</code>（胸/腿/靴）、
 * <code>..._layer_2.png</code>（头）。</p>
 */
public final class ArmorSet {

    private ArmorSet() {}

    /**
     * 构建一个仅含穿戴语义的护甲材料（护甲值/韧性/附魔全 0，修复材料空）。
     *
     * @param modid 命名空间（如 yizxianmod）
     * @param name  材质名 —— 决定穿戴层纹理文件名 {@code <name>_layer_1/2.png}
     */
    public static Holder<ArmorMaterial> material(String modid, String name) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (var t : ArmorItem.Type.values()) defense.put(t, 0);
        return Holder.direct(new ArmorMaterial(
            defense,                              // 护甲值全 0
            0,                                    // enchantmentValue
            SoundEvents.ARMOR_EQUIP_IRON,         // equipSound
            () -> Ingredient.EMPTY,               // repairIngredient
            List.of(new ArmorMaterial.Layer(
                ResourceLocation.fromNamespaceAndPath(modid, name), "", false)),
            0f,                                   // toughness
            0f                                    // knockbackResistance
        ));
    }

    /**
     * 注册一套护甲 3 件（helmet / chestplate / boots）。
     * <p>全局决策：本 mod 的护甲套装全部不含护腿（leggings）。</p>
     * <p>耐久按原版槽位系数比例分配：头:胸:靴 = 11:16:13。
     * 传入的 {@code baseDurability} 作为胸甲耐久（系数 16），
     * 头盔 = base × 11/16、靴子 = base × 13/16。</p>
     *
     * @param items          物品 DeferredRegister（YizxianMod.ITEMS）
     * @param modid          命名空间
     * @param id             物品 ID 前缀（如 zhongtie → zhongtie_helmet 等）
     * @param material       由 {@link #material} 构建的护甲材料
     * @param baseDurability 基准耐久（= 胸甲耐久，系数 16）
     * @return 3 件物品 Supplier，按 头/胸/靴 顺序
     */
    public static List<Supplier<Item>> register(DeferredRegister<Item> items, String modid,
                                                String id, Holder<ArmorMaterial> material,
                                                int baseDurability) {
        int helmet = Math.round(baseDurability * 11f / 16f);
        int boots  = Math.round(baseDurability * 13f / 16f);
        return List.of(
            items.register(id + "_helmet", () -> new SimpleArmorItem(material, ArmorItem.Type.HELMET, helmet)),
            items.register(id + "_chestplate", () -> new SimpleArmorItem(material, ArmorItem.Type.CHESTPLATE, baseDurability)),
            items.register(id + "_boots", () -> new SimpleArmorItem(material, ArmorItem.Type.BOOTS, boots))
        );
    }
}
