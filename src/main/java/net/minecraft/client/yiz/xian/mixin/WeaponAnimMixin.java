package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ILeftHandRender 武器的攻击冷却控制。
 *
 * <p>把冷却焊死在 1.2s，冷却条二值化（满/空），不满时禁止攻击。
 * 全部逻辑收敛在 getAttackStrengthScale，不碰 getCurrentItemAttackStrengthDelay
 * 以避免与其他 mod 的 RETURN 注入冲突。</p>
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    private static final int COOLDOWN_TICKS = 24;

    @Accessor
    public abstract int getAttackStrengthTicker();

    // ── 二值化 + 焊死冷却: 用 COOLDOWN_TICKS 算比例，阈值化 ──

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void yizxian_binaryCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getMainHandItem().getItem() instanceof ILeftHandRender)) return;

        float raw = (getAttackStrengthTicker() + adjustTicks) / (float) COOLDOWN_TICKS;
        cir.setReturnValue(raw >= 1.0f ? 1.0f : 0.0f);
    }

    // ── 不满时禁止攻击（服务端），满时推进连招 ──

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
