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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ILeftHandRender 武器攻击冷却：焊死在 1.2s，二值化，不满禁止攻击。
 *
 * <p>用 {@code @Unique} 字段追踪攻击时刻（毫秒），计算经过的 tick 数替代原版 ticker。
 * 不依赖原版 Player 内部字段（attackStrengthTicker 在 NeoForge 1.21.1 中已改名/移除）。</p>
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    private static final int COOLDOWN_TICKS = 24;
    private static final float MS_PER_TICK = 50f;

    @Unique private long yizxian$lastAttackMs = 0;
    @Unique private boolean yizxian$wasSwinging = false;

    // ── 1. 二值化 + 焊死在 1.2s ──

    @SuppressWarnings("deprecation")
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void yizxian_binaryCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getMainHandItem().getItem() instanceof ILeftHandRender)) return;

        long now = System.currentTimeMillis();

        // 检测 swing 上升沿 → 攻击开始
        if (self.swinging && !yizxian$wasSwinging) {
            yizxian$lastAttackMs = now;
        }
        yizxian$wasSwinging = self.swinging;

        if (yizxian$lastAttackMs == 0) {
            cir.setReturnValue(1.0f); // 从未攻击过，冷却满
            return;
        }

        long elapsedMs = now - yizxian$lastAttackMs;
        float elapsedTicks = elapsedMs / MS_PER_TICK + adjustTicks;
        float raw = elapsedTicks / (float) COOLDOWN_TICKS;
        cir.setReturnValue(raw >= 1.0f ? 1.0f : 0.0f);
    }

    // ── 2. 不满禁止攻击（服务端），满时推进连招 ──

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
