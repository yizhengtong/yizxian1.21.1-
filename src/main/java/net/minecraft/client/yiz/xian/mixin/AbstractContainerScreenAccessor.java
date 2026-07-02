package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link AbstractContainerScreen} 的 protected 字段 {@code leftPos} 和 {@code topPos}。
 * 用于在 target {@code InventoryScreen} 的 mixin 中无需 {@code @Shadow} 即可读取这些值。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor
    int getLeftPos();

    @Accessor
    int getTopPos();
}
