package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.terraria.AccessoryFlags;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JUMP_STRENGTH 属性生效 —— 注入 {@link LivingEntity#getJumpPower()}。
 */
@Mixin(LivingEntity.class)
public abstract class MixinJumpStrength {

    @Inject(method = "getJumpPower", at = @At("RETURN"), cancellable = true)
    private void yizxian$applyJumpStrength(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        float jumpPct = AccessoryFlags.sumValues(player)
            .getOrDefault(EffectTag.JUMP_STRENGTH, 0f);
        if (jumpPct == 0) return;
        cir.setReturnValue(cir.getReturnValue() * (1f + jumpPct / 100f));
    }
}
