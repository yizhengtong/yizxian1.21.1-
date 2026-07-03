package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.handler.HeartWingsKeyMappings;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 悬停锁死：在 travel() 执行前将速度归零，从源头阻止 physics 产生位移。
 */
@Mixin(LivingEntity.class)
public abstract class MixinHoverLock extends Entity {

    public MixinHoverLock(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void yizxian_hoverLock(Vec3 travelVector, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!player.isFallFlying()) return;
        if (!HeartWingsKeyMappings.HOVER.isDown()) return;
        if (!hasHeartWings(player)) return;

        // 完全取消 travel()，焊死在当前位置
        this.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
    }

    private static boolean hasHeartWings(Player player) {
        AccessoryContainer c = AccessoryContainer.get(player);
        for (int i = 0; i < c.getSlotCount(); i++) {
            if (c.getItem(i).getItem() instanceof HeartWingsItem) return true;
        }
        return false;
    }
}
