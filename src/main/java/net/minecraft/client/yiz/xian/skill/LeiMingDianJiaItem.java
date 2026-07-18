package net.minecraft.client.yiz.xian.skill;

import net.minecraft.client.yiz.api.ISkillItem;
import net.minecraft.client.yiz.api.SkillCastMode;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷鸣电甲 — 开关形技能，开启后持续消耗蓝量，周期性链式闪电 + 护盾。
 */
public class LeiMingDianJiaItem extends Item implements ISkillItem {

    /** 玩家UUID → (开启tick, 是否大槽, 槽位编号) */
    private record ToggleState(long startedTick, boolean bigSlot, int slot) {}
    private static final ConcurrentHashMap<UUID, ToggleState> ACTIVE = new ConcurrentHashMap<>();

    public LeiMingDianJiaItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers()));
    }

    private static ItemAttributeModifiers buildModifiers() {
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.MANA_COST_PER_SEC, mod("lmdj_mc", 8.5), EquipmentSlotGroup.ANY)
            .build();
    }

    private static AttributeModifier mod(String id, double val) {
        return new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath("yizmodqzk", id),
            val, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public SkillCastMode getCastMode(ItemStack stack) { return SkillCastMode.INSTANT; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        double sp = player != null ? YizAttributes.getEffectiveSpellPower(player) : 0;
        int dmg = (int)(1 + sp * 0.12);
        double shield = 1 + sp * 0.0125;

        tooltip.add(Component.literal("§9雷鸣电甲"));
        tooltip.add(Component.literal("§7开关形 · 开启后自身化为雷霆之源"));
        tooltip.add(Component.literal("§7每 0.25s 向周围 6 格爆发链式闪电"));
        tooltip.add(Component.literal("§7每秒获得护盾"));
        tooltip.add(Component.literal("§7伤害: §9" + dmg + " §7护盾: §9" + String.format("%.1f", shield) + "/s"));
        tooltip.add(Component.literal("§8蓝耗: 8.5/s  无冷却"));
    }

    @Override
    public void onCast(Player player, ItemStack stack) {
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();
        if (ACTIVE.containsKey(uuid)) {
            // 关闭
            deactivate(player);
            return;
        }

        // 开启
        float mana = net.minecraft.client.yiz.tool.health.ManaTracker.get(player);
        if (mana <= 0) return;
        int sl = net.minecraft.client.yiz.handler.LastCastSlotTracker.get();
        ACTIVE.put(uuid, new ToggleState(player.level().getGameTime(), sl == 0, sl));
        net.minecraft.client.yiz.handler.ToggleSkillState.setActive(player, true);

        // 登记卸载清理回调：技能被卸载时立即关闭（不必等 onTick 槽位校验的下一 tick）。
        net.minecraft.client.yiz.api.EffectRegistry.register(uuid,
            "skill:yizxianmod:lei_ming_dian_jia",
            () -> deactivate(player));

        // 开启入口：分发 ACTIVATE 时机的强化标签（破阵金身在此挂 transient 标记）。
        // 放在开启成功之后，蓝量不足(mana<=0)时不会误触发。
        onActivate(sl, player, stack);
    }

    /** 关闭技能：移除 ACTIVE 表项 + 清破阵金身标记 + 取消激活态。供 onCast 关闭分支与卸载清理共用。 */
    private void deactivate(Player player) {
        ACTIVE.remove(player.getUUID());
        player.getPersistentData().remove("yiz:pozhenjinshen");
        net.minecraft.client.yiz.handler.ToggleSkillState.setActive(player, false);
    }

    /** 由 YizxianMod.onPlayerTick 调用 */
    public static void onTick(ServerPlayer player) {
        ToggleState state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        // 槽位检查：物品被移走或无此技能则自动关闭
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) { ACTIVE.remove(player.getUUID()); return; }
        net.minecraft.world.item.ItemStack slotItem = switch (state.slot) {
            case 0 -> data.bigLoad().getItem(0);
            case 1 -> data.skillLoad().getItem(0);
            case 2 -> data.skillLoad().getItem(1);
            case 3 -> data.skillLoad().getItem(2);
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
        if (!(slotItem.getItem() instanceof LeiMingDianJiaItem)) {
            ACTIVE.remove(player.getUUID());
            net.minecraft.client.yiz.handler.ToggleSkillState.setActive(player, false);
            return;
        }

        double spellPow = YizAttributes.getEffectiveSpellPower(player);
        float baseDmg = (float)(1 + spellPow * 0.12);
        float baseShield = (float)(1 + spellPow * 0.0125);
        float baseCost = 0.425f; // 8.5/s → 0.425/tick

        // 大槽：伤害×2，蓝耗÷2
        float dmg = state.bigSlot ? baseDmg * 2f : baseDmg;
        float shieldVal = state.bigSlot ? baseShield * 2f : baseShield;
        float costPerTick = state.bigSlot ? baseCost * 0.5f : baseCost;

        // 永恒储蓝减耗
        float reduction = (float) player.getAttributeValue(YizAttributes.MANA_COST_REDUCTION);
        costPerTick = Math.max(0, costPerTick - reduction * 0.05f);

        if (!net.minecraft.client.yiz.tool.health.ManaTracker.consume(player, costPerTick)) {
            ACTIVE.remove(player.getUUID());
            net.minecraft.client.yiz.handler.ToggleSkillState.setActive(player, false);
            return;
        }

        long elapsed = player.level().getGameTime() - state.startedTick;

        // 链电周期：基础 5tick，受 SKILL_INTERVAL 加速率影响
        int chainInterval = (int) Math.max(1, Math.round(
            net.minecraft.client.yiz.tool.skill.SkillIntervals.get(player, 5, "damage")));
        if (elapsed % chainInterval == 0) {
            // 范围：基础 6.0，受 SKILL_RANGE 倍率 + 标签偏移影响
            double range = net.minecraft.client.yiz.tool.skill.SkillRanges.get(player, 6.0, "damage");
            AABB aabb = player.getBoundingBox().inflate(range);
            List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class, aabb,
                e -> e.isAlive() && e != player && player.distanceTo(e) <= range);
            for (LivingEntity e : nearby) {
                // 主级标记：10tick内每5tick以自身为中心对周围3格造成伤害，不传播防递归
                net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockWithDamage(
                    e, player, dmg, 3.0f, 10, 5);
            }
            // 玩家体表电流 + 链式闪电视觉
            net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockVisualOnly(
                player, (float) range, 20);
            // 触发入口：每次链电周期结算伤害后，分发一次 TRIGGER 时机标签（雷震千里/奔雷袭等）。
            // 放在循环外、每周期调一次，而非每实体调一次——标签是给整个玩家挂窗口的。
            ((LeiMingDianJiaItem) slotItem.getItem()).onTrigger(state.slot, player, slotItem, null);
        }

        // 护盾周期：基础 4tick；破阵金身(transient 标记)激活时频率翻倍→2tick。
        // 标记由 onActivate 触发的 pozhenjinshen 标签挂载，关闭技能时清除。
        boolean hasPozhen = player.getPersistentData().getBoolean("yiz:pozhenjinshen");
        int shieldInterval = hasPozhen ? 2 : 4;
        // SKILL_INTERVAL 加速率同样作用于护盾周期
        shieldInterval = (int) Math.max(1, Math.round(
            shieldInterval * (1 - player.getAttributeValue(YizAttributes.SKILL_INTERVAL) / 100.0)));
        float perTickShield = shieldVal / 5f; // 将每秒总量拆为 5 份
        if (elapsed % shieldInterval == 0) {
            net.minecraft.client.yiz.tool.health.ShieldTracker.add(player, perTickShield);
        }
    }

    public static boolean isActive(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }
}
