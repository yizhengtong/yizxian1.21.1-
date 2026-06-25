package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 持有 ILeftHandRender 武器攻击时推进连招状态机（服务端）。
 *
 * <p>挥砍渲染由客户端 {@link ClientAttackMixin} + {@link net.minecraft.client.yiz.xian.render.WeaponSwingRenderer} 处理。</p>
 */
@Mixin(Player.class)
public abstract class WeaponAnimMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void yizxian_onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;

        ItemStack held = self.getMainHandItem();
        if (held.getItem() instanceof ILeftHandRender) {
            ComboStateMachine.onAttack(self);
        }
    }
}
