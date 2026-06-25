package net.minecraft.client.yiz.xian.item;

/**
 * 被动法术武器 — 常驻增益效果，持有/穿戴时生效。
 *
 * <p>子类应覆盖 {@link #onWeaponUse} 实现切换/开关逻辑。</p>
 */
public class PassiveSpellWeaponItem extends WeaponItem {

    public PassiveSpellWeaponItem(Properties properties) {
        super(properties, WeaponType.PASSIVE_SPELL);
    }
}
