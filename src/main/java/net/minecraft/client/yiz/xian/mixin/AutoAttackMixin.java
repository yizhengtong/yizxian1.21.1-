package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按住左键自动攻击：持有 ILeftHandRender 武器时，
 * 按住攻击键 + 冷却就绪 → 自动触发攻击。
 */
@Mixin(Minecraft.class)
public abstract class AutoAttackMixin {

    @Shadow public LocalPlayer player;
    @Shadow public abstract boolean startAttack();

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizxian_autoAttack(CallbackInfo ci) {
        if (player == null || player.isSpectator()) return;
        if (!(player.getMainHandItem().getItem() instanceof ILeftHandRender)) return;
        if (!Minecraft.getInstance().options.keyAttack.isDown()) return;
        if (player.getAttackStrengthScale(0f) < 1.0f) return;

        startAttack();
    }
}
