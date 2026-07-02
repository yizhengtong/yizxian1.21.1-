package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.api.AccessorySlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 向 InventoryMenu 注入饰品槽。
 *
 * <p>不做签名特定的构造注入（1.21.1 + NeoForge 上 InventoryMenu 构造签名多变），
 * 而是在构造末尾从已有 slot 中找到 Inventory → Player，再追加饰品槽。</p>
 */
@Mixin(InventoryMenu.class)
public abstract class MixinInventoryMenu extends AbstractContainerMenu {

    private static final int ACCESSORY_Y = 166;

    protected MixinInventoryMenu() {
        super(null, 0);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void yizxian_addAccessorySlots(CallbackInfo ci) {
        // 从已有 slot 中反推 Player（查找第一个 container 是 Inventory 的 slot）
        Player owner = null;
        for (Slot slot : this.slots) {
            if (slot.container instanceof Inventory inv) {
                owner = inv.player;
                break;
            }
        }
        if (owner == null) return;

        AccessoryContainer container = AccessoryContainer.get(owner);
        for (int i = 0; i < container.getSlotCount(); i++) {
            this.addSlot(new AccessorySlot(container, i, 8 + i * 18, ACCESSORY_Y));
        }
    }
}
