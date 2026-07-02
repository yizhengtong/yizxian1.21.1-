package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端侧：处理 START_FALL_FLYING 数据包时，检查饰品槽有无鞘翅。
 *
 * <p>三剑客分工：</p>
 * <ul>
 *   <li>{@link MixinLocalPlayerElytra} — 客户端：按空格时从饰品槽启动飞行（发送数据包）</li>
 *   <li><b>本 Mixin</b> — 服务端：接收 START_FALL_FLYING 数据包后，确认饰品槽有鞘翅允许飞行</li>
 *   <li>{@link MixinElytraFromAccessory} — 双端：维持飞行标志不被 updateFallFlying 关掉</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class MixinPlayerAccessoryElytra {

    /**
     * 拦截 tryToStartFallFlying 的返回。若原版返回 false（胸甲槽无鞘翅），
     * 检查饰品槽，有鞘翅则主动调用 startFallFlying 并返回 true。
     */
    @Inject(method = "tryToStartFallFlying", at = @At("TAIL"), cancellable = true)
    private void yizxian_startElytraFromAccessory(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 原版已成功

        Player self = (Player) (Object) this;
        if (self.isInWater() || self.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION))
            return;

        AccessoryContainer container = AccessoryContainer.get(self);
        for (int i = 0; i < container.getSlotCount(); i++) {
            if (container.getItem(i).is(Items.ELYTRA)) {
                self.startFallFlying();
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
