package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ILeftHandRender 武器攻击冷却：二值化（满/空），不满时禁止攻击。
 * 冷却时长由原版根据武器攻速属性自动决定，不硬编码。
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    // ── 1. 二值化冷却条：原版算完比例后阈值化为 0.0 或 1.0 ──

    @Inject(method = "getAttackStrengthScale", at = @At("RETURN"), cancellable = true)
    private void yizxian_binaryCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getMainHandItem().getItem() instanceof ILeftHandRender)) return;
        cir.setReturnValue(cir.getReturnValue() >= 1.0f ? 1.0f : 0.0f);
    }

    // ── 2. 不满时禁止攻击（服务端），满时推进连招 ──

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack held = self.getMainHandItem();
        if (!(held.getItem() instanceof ILeftHandRender)) return;

        if (self.getAttackStrengthScale(0f) < 1.0f) {
            ci.cancel();
            return;
        }

        ComboStateMachine.onAttack(self);
    }
}
