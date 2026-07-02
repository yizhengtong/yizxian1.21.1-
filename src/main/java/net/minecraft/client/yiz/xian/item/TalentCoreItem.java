package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.ITalentItem;
import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.client.yiz.xian.effect.SharpBladeEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TalentCoreItem extends Item implements ITalentItem {

    private static final String CONTAINED_KEY = "yizmodqzk:contained_effect";
    private static final String MAX_LEVEL_KEY = "yizmodqzk:max_level";

    public TalentCoreItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);

        ResourceLocation effectId = getContainedEffect(held);
        if (effectId == null) return InteractionResultHolder.fail(held);

        AbstractEffect effect = ModRegistries.getEffect(effectId).orElse(null);
        if (effect == null) return InteractionResultHolder.fail(held);

        int maxLevel = getContainedMaxLevel(held);
        boolean isEntity = effect.getPerceptionModes().stream()
            .anyMatch(m -> m instanceof EntityPerception);
        boolean shift = player.isShiftKeyDown();

        if (isEntity) {
            applyToPlayer(player, effectId, maxLevel, shift);
        } else {
            applyToOtherHand(player, hand, effectId, maxLevel, shift);
        }

        return InteractionResultHolder.success(held);
    }

    /** A1: 天赋 — 作用于玩家自身 */
    private void applyToPlayer(Player player, ResourceLocation effectId, int maxLevel, boolean shift) {
        if (shift) {
            int newLevel = TalentLevelTracker.decreaseLevel(player, effectId);
            if (newLevel <= 0) {
                UnlockManager.lock(player, effectId);
                // 天赋被完全移除时，必须清理其运行时状态。
                // 否则面板虽不显示，但 crit_timer / crit_target / entity_crit_range 修饰符会残留，
                // 导致「索敌 + 会心伤害 + 攻击距离加成」在移除后仍继续生效。
                // （AbstractEffect 基类没有 onRemoved 钩子，故在此显式按 effectId 分发清理。）
                clearRuntimeState(player, effectId);
            }
        } else {
            if (!UnlockManager.isUnlocked(player, effectId)) {
                UnlockManager.unlock(player, effectId);
            }
            TalentLevelTracker.increaseLevel(player, effectId, maxLevel);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            NetworkHandler.syncPlayerUnlocks(sp);
        }
    }

    /**
     * 天赋被移除时清理对应的运行时状态。
     * 目前只有会心一击（critical_strike）持有运行时数据（crit_timer / crit_target / entity_crit_range 修饰符），
     * 未来若新增其他带运行时状态的 EntityPerception 天赋，在此追加分支即可。
     */
    private void clearRuntimeState(Player player, ResourceLocation effectId) {
        if (net.minecraft.client.yiz.xian.effect.CriticalStrikeEffect.ID.equals(effectId)) {
            net.minecraft.client.yiz.xian.effect.CriticalStrikeEffect.reset(player);
            net.minecraft.client.yiz.xian.effect.CriticalStrikeProvider.reset(player);
        }
    }

    /** A2/A3: 词缀/随影 — 作用于另一只手的物品 */
    private void applyToOtherHand(Player player, InteractionHand usedHand,
                                   ResourceLocation effectId, int maxLevel, boolean shift) {
        InteractionHand otherHand = usedHand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
        ItemStack target = player.getItemInHand(otherHand);
        if (target.isEmpty()) return;

        if (shift) {
            int current = EffectNBTHandler.getEffectLevel(target, effectId);
            if (current <= 1) {
                EffectNBTHandler.removeEffectFromItem(target, effectId);
            } else {
                EffectNBTHandler.setEffectLevel(target, effectId, current - 1);
            }
        } else {
            int current = EffectNBTHandler.getEffectLevel(target, effectId);
            EffectNBTHandler.setEffectLevel(target, effectId, Math.min(current + 1, maxLevel));
        }
        // 等级变动后立即重算物品属性（利刃写入 %damage_amplification，供伤害链读取）
        SharpBladeEffect.recalculate(target);
    }

    /** 从物品 NBT 读取容器内效果 ID */
    public static ResourceLocation getContainedEffect(ItemStack stack) {
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag tag = cd.copyTag();
        if (tag.contains(CONTAINED_KEY, Tag.TAG_STRING)) {
            return ResourceLocation.parse(tag.getString(CONTAINED_KEY));
        }
        return null;
    }

    /** 读取最大等级，未设置返回 1 */
    public static int getContainedMaxLevel(ItemStack stack) {
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 1;
        CompoundTag tag = cd.copyTag();
        return tag.contains(MAX_LEVEL_KEY) ? tag.getInt(MAX_LEVEL_KEY) : 1;
    }

    /** 给物品写入容器效果 ID 和最大等级 */
    public static void setContainedEffect(ItemStack stack, ResourceLocation effectId, int maxLevel) {
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.putString(CONTAINED_KEY, effectId.toString());
        tag.putInt(MAX_LEVEL_KEY, maxLevel);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }
}
