package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ILeftHandRender 武器的攻击冷却控制。
 *
 * <p>把冷却焊死在 1.2s（与动画 swing timer 一致），
 * 冷却条只显示满或空（二值化），不满时禁止攻击。</p>
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    private static final int COOLDOWN_TICKS = 24;

    // ── 1. 强行焊死冷却时长 → 24 ticks (1.2s) ──

    @ModifyReturnValue(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"))
    private int yizxian_forceCooldown(int original) {
        Player self = (Player) (Object) this;
        if (self.getMainHandItem().getItem() instanceof ILeftHandRender) {
            return COOLDOWN_TICKS;
        }
        return original;
    }

    // ── 2. 二值化冷却条：原版算完比例后阈值化为 0.0 或 1.0 ──

    @Inject(method = "getAttackStrengthScale", at = @At("RETURN"), cancellable = true)
    private void yizxian_binaryCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getMainHandItem().getItem() instanceof ILeftHandRender)) return;
        cir.setReturnValue(cir.getReturnValue() >= 1.0f ? 1.0f : 0.0f);
    }

    // ── 3. 不满时禁止攻击（服务端），满时推进连招 ──

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack held = self.getMainHandItem();
        if (!(held.getItem() instanceof ILeftHandRender)) return;

        // 冷却不满 → 禁止攻击（getAttackStrengthScale 已被二值化）
        if (self.getAttackStrengthScale(0f) < 1.0f) {
            ci.cancel();
            return;
        }

        ComboStateMachine.onAttack(self);
    }

}
