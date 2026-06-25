package net.minecraft.client.yiz.xian.item;

/**
 * 辅助法术武器 — 治疗、护盾、净化、增益等支援型法术。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现辅助逻辑。</p>
 */
public class SupportSpellWeaponItem extends WeaponItem {

    public SupportSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.SUPPORT_SPELL);
    }
}
