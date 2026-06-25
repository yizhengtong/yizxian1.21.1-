package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 武器物品抽象基类。
 *
 * <p>统一所有武器的共有行为，并提供武器类型枚举供下游逻辑分发。
 * 实现 {@link IWeaponItem} 以自动归入「武器装备」创造标签页，
 * 实现 {@link ISkillWeapon} 以允许放入技能施法槽位。</p>
 */
public abstract class WeaponItem extends Item implements IWeaponItem, ISkillWeapon {

    /** 武器类型枚举（向后兼容） */
    public enum WeaponType {
        MELEE, ACTIVE_SPELL, PASSIVE_SPELL, SUPPORT_SPELL, SUMMON
    }

    private final WeaponType weaponType;

    protected WeaponItem(Properties properties, WeaponType weaponType) {
        super(properties);
        this.weaponType = weaponType;
    }

    /** 返回此武器的类型（向后兼容） */
    public WeaponType getWeaponType() {
        return weaponType;
    }

    /** 返回此武器是否为指定类型 */
    public boolean isType(WeaponType type) {
        return this.weaponType == type;
    }

    // ── ISkillWeapon 实现 ──

    @Override
    public ISkillWeapon.SkillType getSkillType() {
        return switch (weaponType) {
            case MELEE         -> ISkillWeapon.SkillType.MELEE;
            case ACTIVE_SPELL  -> ISkillWeapon.SkillType.ACTIVE_SPELL;
            case PASSIVE_SPELL -> ISkillWeapon.SkillType.PASSIVE_SPELL;
            case SUPPORT_SPELL -> ISkillWeapon.SkillType.SUPPORT_SPELL;
            case SUMMON        -> ISkillWeapon.SkillType.SUMMON;
        };
    }

    // ── 子类可覆盖的钩子 ──

    @Override
    public double getAttackDamage(ItemStack stack) {
        return 0;
    }

    @Override
    public double getAttackSpeed(ItemStack stack) {
        return 1.0;
    }

    @Override
    public boolean onWeaponUse(ItemStack stack) {
        return false;
    }

    @Override
    public boolean onWeaponShiftUse(ItemStack stack) {
        return false;
    }
}
