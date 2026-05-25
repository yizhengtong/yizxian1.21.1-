package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * /kill 放行 Mixin —— 拦截 {@link LivingEntity#hurt}，
 * 当伤害来源是 genericKill 时，临时放行保护态（允许血量归零）。
 */
@Mixin(LivingEntity.class)
public class KillBypassMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void onHurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (isGenericKill(source)) {
            EntityASMUtil.beginBypassProtection();
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void onHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (isGenericKill(source)) {
            EntityASMUtil.endBypassProtection();
        }
    }

    /** 判断是否是 /kill 指令的伤害来源 */
    private static boolean isGenericKill(DamageSource source) {
        return "genericKill".equals(source.getMsgId());
    }
}
