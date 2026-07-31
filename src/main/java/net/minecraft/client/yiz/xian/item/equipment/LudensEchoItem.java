package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.handler.LudenOverkillHandler;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 卢登的激荡 — 单版法师神器（奥恩神器，无普通/光明之分）。
 * <pre>
 *  攻击强度  +60%
 *  法术强度  +60（%）
 *  被动·激荡：以穿戴者为击杀来源击杀目标时，过量伤害向附近溅射
 * </pre>
 * <p>击杀非玩家目标时：取距离最近的 2 个非玩家生物（无距离限制，全场搜索），各造成「过量伤害」魔法伤害
 * （穿甲+破无敌帧），并播放闪电链 + 体表闪电特效。</p>
 *
 * <p><b>链式传播</b>：溅射致死的目标会继续向其附近最近 2 个敌人溅射，依此类推，直到范围内无活体
 * 目标自然终止（不限制深度）。每跳衰减 18%（保留 82%，即 {@code ×0.82}）。
 * 每跳溅射延迟 {@link #DELAY_TICKS}（0.5s）使连锁节奏可见。</p>
 */
public class LudensEchoItem extends Item implements IEquipmentItem {

    private static final int SPILL_TARGETS = 2;
    /** 每跳保留系数：溅射 = 过量 × RETAIN。0.82 = 每跳衰减 18%。 */
    private static final double SPILL_RETAIN = 0.82;
    /** 每跳溅射延迟（tick），让连锁传播可见。 */
    private static final long DELAY_TICKS = 10L;

    /** 延迟溅射任务（服务端权威）。 */
    private static final List<PendingSpill> PENDING = new CopyOnWriteArrayList<>();

    private record PendingSpill(long executeAt, ResourceKey<Level> dim,
                                Vec3 centerPos, int centerId,
                                UUID attackerUUID, float overkill) {}

    public LudensEchoItem() {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers()));
    }

    private static ItemAttributeModifiers buildModifiers() {
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.ATTACK_STRENGTH,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:luden_as"),
                    60.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY)
            .add(YizAttributes.SPELL_POWER,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:luden_sp"),
                    60.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·激荡"));
        tooltip.add(Component.literal("§7击杀目标时，过量伤害溅射至最近的 §f" + SPILL_TARGETS + " §7个敌人"));
        tooltip.add(Component.literal("§7溅射伤害 = §f过量伤害 × 82%§7（每跳衰减 18%）"));
        tooltip.add(Component.literal("§7溅射致死可继续传播"));
    }

    @Override public String getUniqueEquipmentGroup() { return ""; }
    @Override public String getUniquePassiveGroup() { return ""; }

    // ── 击杀触发：登记延迟溅射任务（由 YizxianMod.onLivingDeath 调用）────────

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (target instanceof Player) return;                  // 玩家死亡不溅射

        // attacker 来自 captureIfLethal/recordSpill 记录（不依赖 source：溅射 source 非玩家，getEntity 为 null）
        UUID recorded = LudenOverkillHandler.getAttacker(target);
        if (recorded == null) {
            LudenOverkillHandler.clear(target);
            return;
        }

        float spillAmount = LudenOverkillHandler.getSpillAmount(target);
        // 首次（玩家直接击杀，spillAmount<0）：校验 source==recorded，防 persistent data 残留误触
        // 连锁（溅射致死，spillAmount>=0）：recordSpill 权威记录，source 非玩家不校验
        if (spillAmount < 0) {
            Entity srcEntity = event.getSource().getEntity();
            if (srcEntity == null || !srcEntity.getUUID().equals(recorded)) {
                LudenOverkillHandler.clear(target);
                return;
            }
        }

        ServerPlayer player = target.getServer().getPlayerList().getPlayer(recorded);
        if (player == null || !hasLudensEquipped(player)) {
            LudenOverkillHandler.clear(target);
            return;
        }

        // 过量伤害（首次=玩家击杀过量；连锁=上跳溅射量−死前血量），不衰减
        float overkill = LudenOverkillHandler.getOverkill(target);

        // 延迟溅射：DELAY_TICKS 后执行，使连锁节奏可见
        PENDING.add(new PendingSpill(
            target.level().getGameTime() + DELAY_TICKS,
            target.level().dimension(),
            target.position(),
            target.getId(),
            player.getUUID(),
            overkill));

        LudenOverkillHandler.clear(target);
    }

    // ── tick 调度：处理到期溅射（由 YizxianMod.onPlayerTick 调用）────────────

    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) return;
        long now = level.getGameTime();
        ResourceKey<Level> dim = level.dimension();

        List<PendingSpill> toExecute = null;
        for (PendingSpill p : PENDING) {
            if (!p.dim().equals(dim)) continue;       // 仅处理当前维度
            if (p.executeAt() > now) continue;        // 未到期
            if (toExecute == null) toExecute = new ArrayList<>();
            toExecute.add(p);
        }
        if (toExecute == null) return;
        PENDING.removeAll(toExecute);
        for (PendingSpill p : toExecute) executeSpill(level, p);
    }

    /** 执行一次溅射：找最近 2 个敌人，造伤害 + 特效。伤害致死会触发 victim 的 onLivingDeath → 继续登记（连锁）。 */
    private static void executeSpill(ServerLevel level, PendingSpill p) {
        ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(p.attackerUUID());
        if (attacker == null) return;                  // 玩家下线
        if (!hasLudensEquipped(attacker)) return;      // 连锁中卸下卢登则停止

        Vec3 center = p.centerPos();
        // 无限制：遍历世界所有加载实体，取最近 2 个非玩家生物
        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity le && le.isAlive() && !(le instanceof Player)) {
                nearby.add(le);
            }
        }
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        int count = Math.min(SPILL_TARGETS, nearby.size());
        List<LivingEntity> victims = nearby.subList(0, count);

        // 特效：center(死亡位置) 体表 + center→victim 闪电链 + victim 体表
        List<Integer> victimIds = new ArrayList<>(count);
        for (LivingEntity v : victims) victimIds.add(v.getId());
        NetworkHandler.sendLudenFx(level, center, p.centerId(), victimIds);

        // 溅射伤害 = 过量 × 0.82；source=玩家 → 享受法强/攻强增幅 + 触发吸血
        for (LivingEntity v : victims) {
            float spillDmg = (float)(p.overkill() * SPILL_RETAIN);  // 每跳固定保留 82%
            LudenOverkillHandler.recordSpill(v, spillDmg, attacker);  // 记连锁
            int saved = v.invulnerableTime;
            v.invulnerableTime = 0;
            String prev = net.minecraft.client.yiz.core.SpellSourceTracker.set("yizxianmod:ludens_echo");
            try {
                float hpBefore = v.getHealth();
                net.minecraft.world.damagesource.DamageSource ds = attacker.damageSources().source(net.minecraft.client.yiz.api.YizDamageTypes.SPELL, attacker);
                v.hurt(ds, spillDmg);
                // 兜底：末影龙等重写 hurt 吞伤害 → 直接 setHealth 扣血
                if (v.getHealth() >= hpBefore && spillDmg > 0) {
                    float newHp = Math.max(0f, hpBefore - spillDmg);
                    v.setHealth(newHp);
                    if (newHp <= 0f && !v.isDeadOrDying()) v.die(ds);
                }
                System.out.println("[LudenSpill] victim=" + v.getType() + " dmg=" + spillDmg + " hp " + hpBefore + "->" + v.getHealth());
            } finally {
                net.minecraft.client.yiz.core.SpellSourceTracker.restore(prev);
                v.invulnerableTime = saved;
            }
        }
    }

    private static boolean hasLudensEquipped(Player player) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return false;
        for (int i = 0; i < 6; i++) {
            if (data.equipment().getItem(i).getItem() instanceof LudensEchoItem) return true;
        }
        return false;
    }
}
