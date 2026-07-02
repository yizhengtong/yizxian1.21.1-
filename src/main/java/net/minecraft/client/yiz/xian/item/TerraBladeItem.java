package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.MeleeWeaponScaling;
import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.resources.ResourceLocation;

/**
 * 泰拉刃 — 近战剑，5 级品质（平凡→传说），无耐久。
 * <p>使用 {@link MeleeWeaponScaling} 标准化倍率自动生成面板。
 * Tooltip 由 {@link MeleeWeaponItem} 统一渲染。</p>
 */
public class TerraBladeItem extends MeleeWeaponItem implements ILeftHandRender {

    static final ResourceLocation WEAPON_ID =
        ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID, "terra_blade");
    static final WeaponProfile PROFILE = buildDefault();

    public TerraBladeItem(int level) {
        super(new Properties().fireResistant(), WEAPON_ID, PROFILE, level);
    }

    /** 标准化 Profile：Lv1 基础面板 → 自动按倍率生成全部 5 级。 */
    public static WeaponProfile buildDefault() {
        return MeleeWeaponScaling.buildProfile(WEAPON_ID,
            new MeleeWeaponScaling.BaseStats(
                17.5,  // 攻击力 Lv1
                1.4,   // 攻击速度 Lv1
                25,    // 暴击率 Lv1
                20,    // 暴伤 Lv1
                10,    // 吸血 Lv1
                6,     // 溅射半径 Lv1
                50,    // 溅射伤害 Lv1
                30,    // 溅射衰减 Lv1
                4.2    // 实体交互距离 Lv1
            ));
    }
}
