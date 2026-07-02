package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.resources.ResourceLocation;

/**
 * 主动法术武器 — 右键施放法术，通常有冷却或消耗。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现施法逻辑。</p>
 */
public class ActiveSpellWeaponItem extends WeaponItem {

    /**
     * 旧构造器（向后兼容）。
     * @deprecated 新武器应使用 {@link #ActiveSpellWeaponItem(Properties, ResourceLocation, WeaponProfile, int)}
     */
    @Deprecated
    public ActiveSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.ACTIVE_SPELL);
    }

    /** 新构造器 — 关联 WeaponProfile。 */
    public ActiveSpellWeaponItem(Properties properties, ResourceLocation weaponId,
                                 WeaponProfile profile, int level) {
        super(properties, WeaponType.ACTIVE_SPELL, weaponId, profile, level);
    }
}
