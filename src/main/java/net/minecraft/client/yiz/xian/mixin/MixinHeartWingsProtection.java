package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 心之翅特殊效果：
 * <ol>
 *   <li>免疫衰落伤害</li>
 *   <li>免疫鞘翅碰撞动能伤害</li>
 *   <li>起飞时自动获得一次推进加速</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public class MixinHeartWingsProtection {

    /** 检查玩家饰品槽是否有心之翅 */
    private static boolean hasHeartWings(Player player) {
        AccessoryContainer c = AccessoryContainer.get(player);
        for (int i = 0; i < c.getSlotCount(); i++) {
            if (c.getItem(i).getItem() instanceof HeartWingsItem) return true;
        }
        return false;
    }

    // ── 1) 免疫衰落伤害 ──

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void yizxian_cancelFallDamage(float fallDistance, float multiplier, DamageSource source,
                                          CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && hasHeartWings(player)) {
            cir.setReturnValue(false);
        }
    }

    // ── 2) 免疫鞘翅碰撞动能伤害 ──

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizxian_cancelKineticDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && hasHeartWings(player)) {
            if (source.is(DamageTypes.FLY_INTO_WALL)) {
                cir.setReturnValue(false);
            }
        }
    }

    // ── 3) 起飞推进见 MixinLocalPlayerElytra ──
}
