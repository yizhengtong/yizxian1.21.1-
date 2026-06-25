package net.minecraft.client.yiz.xian.item;

/**
 * 主动法术武器 — 右键施放法术，通常有冷却或消耗。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现施法逻辑。</p>
 */
public class ActiveSpellWeaponItem extends WeaponItem {

    public ActiveSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.ACTIVE_SPELL);
    }
}
