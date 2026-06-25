package net.minecraft.client.yiz.xian.item;

/**
 * 近战武器 — 刀、剑、斧等物理攻击。
 *
 * <p>右击无特殊效果（可由子类覆盖 {@link #onWeaponUse}），
 * 主要通过原版攻击流程造成伤害。</p>
 */
public class MeleeWeaponItem extends WeaponItem {

    public MeleeWeaponItem(Properties properties) {
        super(properties, WeaponType.MELEE);
    }
}
