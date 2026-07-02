package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.client.yiz.api.IWeaponAbility;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.client.yiz.api.IRenderConfig;
import net.minecraft.client.yiz.weapon.WeaponLevelData;
import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;

import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.List;

/**
 * 近战武器 — 继承 {@link SwordItem} 获得原版剑属性（伤害/攻速/附魔/挖掘速度等），无耐久。
 *
 * <p>构造时从 {@link WeaponProfile} 读取攻击伤害和攻击速度，
 * 自动注册为原版剑属性修饰符。</p>
 */
public class MeleeWeaponItem extends SwordItem
        implements IWeaponItem, ISkillWeapon, IRenderConfig, IWeaponAbility.Host {

    private final ResourceLocation weaponId;
    private final WeaponProfile profile;
    private final int level;
    private List<IWeaponAbility> abilities = List.of();

    /** 无耐久 Tier：极大 uses 避免耐久条显示。999999 避免溢出。 */
    private static final Tier NO_DURABILITY_TIER = new Tier() {
        @Override public int getUses() { return 999999; }
        @Override public float getSpeed() { return 0f; }
        @Override public float getAttackDamageBonus() { return 0f; }
        @Override public int getEnchantmentValue() { return 22; }
        @Override public @NotNull TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_STONE_TOOL; }
        @Override public @NotNull Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    /**
     * 旧构造器（向后兼容，不使用 WeaponProfile 的简单近战武器）。
     * @deprecated 新武器应使用 {@link #MeleeWeaponItem(Properties, ResourceLocation, WeaponProfile, int)}
     */
    @Deprecated
    public MeleeWeaponItem(Properties properties, double attackDamage, double attackSpeed) {
        super(NO_DURABILITY_TIER, properties.attributes(
            SwordItem.createAttributes(
                NO_DURABILITY_TIER,
                (int)(attackDamage - 1.0),
                (float)(attackSpeed - 4.0)
            )));
        this.weaponId = null;
        this.profile = null;
        this.level = 1;
    }

    /**
     * 新构造器 — 从 WeaponProfile 读取伤害/攻速/品质信息。
     *
     * @param properties 物品属性（可预先设置 fireResistant 等，构造器内部会叠加 rarity 和 attributes）
     * @param weaponId   武器注册 ID
     * @param profile    武器全等级 Profile
     * @param level      当前品质等级（1-based）
     */
    public MeleeWeaponItem(Properties properties, ResourceLocation weaponId,
                           WeaponProfile profile, int level) {
        super(NO_DURABILITY_TIER, buildProperties(properties, profile, level));
        this.weaponId = weaponId;
        this.profile = profile;
        this.level = level;
    }

    /** 从 Profile 提取属性构建 Properties（供 super() 调用）。不包含横扫之刃。 */
    private static Properties buildProperties(Properties props, WeaponProfile profile, int level) {
        WeaponLevelData data = profile.forLevel(level);
        var builder = ItemAttributeModifiers.builder()
            .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                    data.stats().damage() - 1.0,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                    data.stats().speed() - 4.0,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);

        // 注：攻击距离不通过 ItemAttributeModifiers（ENTITY_INTERACTION_RANGE 不接受物品属性修饰符），
        // 改为 Mixin 注入 Player.entityInteractionRange() 直接覆写返回值。

        var attrs = builder.build();
        return props
            .stacksTo(1)
            .rarity(profile.tier(level).rarity())
            .attributes(attrs);
    }

    // ── 访问器 ──

    public ResourceLocation getWeaponId()    { return weaponId; }
    public WeaponProfile getWeaponProfile()  { return profile; }
    public int getLevel()                    { return level; }

    @Nullable
    public WeaponLevelData getLevelData() {
        return profile != null ? profile.forLevel(level) : null;
    }

    // ── 旧版兼容 getter ──

    /** @deprecated 使用 {@link #getLevelData()}.stats().damage() */
    @Deprecated
    public double getAttackDamage() {
        WeaponLevelData data = getLevelData();
        return data != null ? data.stats().damage() : 0;
    }

    /** @deprecated 使用 {@link #getLevelData()}.stats().speed() */
    @Deprecated
    public double getAttackSpeed() {
        WeaponLevelData data = getLevelData();
        return data != null ? data.stats().speed() : 1.0;
    }

    // ── IWeaponAbility.Host ──

    @Override
    public void yizweapon$setAbilities(List<IWeaponAbility> abilities) {
        this.abilities = List.copyOf(abilities);
    }

    protected List<IWeaponAbility> getAbilities() {
        return abilities;
    }

    // ── Tooltip：展示所有自定义属性 ──

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                 List<Component> tooltip, TooltipFlag flag) {
        WeaponLevelData d = getLevelData();
        if (d == null) return;
        tooltip.add(Component.literal(
            "§7" + d.tier().prefix()));
        tooltip.add(Component.literal(
            "§9伤害 §f" + String.format("%.1f", d.stats().damage()) + "  §7(近战)"));
        tooltip.add(Component.literal(
            "§9攻击速度 §f" + String.format("%.2f", d.stats().speed())));
        double cr = d.stats().critRate();
        if (cr > 0) tooltip.add(Component.literal(
            "§9暴击率 §f" + String.format("%.0f%%", cr)));
        double cd = d.getExtra("critDmg");
        if (cd > 0) tooltip.add(Component.literal(
            "§9暴伤 §f+" + String.format("%.0f%%", cd)));
        double ls = d.getExtra("lifeSteal");
        if (ls > 0) tooltip.add(Component.literal(
            "§9吸血 §f" + String.format("%.0f%%", ls)));
        double sr = d.getExtra("splashRadius");
        double sd = d.getExtra("splashDmg");
        double sf = d.getExtra("splashFalloff");
        if (sr > 0) tooltip.add(Component.literal(
            "§9伤害半径 §f" + String.format("%.1f", sr)));
        if (sd > 0) tooltip.add(Component.literal(
            "§9溅射伤害 §f" + String.format("%.0f%%", sd)));
        if (sf > 0) tooltip.add(Component.literal(
            "§9溅射衰减 §f" + String.format("%.0f%%", sf)));
    }

    // ── 无耐久 ──

    @Override public boolean isBarVisible(ItemStack stack) { return false; }
    @Override public boolean isDamageable(ItemStack stack) { return false; }
    @Override public int getMaxDamage(ItemStack stack) { return 0; }
    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return true; }
    @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 无耐久武器：不调用 hurtAndBreak
    }

    // ── ISkillWeapon（MELEE） ──

    @Override
    public ISkillWeapon.SkillType getSkillType() {
        return ISkillWeapon.SkillType.MELEE;
    }

    @Override
    public double getAttackDamage(ItemStack stack) {
        WeaponLevelData data = getLevelData();
        return data != null ? data.stats().damage() : 0;
    }

    @Override
    public double getAttackSpeed(ItemStack stack) {
        WeaponLevelData data = getLevelData();
        return data != null ? data.stats().speed() : 1.0;
    }

    @Override
    public boolean onWeaponUse(ItemStack stack) {
        return false;
    }

    @Override
    public boolean onWeaponShiftUse(ItemStack stack) {
        return false;
    }

    // ── IRenderConfig（从 Profile 读取光效） ──

    @Override
    @Nullable
    public Vector4f getGlowColor(ItemStack stack) {
        if (profile == null) return null;
        return profile.tier(level).glowColor();
    }

    @Override
    public int getGlowType(ItemStack stack) {
        if (profile == null) return 0;
        return profile.tier(level).glowType();
    }

    @Override
    public int getRenderLevel(ItemStack stack) {
        return level;
    }

    @Override
    public int getQualityColor(ItemStack stack) {
        return 0xFFFFFF;
    }
}
