package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
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
 * 满足条件则直接调用 {@code startFallFlying()} 启动滑翔。
 *
 * <p>不依赖 {@code INVOKE_ASSIGN} 或 {@code @ModifyVariable}——
 * 在 {@code aiStep} 最开始就判断，手动复制原版的双跳检测逻辑。</p>
 *
 * <p>飞行一旦启动，由 {@link MixinElytraFromAccessory} 的 {@code updateFallFlying}
 * {@code @ModifyArg} 维持飞行标志，不会被关掉。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerElytra {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void yizxian$startElytraFromAccessory(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        // 复制原版 elytra 判断条件（1.21.1 的 aiStep 中的精确条件）
        if (self.onGround()) return;
        if (self.isFallFlying()) return;
        if (self.isInWater()) return;
        if (self.isPassenger()) return;
        if (self.hasEffect(MobEffects.LEVITATION)) return;
        // 必须按了跳跃键（双跳触发鞘翅）
        if (!self.input.jumping) return;

        // 原版已能处理（胸甲槽直接有鞘翅）→ 不干预
        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA)) return;

        // 饰品槽有鞘翅 → 手动启动飞行
        AccessoryContainer container = AccessoryContainer.get(self);
        for (int i = 0; i < container.getSlotCount(); i++) {
            if (container.getItem(i).is(Items.ELYTRA)) {
                YizxianMod.LOGGER.info("[ElytraDiag] Starting flight from accessory slot {}", i);
                self.startFallFlying();
                self.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                    self, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                return;
            }
        }
    }
}
