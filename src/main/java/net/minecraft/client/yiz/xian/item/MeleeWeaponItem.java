package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * 近战武器 — 继承 {@link SwordItem} 获得原版剑属性（伤害/攻速/附魔/挖掘速度等），无耐久。
 *
 * <p>构造时传入攻击伤害和攻击速度，自动注册为原版剑属性修饰符，
 * 使武器在伤害计算、攻击冷却、附魔兼容等方面与普通剑一致。
 */
public class MeleeWeaponItem extends SwordItem implements IWeaponItem, ISkillWeapon {

    private final double attackDamage;
    private final double attackSpeed;

    /** 无耐久 Tier：maxUses=0 = 不显示耐久条，无损坏。 */
    private static final Tier NO_DURABILITY_TIER = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 0f; }
        @Override public float getAttackDamageBonus() { return 0f; }
        @Override public int getEnchantmentValue() { return 22; }          // 金剑级附魔
        @Override public @NotNull TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_STONE_TOOL; }
        @Override public @NotNull Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    /**
     * @param properties   物品属性（不可堆叠、稀有度等）
     * @param attackDamage 攻击伤害（最终值，如 8.5、28.0）
     * @param attackSpeed  攻击速度（最终值，如 1.6 = 原版剑；1.2 = 较慢）
     */
    public MeleeWeaponItem(Properties properties, double attackDamage, double attackSpeed) {
        super(NO_DURABILITY_TIER, properties.attributes(
            SwordItem.createAttributes(
                NO_DURABILITY_TIER,
                (int)(attackDamage - 1.0),       // 减去空手基础伤害 1.0
                (float)(attackSpeed - 4.0)       // 减去空手基础速度 4.0 = 修饰值（如 -2.4）
            )));
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
    }

    // ── 无耐久 ──

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 0;
    }

    /** 禁止铁砧或经验修补等方式修复。 */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    // ── 属性查询 ──

    public double getAttackDamage() { return attackDamage; }
    public double getAttackSpeed() { return attackSpeed; }

    // ── ISkillWeapon（MELEE 类型，与之前 WeaponType.MELEE 等效）──

    @Override
    public ISkillWeapon.SkillType getSkillType() {
        return ISkillWeapon.SkillType.MELEE;
    }

    @Override
    public double getAttackDamage(ItemStack stack) {
        return attackDamage;
    }

    @Override
    public double getAttackSpeed(ItemStack stack) {
        return attackSpeed;
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
