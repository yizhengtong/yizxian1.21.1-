package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.resources.ResourceLocation;

/**
 * 辅助法术武器 — 治疗、护盾、净化、增益等支援型法术。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现辅助逻辑。</p>
 */
public class SupportSpellWeaponItem extends WeaponItem {

    /**
     * 旧构造器（向后兼容）。
     * @deprecated 新武器应使用 {@link #SupportSpellWeaponItem(Properties, ResourceLocation, WeaponProfile, int)}
     */
    @Deprecated
    public SupportSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.SUPPORT_SPELL);
    }

    /** 新构造器 — 关联 WeaponProfile。 */
    public SupportSpellWeaponItem(Properties properties, ResourceLocation weaponId,
                                  WeaponProfile profile, int level) {
        super(properties, WeaponType.SUPPORT_SPELL, weaponId, profile, level);
    }
}
