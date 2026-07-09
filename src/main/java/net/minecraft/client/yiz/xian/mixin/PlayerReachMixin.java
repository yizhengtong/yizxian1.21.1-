package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.client.yiz.xian.item.WeaponReachHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 覆写 entityInteractionRange() 返回自定义武器攻击距离。
 * 具体逻辑委托给 {@link WeaponReachHelper}，避免 Mixin 类直接引用 mod 物品类引发类加载死锁。
 *
 * <p>锁定系统的距离延伸作为标准 AttributeModifier 挂载在
 * ENTITY_INTERACTION_RANGE 上，自动计入 cir.getReturnValue()。</p>
 */
@Mixin(Player.class)
public abstract class PlayerReachMixin {

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void yizxian$weaponReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        double weaponReach = WeaponReachHelper.getWeaponReach(self.getMainHandItem());
        if (weaponReach <= 0) return; // 非近战武器：保留原版返回值（含所有修饰符），不干预

        double base = cir.getReturnValue();          // vanilla + 所有修饰符（含锁定加成）
        cir.setReturnValue(Math.max(base, weaponReach));
    }
}
