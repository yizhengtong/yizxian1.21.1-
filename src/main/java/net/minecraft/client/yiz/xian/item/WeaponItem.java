package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 武器物品抽象基类。
 *
 * <p>统一所有武器的共有行为，并提供武器类型枚举供下游逻辑分发。
 * 实现 {@link IWeaponItem} 以自动归入「武器装备」创造标签页。</p>
 */
public abstract class WeaponItem extends Item implements IWeaponItem {

    /** 武器类型枚举 */
    public enum WeaponType {
        /** 近战武器 — 刀、剑、斧等物理攻击 */
        MELEE,
        /** 主动法术武器 — 右键施放，有冷却/消耗 */
        ACTIVE_SPELL,
        /** 被动法术武器 — 常驻增益效果 */
        PASSIVE_SPELL,
        /** 辅助法术武器 — 治疗、护盾、净化等 */
        SUPPORT_SPELL,
        /** 召唤武器 — 召唤仆从/飞剑/灵体等 */
        SUMMON
    }

    private final WeaponType weaponType;

    protected WeaponItem(Properties properties, WeaponType weaponType) {
        super(properties);
        this.weaponType = weaponType;
    }

    /** 返回此武器的类型 */
    public WeaponType getWeaponType() {
        return weaponType;
    }

    /** 返回此武器是否为指定类型 */
    public boolean isType(WeaponType type) {
        return this.weaponType == type;
    }

    // ── 子类可覆盖的钩子 ──

    /** 获取武器的攻击伤害（基类默认 0，子类覆盖） */
    public double getAttackDamage(ItemStack stack) {
        return 0;
    }

    /** 获取武器的攻击速度（基类默认 1.0，子类覆盖） */
    public double getAttackSpeed(ItemStack stack) {
        return 1.0;
    }

    /** 武器主手交互（右键），子类覆盖 */
    public boolean onWeaponUse(ItemStack stack) {
        return false;
    }

    /** 武器潜行交互（Shift+右键），子类覆盖 */
    public boolean onWeaponShiftUse(ItemStack stack) {
        return false;
    }
}
