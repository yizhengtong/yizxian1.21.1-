package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.resources.ResourceLocation;

/**
 * 被动法术武器 — 常驻增益效果，持有/穿戴时生效。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现切换/开关逻辑。</p>
 */
public class PassiveSpellWeaponItem extends WeaponItem {

    /**
     * 旧构造器（向后兼容）。
     * @deprecated 新武器应使用 {@link #PassiveSpellWeaponItem(Properties, ResourceLocation, WeaponProfile, int)}
     */
    @Deprecated
    public PassiveSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.PASSIVE_SPELL);
    }

    /** 新构造器 — 关联 WeaponProfile。 */
    public PassiveSpellWeaponItem(Properties properties, ResourceLocation weaponId,
                                  WeaponProfile profile, int level) {
        super(properties, WeaponType.PASSIVE_SPELL, weaponId, profile, level);
    }
}
