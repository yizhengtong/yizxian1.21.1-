package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.item.WeaponReachHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 直接覆写 entityInteractionRange() 返回自定义武器攻击距离。
 * 具体逻辑委托给 {@link WeaponReachHelper}，避免 Mixin 类直接引用 mod 物品类引发类加载死锁。
 *
 * <h3>与「会心一击」攻击距离修饰符的协调</h3>
 * 会心一击（{@code CriticalStrikeEffect}）满充时会给玩家的 ENTITY_INTERACTION_RANGE
 * 挂一个 {@code yizmodqzk:entity_crit_range} 的 ADD_VALUE 修饰符（+15）。
 * 如果这里直接 {@code setReturnValue(weaponReach)}，会把这个修饰符一并丢弃，
 * 导致拿近战武器时会心的距离奖励失效。
 *
 * 因此当主手是近战武器时，最终距离 = {@code max(vanilla + crit, weaponReach) + crit}：
 * <ul>
 *   <li>{@code cir.getReturnValue()} 已是 vanilla 基础值 + 所有修饰符（含 crit）计算后的值；</li>
 *   <li>我们把它与武器固有距离取最大，再单独叠加 crit 的 +值，
 *       保证「拿近战武器」与「不拿近战武器」两种情况下 crit 奖励都不丢失。</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerReachMixin {

    /** 会心一击挂在 ENTITY_INTERACTION_RANGE 上的修饰符 id（与 ItemAttributeHandler.setEntityAttribute 的拼名规则一致）。 */
    private static final ResourceLocation CRIT_RANGE_ID =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "entity_crit_range");

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void yizxian$weaponReach(CallbackInfoReturnable<Double> cir) {
        Player self = (Player) (Object) this;
        double weaponReach = WeaponReachHelper.getWeaponReach(self.getMainHandItem());
        if (weaponReach <= 0) return; // 非近战武器：保留原版返回值（含 crit 修饰符），不干预

        double base = cir.getReturnValue();          // vanilla + 所有修饰符（含 crit）
        double crit = readCritBonus(self);           // crit 修饰符的原始 +值
        // 取「原版含 crit 距离」与「武器固有距离」的最大，再补一次 crit（保证武器距离也吃到 crit 加成）
        cir.setReturnValue(Math.max(base, weaponReach) + crit);
    }

    /** 读取玩家 ENTITY_INTERACTION_RANGE 上会心修饰符的值，不存在返回 0。 */
    private static double readCritBonus(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (inst == null) return 0.0;
        AttributeModifier mod = inst.getModifier(CRIT_RANGE_ID);
        return mod != null ? mod.amount() : 0.0;
    }
}
