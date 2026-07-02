package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.MeleeWeaponScaling;
import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.resources.ResourceLocation;

/**
 * 村正 — 近战剑，5 级品质（平凡→传说），无耐久。
 * <p>使用 {@link MeleeWeaponScaling} 标准化倍率自动生成面板。
 * Tooltip 由 {@link MeleeWeaponItem} 统一渲染。</p>
 */
public class MuramasaItem extends MeleeWeaponItem implements ILeftHandRender {

    static final ResourceLocation WEAPON_ID =
        ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID, "muramasa");
    static final WeaponProfile PROFILE = buildDefault();

    public MuramasaItem(int level) {
        super(new Properties(), WEAPON_ID, PROFILE, level);
    }

    /** 标准化 Profile：Lv1 基础面板 → 自定义攻速倍率序列。 */
    public static WeaponProfile buildDefault() {
        return MeleeWeaponScaling.buildProfileWithSpeeds(WEAPON_ID,
            new MeleeWeaponScaling.BaseStats(
                5.0,   // 攻击力 Lv1
                1.8,   // 攻击速度 Lv1
                12,    // 暴击率 Lv1
                15,    // 暴伤 Lv1
                6,     // 吸血 Lv1
                2.5,   // 溅射半径 Lv1
                30,    // 溅射伤害 Lv1
                20,    // 溅射衰减 Lv1
                4.0    // 实体交互距离 Lv1
            ),
            new double[]{1.8, 1.9, 2.0, 2.1, 2.2}  // 自定义攻速序列
        );
    }
}
