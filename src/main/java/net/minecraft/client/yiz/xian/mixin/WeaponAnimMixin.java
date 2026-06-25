package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ILeftHandRender 武器的攻击冷却控制。
 *
 * <p>把攻击冷却焊死在 1.2 秒（与 swing timer 一致），
 * 冷却条只显示满(1.0)或空(0.0)，不满时禁止攻击，
 * 保证攻击动画和攻速完全同步。</p>
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    /** 武器攻击冷却目标时长（tick 数，1.2s @ 20tps） */
    private static final int COOLDOWN_TICKS = 24;

    @Shadow public int attackStrengthTicker;

    // ── 1. 强行焊死冷却时长 ──

    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("HEAD"), cancellable = true)
    private void yizxian_forceCooldown(CallbackInfoReturnable<Integer> cir) {
        Player self = (Player) (Object) this;
        if (self.getMainHandItem().getItem() instanceof ILeftHandRender) {
            cir.setReturnValue(COOLDOWN_TICKS);
        }
    }

    // ── 2. 二值化冷却条：只返回 0.0 或 1.0 ──

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void yizxian_binaryCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getMainHandItem().getItem() instanceof ILeftHandRender)) return;
        int delay = COOLDOWN_TICKS;
        if (delay <= 0) { cir.setReturnValue(1.0f); return; }
        float raw = (attackStrengthTicker + adjustTicks) / (float) delay;
        cir.setReturnValue(raw >= 1.0f ? 1.0f : 0.0f);
    }

    // ── 3. 不满时禁止攻击（服务端），并推进连招 ──

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack held = self.getMainHandItem();
        if (!(held.getItem() instanceof ILeftHandRender)) return;

        // 冷却不满 → 禁止攻击
        float scale = computeAttackStrengthScale(self);
        if (scale < 1.0f) {
            ci.cancel();
            return;
        }

        ComboStateMachine.onAttack(self);
    }

    /**
     * 公开方法：判断武器攻击冷却是否已满。
     * 外部可直接 {@code WeaponAnimMixin.isCooldownFull(player)} 查询。
     */
    public static boolean isCooldownFull(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof ILeftHandRender)) return true;
        return computeAttackStrengthScale(player) >= 1.0f;
    }

    /** 计算原始冷却比例（0→1），不受二值化影响 */
    private static float computeAttackStrengthScale(Player player) {
        int ticker = ((WeaponAnimMixin) (Object) player).attackStrengthTicker;
        int delay = COOLDOWN_TICKS;
        if (delay <= 0) return 1.0f;
        return Mth.clamp((float) ticker / (float) delay, 0.0f, 1.0f);
    }
}
