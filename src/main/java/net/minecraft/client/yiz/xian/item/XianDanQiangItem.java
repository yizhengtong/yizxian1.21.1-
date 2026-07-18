package net.minecraft.client.yiz.xian.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 霰弹枪 — 远程武器（3D 物品模型）。
 *
 * <p>当前为占位实现，仅完成物品注册、创造标签（远武）与 3D 模型/贴图接入。
 * 实际射击逻辑（弹药、散射、伤害）待后续补充。</p>
 */
public class XianDanQiangItem extends Item {

    public XianDanQiangItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 14;
    }
}
