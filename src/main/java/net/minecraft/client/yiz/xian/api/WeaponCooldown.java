package net.minecraft.client.yiz.xian.api;

import net.minecraft.world.entity.player.Player;

/**
 * ILeftHandRender 武器攻击冷却公开查询。
 *
 * <p>内部由 {@code WeaponAnimMixin} 焊死在 1.2 秒并二值化。
 */
public final class WeaponCooldown {

    private WeaponCooldown() {}

    /** 攻击冷却 tick 数（24 = 1.2 秒 @20tps）。 */
    public static final int TICKS = 24;

    /** 冷却是否已满（可以攻击）。非 ILeftHandRender 武器永远返回 true。 */
    public static boolean isFull(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof ILeftHandRender)) return true;
        return player.getAttackStrengthScale(0f) >= 1.0f;
    }
}
