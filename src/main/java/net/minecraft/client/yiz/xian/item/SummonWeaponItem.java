package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 召唤武器 — 召唤仆从、飞剑、灵体等协助作战。
 *
 * <h3>统一攻击入口</h3>
 * <p>所有继承本类的召唤武器，其攻击逻辑都应通过 {@link #onSummonAttack(Player, LivingEntity, ItemStack)}
 * 统一处理。外部只需判断 {@code instanceof SummonWeaponItem} 即可调用，无需关心具体子类。</p>
 *
 * <h3>独立破无敌帧</h3>
 * <p>提供 {@link #pierceInvulnerabilityHit} 和 {@link #trueDamageHit} 两个快捷方法，
 * 封装前置库 {@link YizModQZKAPI} 的对应能力。
 * 攻击只影响自身无敌帧，不会令其他伤害源也破除无敌帧。</p>
 */
public class SummonWeaponItem extends WeaponItem {

    public SummonWeaponItem(Properties properties) {
        super(properties, WeaponType.SUMMON);
    }

    // ══════════════════════════════════════════════════════════
    //  子类覆盖钩子
    // ══════════════════════════════════════════════════════════

    /**
     * 召唤武器统一攻击入口。
     *
     * <p>子类应覆盖此方法实现具体的召唤物攻击逻辑（飞剑、灵体等）。
     * 默认返回 false 表示未处理，由子类决定是否覆盖。</p>
     */
    public boolean onSummonAttack(Player attacker, LivingEntity target, ItemStack weapon) {
        return false;
    }

    public int getSummonCount(ItemStack weapon) { return 0; }
    public int getMaxSummonCount(ItemStack weapon) { return 0; }
    public void increaseCount(ItemStack weapon) { }
    public void decreaseCount(ItemStack weapon) { }

    // ══════════════════════════════════════════════════════════
    //  快捷伤害方法（封装前置库 API）
    // ══════════════════════════════════════════════════════════

    /**
     * 独立破无敌帧伤害。
     *
     * <p>临时清零目标无敌帧 → 应用伤害 → 恢复无敌帧。
     * 攻击只影响自身，不会令其他伤害源也破除无敌帧。</p>
     *
     * @param target 目标
     * @param amount 伤害值
     * @param source 伤害来源
     */
    protected void pierceInvulnerabilityHit(LivingEntity target, float amount, Entity source) {
        YizModQZKAPI.pierceInvulnerabilityDamage(target, amount, source);
    }

    /**
     * 真实伤害（直接 setHealth，无视护甲、无敌帧、一切减免）。
     *
     * @param target 目标
     * @param amount 伤害值
     * @param source 伤害来源
     */
    protected void trueDamageHit(LivingEntity target, float amount, Entity source) {
        YizModQZKAPI.trueDamage(target, amount, source);
    }

    /**
     * 破甲 + 独立破无敌帧伤害。
     *
     * <p>跳过护甲减伤，同时独立破除无敌帧。</p>
     */
    protected void armorPiercingPierceInvulnerabilityHit(LivingEntity target, float amount, Entity source) {
        YizModQZKAPI.armorPiercingAndPierceInvulnerabilityDamage(target, amount, source);
    }
}
