package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.render.TerraprismaRenderHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回复拦截 Mixin — 拦截 {@link LivingEntity#heal(float)}。
 * 禁疗实体 → 取消回血。
 */
@Mixin(LivingEntity.class)
public class HealMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(amount > 0)) return;

        // 禁疗检查（所有实体）
        if (TerraprismaRenderHandler.isAntiHealed(self.getUUID())) {
            ci.cancel();
        }
    }
}