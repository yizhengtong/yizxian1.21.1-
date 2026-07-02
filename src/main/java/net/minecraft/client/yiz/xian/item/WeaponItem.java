package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.IWeaponAbility;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.client.yiz.api.IRenderConfig;
import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.client.yiz.weapon.WeaponLevelData;
import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器物品抽象基类。
 *
 * <p>统一所有武器的共有行为，并提供武器类型枚举供下游逻辑分发。
 * 实现 {@link IWeaponItem} 以自动归入「武器装备」创造标签页，
 * 实现 {@link ISkillWeapon} 以允许放入技能施法槽位，
 * 实现 {@link IRenderConfig} 以自提供渲染配置，
 * 实现 {@link IWeaponAbility.Host} 以接收 Ability 注入。</p>
 */
public abstract class WeaponItem extends Item
        implements IWeaponItem, ISkillWeapon, IRenderConfig, IWeaponAbility.Host {

    /** 武器类型枚举（向后兼容） */
    public enum WeaponType {
        MELEE, ACTIVE_SPELL, PASSIVE_SPELL, SUPPORT_SPELL, SUMMON
    }

    private final WeaponType weaponType;
    private final ResourceLocation weaponId;
    private final WeaponProfile profile;
    private final int level;
    private List<IWeaponAbility> abilities = List.of();

    /**
     * 旧构造器（向后兼容，不使用 WeaponProfile 的简单武器）。
     * @deprecated 新武器应使用 {@link #WeaponItem(Properties, WeaponType, ResourceLocation, WeaponProfile, int)}
     */
    @Deprecated
    protected WeaponItem(Properties properties, WeaponType weaponType) {
        super(properties);
        this.weaponType = weaponType;
        this.weaponId = null;
        this.profile = null;
        this.level = 1;
    }

    /**
     * 新构造器 — 关联 WeaponProfile 获取品质/属性/光效。
     *
     * @param properties 物品属性（可预先设置 stacksTo/rarity 等，构造器内部会叠加上 rarity）
     * @param weaponType 武器类型
     * @param weaponId   武器注册 ID（用于 JSON 覆写查询）
     * @param profile    武器全等级 Profile
     * @param level      当前品质等级（1-based）
     */
    protected WeaponItem(Properties properties, WeaponType weaponType,
                         ResourceLocation weaponId, WeaponProfile profile, int level) {
        super(properties
            .stacksTo(1)
            .rarity(profile.tier(level).rarity()));
        this.weaponType = weaponType;
        this.weaponId = weaponId;
        this.profile = profile;
        this.level = level;
    }

    // ── 访问器 ──

    public WeaponType getWeaponType()        { return weaponType; }
    public ResourceLocation getWeaponId()    { return weaponId; }
    public WeaponProfile getWeaponProfile()  { return profile; }
    public int getLevel()                    { return level; }

    /** 获取当前等级的品质+属性+额外参数捆绑包。profile 为 null 时返回 null。 */
    @Nullable
    public WeaponLevelData getLevelData() {
        return profile != null ? profile.forLevel(level) : null;
    }

    /** 返回此武器是否为指定类型 */
    public final boolean isType(WeaponType type) {
        return this.weaponType == type;
    }

    // ── IWeaponAbility.Host ──

    @Override
    public void yizweapon$setAbilities(List<IWeaponAbility> abilities) {
        this.abilities = List.copyOf(abilities);
    }

    /** 子类可调用以获取已注入的 Ability 列表。 */
    protected List<IWeaponAbility> getAbilities() {
        return abilities;
    }

    // ── IWeaponAbility 分发（供子类覆写或直接调用） ──

    /** 向所有已注入 Ability 分发 onEntityHit。子类可覆写追加逻辑。 */
    protected void dispatchEntityHit(net.minecraft.world.entity.player.Player attacker,
                                     net.minecraft.world.entity.LivingEntity target,
                                     ItemStack stack) {
        for (IWeaponAbility a : abilities) a.onEntityHit(attacker, target, stack);
    }

    /** 向所有已注入 Ability 分发 onWeaponUse。子类可覆写追加逻辑。 */
    protected void dispatchWeaponUse(net.minecraft.world.level.Level level,
                                     net.minecraft.world.entity.player.Player player,
                                     net.minecraft.world.InteractionHand hand,
                                     ItemStack stack) {
        for (IWeaponAbility a : abilities) a.onWeaponUse(level, player, hand, stack);
    }

    /** 向所有已注入 Ability 分发 onSummonAttack。子类可覆写追加逻辑。 */
    protected void dispatchSummonAttack(net.minecraft.world.entity.player.Player attacker,
                                        net.minecraft.world.entity.LivingEntity target,
                                        ItemStack stack) {
        for (IWeaponAbility a : abilities) a.onSummonAttack(attacker, target, stack);
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

    // ── ISkillWeapon 实现 ──

    @Override
    public ISkillWeapon.SkillType getSkillType() {
        return switch (weaponType) {
            case MELEE         -> ISkillWeapon.SkillType.MELEE;
            case ACTIVE_SPELL  -> ISkillWeapon.SkillType.ACTIVE_SPELL;
            case PASSIVE_SPELL -> ISkillWeapon.SkillType.PASSIVE_SPELL;
            case SUPPORT_SPELL -> ISkillWeapon.SkillType.SUPPORT_SPELL;
            case SUMMON        -> ISkillWeapon.SkillType.SUMMON;
        };
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
}
