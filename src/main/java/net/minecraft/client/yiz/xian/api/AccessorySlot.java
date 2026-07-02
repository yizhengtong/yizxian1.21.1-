package net.minecraft.client.yiz.xian.api;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品槽 — 标准 {@link Slot} 子类。
 *
 * <p>每个槽最多放 1 个物品。不限制可放置的物品类型，
 * 物品过滤留给未来扩展（如通过 Item tag / capability 判定）。</p>
 */
public class AccessorySlot extends Slot {

    public AccessorySlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
