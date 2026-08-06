package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截挥舞动画：手持昭明法杖时固定为静态手持（不挥舞）。
 * 右键施法 / 左键都不触发原版挥臂动画。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxian$blockStaffSwing(InteractionHand hand, boolean broadcast, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (main.getItem() instanceof net.minecraft.client.yiz.xian.item.WupinItem
                    || off.getItem() instanceof net.minecraft.client.yiz.xian.item.WupinItem) {
                ci.cancel();
            }
        }
    }
}
