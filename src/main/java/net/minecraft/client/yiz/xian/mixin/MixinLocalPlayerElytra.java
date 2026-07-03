package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端侧：在 {@code LocalPlayer.aiStep()} 头部检测饰品槽中的鞘翅，
 * 满足条件则自动展开滑翔（原版风格，无需按跳跃键）。
 *
 * <p>飞行一旦启动，由 {@link MixinElytraFromAccessory} 的 {@code updateFallFlying}
 * {@code @ModifyArg} 维持飞行标志，不会被关掉。</p>
 *
 * <p>能力判定走统一入口 {@link AccessoryContainer#hasHeartWings}（客户端查 _c）。
 * 不再有 fallback refreshFromSync —— 客户端 _c 是只读镜像，由 SyncAccessoryPayload 填充。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerElytra {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void yizxian$startElytraFromAccessory(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        // 原版鞘翅展开条件：离地、未在飞行、不在水中、未骑乘、无漂浮
        if (self.onGround()) return;
        if (self.isFallFlying()) return;
        if (self.isInWater()) return;
        if (self.isPassenger()) return;
        if (self.hasEffect(MobEffects.LEVITATION)) return;
        // 原版风格：按下跳跃键才展开（防走路/落地反弹误触发）
        if (!self.input.jumping) return;

        // 原版已能处理（胸甲槽直接有鞘翅）→ 不干预
        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA) || chest.getItem() instanceof HeartWingsItem) return;

        // 饰品槽有鞘翅 → 原版风格（按跳跃键展开）
        if (AccessoryContainer.hasHeartWings(self)) {
            self.startFallFlying();
            self.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                self, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }
}
