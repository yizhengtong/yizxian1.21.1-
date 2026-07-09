package net.minecraft.client.yiz.xian.api;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.item.AttributeScrollItem;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 属性卷轴交互处理 — 在背包/容器界面右键时应用属性。
 */
public final class AttributeScrollHandler {

    private AttributeScrollHandler() {}

    /**
     * 在 {@link ScreenEvent.MouseButtonPressedEvent.Pre} 中调用。
     * 检测右键卷轴 → 目标物品 → 执行属性变更。
     */
    public static boolean onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return false; // 只拦截右键
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return false;

        ItemStack carried = screen.getMenu().getCarried();
        if (!AttributeScrollItem.isScroll(carried)) return false;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return false;

        ItemStack target = slot.getItem();
        String attrId = AttributeScrollItem.getAttrId(carried);
        int delta = AttributeScrollItem.getDelta(carried);
        if (attrId == null || delta == 0) return false;

        // 通过服务端网络包执行实际修改
        net.minecraft.client.yiz.xian.network.C2SAttributeApplyPayload.send(attrId, delta, slot.index);

        // 取消原事件，避免物品被放入槽位
        event.setCanceled(true);
        return true;
    }
}