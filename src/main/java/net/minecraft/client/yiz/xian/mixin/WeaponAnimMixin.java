package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * ILeftHandRender 武器攻击：推进连招 + 前方 3×3×3 范围伤害。
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    @Unique private boolean yizxian$sweeping = false;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;
        if (yizxian$sweeping) return; // 防止递归重入

        ItemStack held = self.getMainHandItem();
        if (!(held.getItem() instanceof ILeftHandRender)) return;

        ci.cancel();
        yizxian$sweeping = true;

        try {
            ComboStateMachine.onAttack(self);

            // 前方 3×3×3 范围全扫
            Vec3 look = self.getLookAngle();
            Vec3 eye = self.getEyePosition();
            Vec3 center = eye.add(look.scale(2.5));
            double r = 1.5;
            AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                                center.x + r, center.y + r, center.z + r);

            List<LivingEntity> hits = self.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != self && e.isAlive() && e.isAttackable() && !e.isAlliedTo(self));

            for (LivingEntity hit : hits) {
                self.attack(hit); // 不会重入，yizxian$sweeping=true
            }
        } finally {
            yizxian$sweeping = false;
        }
    }
}
