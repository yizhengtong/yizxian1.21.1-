package net.minecraft.client.yiz.xian.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * 简单有耐久剑 — 昭明法杖式模型（item JSON + 贴图）的近战武器。
 * 不走 WeaponProfile 等级体系，构造参数直接给攻击力/攻速/耐久。
 * <pre>
 *   玩家最终攻击力 = attackDamage（createAttributes 换算 -1）
 *   玩家最终攻速   = attackSpeed（createAttributes 换算 -4）
 *   耐久           = Tier.getUses()（TieredItem 构造自动套用）
 * </pre>
 */
public class SimpleBladeItem extends SwordItem {

    private final int attackDamage;
    private final float attackSpeed;

    public SimpleBladeItem(Properties props, int attackDamage, float attackSpeed, int durability) {
        this(tier(durability), props, attackDamage, attackSpeed);
    }

    private SimpleBladeItem(Tier tier, Properties props, int attackDamage, float attackSpeed) {
        super(tier, props.attributes(
            SwordItem.createAttributes(tier, attackDamage - 1, attackSpeed - 4)));
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
    }

    /** 耐久 Tier：uses = 指定耐久，无附加伤害/速度（攻击属性由 createAttributes 直给）。 */
    private static Tier tier(int durability) {
        return new Tier() {
            @Override public int getUses() { return durability; }
            @Override public float getSpeed() { return 0f; }
            @Override public float getAttackDamageBonus() { return 0f; }
            @Override public int getEnchantmentValue() { return 22; }
            @Override public @NotNull TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_STONE_TOOL; }
            @Override public @NotNull Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        };
    }

    public int getBladeDamage() { return attackDamage; }
    public float getBladeSpeed() { return attackSpeed; }
}
