package net.minecraft.client.yiz.xian.mixin.terraria;

import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附加跳坠落伤害减免 —— <b>常驻被动</b>，由 {@link ExtraJumpData#getFallSafeByGear} /
 * {@link ExtraJumpData#getFallReduceByGear} 实时按装备总能力查询。
 *
 * <p>用户决策 2026-07-06（常驻化修正）：减免只由<b>装备提供的总跳跃能力</b>决定，
 * 与本下落用没用附加跳<b>无关</b>。玩家只要装备着跳瓶就享受减免（哪怕走下悬崖摔下去）：</p>
 * <ul>
 *   <li>常驻安全距离 = Σ（各档跳跃高度 × 该档装备次数）：云 4 / 暴雪 5 / 沙 7</li>
 *   <li>常驻伤害减免 = Σ（各档 ⌈高度/2⌉ × 该档装备次数）：云 2 / 暴雪 3 / 沙 4</li>
 *   <li>公式：{@code damage = max(0, ⌈fallDistance - 安全距离⌉ - 减免)}</li>
 * </ul>
 * <p>例：装云朵瓶(1云) + 沙暴瓶(1沙) → safe=4+7=11、reduce=2+4=6。从 11 格内摔 0 伤。</p>
 *
 * <p>注入 {@code LivingEntity.calculateFallDamage} HEAD 覆盖原版伤害计算。
 * 跳跃提升药水（{@link MobEffects#JUMP}）作为独立来源仍按原版 +1/级叠加在安全距离上。</p>
 *
 * <p>无累加/清零时序问题 —— 减免纯实时查询，无副作用状态。</p>
 */
@Mixin(LivingEntity.class)
public abstract class MixinExtraJumpFallDamage {

    @Inject(method = "calculateFallDamage", at = @At("HEAD"), cancellable = true)
    private void yizxian$reduceFallDamageByExtraJump(float distance, float multiplier,
                                                     CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        int fallSafe = ExtraJumpData.getFallSafeByGear(player);
        int fallReduce = ExtraJumpData.getFallReduceByGear(player);
        if (fallSafe <= 0 && fallReduce <= 0) return;   // 没装备跳瓶，原版处理

        // 跳跃提升药水：每级 +1 安全距离（原版行为，独立来源叠加）
        int jumpBoost = 0;
        MobEffectInstance eff = player.getEffect(MobEffects.JUMP);
        if (eff != null) jumpBoost = eff.getAmplifier() + 1;

        // 常驻安全距离 + 药水加成
        float safe = fallSafe + jumpBoost;
        int damage = (int) Math.ceil((distance - safe) * multiplier);
        damage = Math.max(0, damage - fallReduce);   // 常驻减免
        cir.setReturnValue(damage);
    }
}
