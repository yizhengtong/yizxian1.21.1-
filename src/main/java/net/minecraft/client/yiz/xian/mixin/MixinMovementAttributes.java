package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.terraria.AccessoryFlags;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 移动速度属性生效 —— MOVE_SPEED / MAX_RUN_SPEED / AIR_SPEED。
 * <p>MOVE_SPEED + MAX_RUN_SPEED 注入 {@link Player#getSpeed()}（地面/冲刺加速度）。
 * AIR_SPEED 注入 {@code travel()} HEAD 降低空中水平摩擦力，增加跳跃水平距离。</p>
 */
@Mixin(Player.class)
public abstract class MixinMovementAttributes {

    private static final WeakHashMap<Player, Integer> SPRINT_TICKS = new WeakHashMap<>();
    private static final int SPRINT_RAMP_TICKS = 60;
    private static final double DEFAULT_SPRINT_BONUS = 0.50;

    /** MOVE_SPEED + MAX_RUN_SPEED：改写 getSpeed() 返回值。 */
    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
    private void yizxian$applyMovementAttrs(CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        Map<EffectTag, Float> attrs = AccessoryFlags.sumValues(player);
        float movePct = attrs.getOrDefault(EffectTag.MOVE_SPEED, 0f);
        float runPct  = attrs.getOrDefault(EffectTag.MAX_RUN_SPEED, 0f);
        if (movePct == 0 && runPct == 0) return;

        float walkSpeed = cir.getReturnValue() * (1f + movePct / 100f);

        boolean sprinting = player.isSprinting() && player.onGround();
        int ticks = sprinting ? SPRINT_TICKS.getOrDefault(player, 0) + 1 : 0;
        SPRINT_TICKS.put(player, ticks);
        double sprintMult = 1.0;
        if (sprinting && ticks > 0) {
            double max = DEFAULT_SPRINT_BONUS + runPct / 100.0;
            sprintMult = 1.0 + max * Math.min((double) ticks / SPRINT_RAMP_TICKS, 1.0);
        }

        cir.setReturnValue(walkSpeed * (float) sprintMult);
    }

    /**
     * AIR_SPEED：降低空中水平摩擦力，增加跳跃水平距离。
     * <p>MC 玩家空中水平摩擦每 tick：{@code vₓ *= 0.91}。跳跃总水平位移 = 初速 / (1 - 0.91)。
     * AIR_SPEED 加成 B% 时目标位移 = 原位移 × (1 + B/100)，反解目标摩擦 f'：
     * {@code f' = 1 - 0.09 / (1 + B/100)}。
     * 在 travel() HEAD 预乘 {@code f' / 0.91}，travel 内部再 ×0.91 → 净摩擦 = f'。</p>
     */
    @Inject(method = "travel", at = @At("HEAD"))
    private void yizxian$applyAirSpeed(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.onGround() || player.isFallFlying()) return;
        float airPct = AccessoryFlags.sumValues(player).getOrDefault(EffectTag.AIR_SPEED, 0f);
        if (airPct <= 0) return;
        double desiredFriction = 1.0 - 0.09 / (1.0 + airPct * 2.0 / 100.0);   // 2×倍率修正：20% → 等效40%
        double preFactor = desiredFriction / 0.91;   // 预乘，travel 内 ×0.91 后净 = desiredFriction
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x * preFactor, dm.y, dm.z * preFactor);
    }
}
