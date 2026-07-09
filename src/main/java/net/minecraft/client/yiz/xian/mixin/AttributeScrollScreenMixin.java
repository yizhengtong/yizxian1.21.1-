package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.item.AttributeScrollItem;
import net.minecraft.client.yiz.xian.network.C2SAttributeApplyPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 属性卷轴交互：拦截背包/容器界面的右键点击。
 * 右键卷轴 → 目标物品槽位 → 发送 C2S 包应用属性。
 */
@Mixin(AbstractContainerScreen.class)
public class AttributeScrollScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 1) return; // 只拦截右键
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        ItemStack carried = screen.getMenu().getCarried();
        if (!AttributeScrollItem.isScroll(carried)) return;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;

        String attrId = AttributeScrollItem.getAttrId(carried);
        int delta = AttributeScrollItem.getDelta(carried);
        if (attrId == null || delta == 0) return;

        // 发送 C2S 包，由服务端执行实际属性修改
        C2SAttributeApplyPayload.send(attrId, delta, slot.index);

        // 客户端消耗 1 个卷轴（服务端看不到光标物品）
        carried.shrink(1);
        if (carried.isEmpty()) screen.getMenu().setCarried(ItemStack.EMPTY);

        // 取消原事件，防止物品交换
        cir.setReturnValue(true);
    }
}