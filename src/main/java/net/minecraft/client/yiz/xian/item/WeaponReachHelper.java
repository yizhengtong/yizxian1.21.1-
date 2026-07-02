package net.minecraft.client.yiz.xian.item;

import net.minecraft.world.item.ItemStack;

/**
 * 武器攻击距离辅助类。
 * 独立于 Mixin 之外，避免类加载死锁（Mixin 引用本类不会直接触发 MeleeWeaponItem 加载）。
 */
public final class WeaponReachHelper {

    private WeaponReachHelper() {}

    /**
     * 获取主手持近战武器的自定义攻击距离。不是近战武器或未设自定义距离时返回 0。
     */
    public static double getWeaponReach(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof MeleeWeaponItem weapon)) return 0;
        var data = weapon.getLevelData();
        return data != null ? data.getExtra("entityInteraction") : 0;
    }
}
