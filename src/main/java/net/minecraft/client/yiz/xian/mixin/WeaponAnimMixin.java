package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ILeftHandRender 武器攻击：推进连招状态机。
 * 范围伤害由 {@code AutoAttackMixin.performSweepAttack} 独立处理。
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack held = self.getMainHandItem();
        if (!(held.getItem() instanceof ILeftHandRender)) return;

        ComboStateMachine.onAttack(self);
    }
}
