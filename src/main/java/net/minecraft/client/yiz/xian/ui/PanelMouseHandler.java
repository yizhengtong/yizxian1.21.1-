package net.minecraft.client.yiz.xian.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 面板槽位鼠标交互逻辑：左键拾取/放置/交换、右键半堆、Shift+点击快速移动。
 */
public final class PanelMouseHandler {

    private PanelMouseHandler() {}

    public static boolean handleSlotClick(
            int slotIndex, int button,
            AbstractContainerMenu menu, SimpleContainer container, Player player
    ) {
        if (slotIndex < 0 || slotIndex >= container.getContainerSize()) return false;
        if (button == 2) return false;

        boolean isShift = Screen.hasShiftDown();
        ItemStack carried = menu.getCarried();
        ItemStack slotStack = container.getItem(slotIndex);

        if (isShift && button == 0) {
            return handleShiftClick(slotIndex, container, menu, player);
        }
        if (button == 0) {
            return handleLeftClick(slotIndex, container, menu, carried, slotStack);
        }
        if (button == 1) {
            return handleRightClick(slotIndex, container, menu, carried, slotStack);
        }
        return false;
    }

    private static boolean handleLeftClick(
            int idx, SimpleContainer container, AbstractContainerMenu menu,
            ItemStack carried, ItemStack slotStack
    ) {
        if (carried.isEmpty() && slotStack.isEmpty()) return true;
        if (!carried.isEmpty() && slotStack.isEmpty()) {
            container.setItem(idx, carried.copy());
            menu.setCarried(ItemStack.EMPTY);
        } else if (carried.isEmpty() && !slotStack.isEmpty()) {
            menu.setCarried(slotStack.copy());
            container.setItem(idx, ItemStack.EMPTY);
        } else {
            if (ItemStack.isSameItemSameComponents(carried, slotStack)) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int transfer = Math.min(carried.getCount(), space);
                if (transfer > 0) {
                    slotStack.grow(transfer);
                    carried.shrink(transfer);
                    container.setItem(idx, slotStack);
                    menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
            } else {
                container.setItem(idx, carried.copy());
                menu.setCarried(slotStack.copy());
            }
        }
        container.setChanged();
        return true;
    }

    private static boolean handleRightClick(
            int idx, SimpleContainer container, AbstractContainerMenu menu,
            ItemStack carried, ItemStack slotStack
    ) {
        if (!carried.isEmpty() && slotStack.isEmpty()) {
            ItemStack one = carried.copy();
            one.setCount(1);
            container.setItem(idx, one);
            carried.shrink(1);
            menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        } else if (carried.isEmpty() && !slotStack.isEmpty()) {
            int take = (slotStack.getCount() + 1) / 2;
            ItemStack half = slotStack.copy();
            half.setCount(take);
            slotStack.shrink(take);
            container.setItem(idx, slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
            menu.setCarried(half);
        } else if (!carried.isEmpty() && !slotStack.isEmpty()
                && ItemStack.isSameItemSameComponents(carried, slotStack)) {
            int space = slotStack.getMaxStackSize() - slotStack.getCount();
            if (space > 0) {
                slotStack.grow(1);
                carried.shrink(1);
                container.setItem(idx, slotStack);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        }
        container.setChanged();
        return true;
    }

    private static boolean handleShiftClick(
            int idx, SimpleContainer container,
            AbstractContainerMenu menu, Player player
    ) {
        ItemStack slotStack = container.getItem(idx);
        if (slotStack.isEmpty()) return true;
        ItemStack remaining = tryMoveToPlayerInventory(slotStack.copy(), menu, player);
        if (remaining.isEmpty()) {
            container.setItem(idx, ItemStack.EMPTY);
        } else if (remaining.getCount() < slotStack.getCount()) {
            container.setItem(idx, remaining);
        }
        container.setChanged();
        return true;
    }

    private static ItemStack tryMoveToPlayerInventory(
            ItemStack stack, AbstractContainerMenu menu, Player player
    ) {
        ItemStack remaining = stack.copy();
        for (Slot slot : menu.slots) {
            if (!slot.container.equals(player.getInventory())) continue;
            if (!slot.mayPlace(stack)) continue;
            ItemStack existing = slot.getItem();
            if (existing.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getCount();
            if (space > 0) {
                int transfer = Math.min(remaining.getCount(), space);
                existing.grow(transfer);
                remaining.shrink(transfer);
                slot.setChanged();
                if (remaining.isEmpty()) return ItemStack.EMPTY;
            }
        }
        for (Slot slot : menu.slots) {
            if (!slot.container.equals(player.getInventory())) continue;
            if (!slot.mayPlace(stack)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty()) continue;
            slot.set(remaining.copy());
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        return remaining;
    }
}
