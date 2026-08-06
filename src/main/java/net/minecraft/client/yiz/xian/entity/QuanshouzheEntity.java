package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.editor.PoshiBypassBridge;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 辖界者 — Boss 生物（M2-M4：AI / 动画 / 技能）。
 *
 * <p>M2-AI：<b>中立生物</b>——不主动索敌；被攻击后反击（{@link net.minecraft.client.yiz.xian.entity.ai.QuanshouzheRetaliateGoal}，
 * 攻击者中有玩家则优先锁定玩家）+ 近战挥砍（{@link net.minecraft.client.yiz.xian.entity.ai.QuanshouzheMeleeGoal}）
 * + 技能循环（{@link net.minecraft.client.yiz.xian.entity.ai.QuanshouzheCastingGoal}）+ 半血狂暴。
 * M4-技能：雷系，逻辑在 {@link QuanshouzheSkillManager}，全部复用现有管道（vanilla 落雷 / 感电 AoE / 电弧视觉）。</p>
 */
public class QuanshouzheEntity extends YizxianMob {

    // ── 技能相位（SynchedEntityData 同步到客户端，驱动施法动画；当前无技能仅 NONE）──
    public static final int PHASE_NONE = 0;

    /** 狂暴移动速度加成。 */
    private static final double RAGE_SPEED_BONUS = 0.2;
    private static final ResourceLocation RAGE_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID, "quanshouzhe_rage_speed");

    private static final EntityDataAccessor<Integer> DATA_CAST_PHASE =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HEAVY_ATTACK =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);

    /** 技能冷却计时（服务端）。当前无远程技能。 */
    private final int[] skillCooldowns = new int[0];

    /** Boss 血条。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("辖界者"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    /** 客户端：最近一次施法开始的世界 tick（动画进度起点）。 */
    private long clientCastStartTick = -1;

    /** 最近被玩家攻击的记录（服务端）：用于「多攻击者时优先锁定玩家」反击判定。 */
    private UUID lastPlayerHurtBy;
    private long lastPlayerHurtTime = -1000;

    /** 传导受击 CD 保底（属性 CONDUCTION_INTERVAL 未挂/为 0 时兜底）：20 tick = 1 秒。 */
    private static final int CONDUCTION_HIT_CD_FALLBACK = 20;
    private long lastConductionHitTick = Long.MIN_VALUE;

    /** 读传导受击 CD（属性 CONDUCTION_INTERVAL 决定，0=禁用；未挂载/为 0 → 保底 20tick）。 */
    private long conductionHitCdTicks() {
        var inst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.CONDUCTION_INTERVAL);
        double v = inst != null ? inst.getValue() : 0;
        return v > 0 ? (long) v : CONDUCTION_HIT_CD_FALLBACK;
    }

    // ── 原版 Warden 动画状态（客户端通过 Pose/实体事件同步触发）──
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState sonicBoomAnimationState = new AnimationState();
    public final AnimationState diggingAnimationState = new AnimationState();
    public final AnimationState emergeAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState sniffAnimationState = new AnimationState();
    /** 咆哮结束 tick（服务端，用于恢复 STANDING pose）。 */
    private int roarEndTick = -1;
    /** 触须动画进度（Warden 同款：byte 61 置 10，客户端每 tick 递减）。 */
    private int tendrilAnimation;
    private int tendrilAnimationO;
    /** 战斗开始 tick（首次获得目标时记录，用于「攻击 5 秒后狂暴」）。 */
    private int combatStartTick = -1;
    /** 狂暴结束 tick（持续 6 秒 = 120 tick；期间满足条件会刷新）。 */
    private int rageEndTick = -1;

    // ── 攻击行为（A 反击 / B 普通 / C 重击）──
    /** 主动攻击计数：每 4 次触发一次重击（C）。 */
    private int attackCounter;
    /** 反击目标 UUID（被攻击后立即反击 85 伤害）；null = 非反击。 */
    @Nullable private UUID counteringTarget;
    /** 重击剩余 tick。 */
    private int heavyAttackTick;
    /** 上次同步的最初梦幻值（攻击力×20%，变化时才写属性）。 */
    private double lastDreamValue = Double.NaN;

    // ── 仇恨系统（继承仇恨扩散）──
    /** 仇恨扩散范围（格）：对该范围内目标同类型实体继承仇恨，范围外不继承。 */
    private static final double HATE_SPREAD_RANGE = 15.0;
    /** 仇恨实体（UUID）：继承仇恨的 15 格内目标同类型实体。 */
    private final Set<UUID> hateSet = new HashSet<>();

    public QuanshouzheEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 400.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.ATTACK_DAMAGE, 50.0)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 60.0)
            .add(Attributes.STEP_HEIGHT, 2.0)
            // ── yizmodqzk 自定义属性骨架（基值 0，数值由 applyEntityAttributes 经 EntityAttributeGate 分配，防外部移除）──
            .add(YizAttributes.ATTACK_STRENGTH, 0.0)
            .add(YizAttributes.SPELL_POWER, 0.0)
            .add(YizAttributes.GENERIC_DAMAGE, 0.0)
            .add(YizAttributes.MELEE_DAMAGE, 0.0)
            .add(YizAttributes.RANGED_DAMAGE, 0.0)
            .add(YizAttributes.DAMAGE_REDUCTION, 0.0)
            .add(YizAttributes.DAMAGE_BLOCK, 0.0)
            .add(YizAttributes.INVINCIBILITY_MULT, 0.0)
            .add(YizAttributes.DODGE_CHANCE, 0.0)
            .add(YizAttributes.LIFE_STEAL, 0.0)
            .add(YizAttributes.ARMOR, 0.0)
            .add(YizAttributes.SPELL_DEFENSE, 0.0)
            // ── 禁疗 + 特殊伤害（2026-08-05 新增，基值 0，数值后续分配）──
            .add(YizAttributes.VITALITY_SEVERANCE_RATE, 0.0)
            .add(YizAttributes.VITALITY_SEVERANCE_TIME, 0.0)
            .add(YizAttributes.FIRST_DREAM, 0.0)
            // ── 传导限伤（2026-08-07 新增，基值 0，数值由 applyEntityAttributes 经 EntityAttributeGate 分配）──
            .add(YizAttributes.CONDUCTION_CAP, 0.0)
            .add(YizAttributes.CONDUCTION_INTERVAL, 0.0)
            // ── 血量隐匿（>0 时真实血量藏 Lambda 闭包）──
            .add(YizAttributes.SECURE_PULSE, 0.0);
    }

    /**
     * 分配辖界者受保护自定义属性值（生成/加载后第一 tick 由 YizxianMob.aiStep 调用一次）。
     * <p>经 {@link EntityAttributeGate} 写入 {@code yizmodqzk:prot_*} modifier：
     * 调用栈+包名鉴权 + {@code AttributeInstanceMixin} 防外部移除。</p>
     */
    @Override
    protected void applyEntityAttributes() {
        // 难度缩放：原版生命/攻击（移速/击退/跟随/步高不变），自定义属性全部 scaleDifficulty（最低 1 点）
        applyVanillaDifficultyScale();
        EntityAttributeGate.set(this, YizAttributes.ATTACK_STRENGTH, "attack_strength", scaleDifficulty(60.0));
        EntityAttributeGate.set(this, YizAttributes.SPELL_POWER, "spell_power", scaleDifficulty(100.0));
        EntityAttributeGate.set(this, YizAttributes.LIFE_STEAL, "life_steal", scaleDifficulty(10.0));
        EntityAttributeGate.set(this, YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(1.0));
        EntityAttributeGate.set(this, YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(25.0));
        EntityAttributeGate.set(this, YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", scaleDifficulty(16.0));
        EntityAttributeGate.set(this, YizAttributes.ARMOR, "armor", scaleDifficulty(15.0));
        EntityAttributeGate.set(this, YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(15.0));
        // ── 传导限伤：先衰减再与上限比较。上限 = 最大生命值 × CONDUCTION_CAP%（当前 25%）。
        //    受击 CD = CONDUCTION_INTERVAL（当前 20tick = 1 秒，属性可调）──
        EntityAttributeGate.set(this, YizAttributes.CONDUCTION_CAP, "conduction_cap", scaleDifficulty(25.0));
        EntityAttributeGate.set(this, YizAttributes.CONDUCTION_INTERVAL, "conduction_interval", scaleDifficulty(20.0));
        // 血量外部存储（flashfur 式）：真实血量在 SecureHealthClosure 哈希表，首次以当前血量注册
        EntityAttributeGate.set(this, YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.register(this, (float) this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH));
        // 健康值字段写入守卫：登记为受管理自研血量实体（拦截外部直接写真实血量字段回血）
        net.minecraft.client.yiz.tool.health.HealthWriteGuard.register(this);
    }

    // ═══ M2-AI：Goal 挂载 ═══

    @Override
    protected void registerGoals() {
        // 纯近战：不主动攻击任何实体。被攻击瞬间立即反击（hurt() 内锁定，玩家优先），跳过创造玩家/无敌。
        this.goalSelector.addGoal(2, new net.minecraft.client.yiz.xian.entity.ai.QuanshouzheMeleeGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.client.yiz.xian.entity.ai.QuanshouzheRetaliateGoal(this));
    }

    // ═══ 同步数据：施法相位 / 狂暴 ═══

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CAST_PHASE, PHASE_NONE);
        builder.define(DATA_RAGING, false);
        builder.define(DATA_HEAVY_ATTACK, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_CAST_PHASE) && level().isClientSide() && getCastPhase() != PHASE_NONE) {
            this.clientCastStartTick = level().getGameTime();
        }
        // 咆哮动画：服务端 setPose(ROARING) → 客户端据此启动 roarAnimationState（Warden 同款机制）
        if (key.equals(DATA_POSE) && getPose() == Pose.ROARING) {
            this.roarAnimationState.start(this.tickCount);
        }
    }

    /** Warden 同款实体事件：4=近战攻击、61=触须竖起、62=音爆起手（客户端驱动 AnimationState）。 */
    @Override
    public void handleEntityEvent(byte id) {
        // 拦截死亡动画（byte 3）：血量未归零时外部模组广播的死亡事件不触发客户端死亡动画
        if (id == 3 && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        if (id == 4) {
            this.roarAnimationState.stop();
            this.attackAnimationState.start(this.tickCount);
        } else if (id == 61) {
            this.tendrilAnimation = 10;
        } else if (id == 62) {
            this.sonicBoomAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** 触须动画进度 0..1（Warden 同款，模型 animateTendrils 用）。 */
    public float getTendrilAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.tendrilAnimationO, this.tendrilAnimation) / 10.0F;
    }

    public int getCastPhase() { return getEntityData().get(DATA_CAST_PHASE); }
    public void setCastPhase(int phase) { getEntityData().set(DATA_CAST_PHASE, phase); }
    /** 狂暴状态：6 秒计时内为真（服务端读；客户端无动画依赖）。 */
    public boolean isRaging() { return this.tickCount < this.rageEndTick; }
    public void setRaging(boolean raging) { getEntityData().set(DATA_RAGING, raging); }

    /** 技能冷却：当前无远程技能，恒可用。 */
    public boolean isSkillReady(int index) { return true; }
    public void setSkillCooldown(int index, int ticks) {}

    /** 客户端施法进度 0→1（超出钳制为 1），供模型 setupAnim 读取。 */
    public float getCastProgress(float partialTick) {
        if (getCastPhase() == PHASE_NONE || clientCastStartTick < 0) return 0f;
        int windup = getCastWindup(getCastPhase());
        float elapsed = level().getGameTime() + partialTick - clientCastStartTick;
        return Mth.clamp(elapsed / windup, 0f, 1f);
    }

    /** 阶段 → 前摇 tick（当前无技能，恒 0）。 */
    public static int getCastWindup(int phase) {
        return 0;
    }

    // ═══ 攻击执行（服务端）：A 反击 85 / B 普通 25~40 / C 重击 45~65+9格AoE ═══

    /** 重击状态（SynchedEntityData 同步到客户端，供模型放缓动画）。 */
    public boolean isHeavyAttacking() { return getEntityData().get(DATA_HEAVY_ATTACK); }
    public void setHeavyAttacking(boolean v) { getEntityData().set(DATA_HEAVY_ATTACK, v); }

    /** MeleeGoal 调用：对目标执行一次攻击。整个攻击（含重击 AoE）处于破时绕过窗口内。 */
    public void attackTarget(LivingEntity target) {
        PoshiBypassBridge.beginBypass();
        try {
            // 伤害按攻击力倍率计算（随难度缩放实时生效）：反击 ×1.7 / 普通 ×(0.5~0.8) / 重击 ×(0.9~1.3)
            float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            // A 反击：对"刚攻击过自己"的目标造成 攻击力×1.7
            if (this.counteringTarget != null && this.counteringTarget.equals(target.getUUID())) {
                this.counteringTarget = null;
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * 1.7f);
                return;
            }
            // 主动攻击循环：每 4 次触发一次重击
            this.attackCounter++;
            if (this.attackCounter % 4 == 0) {
                this.performHeavyAttack(); // C 重击
            } else {
                // B 普通攻击：攻击力×(0.5~0.8) 随机
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * (0.5f + this.random.nextFloat() * 0.3f));
            }
        } finally {
            PoshiBypassBridge.endBypass();
        }
    }

    /** 带破时的近战伤害：清目标无敌帧 + 目标 hurt（须在破时绕过窗口内调用）。 */
    private void hit(LivingEntity target, float dmg) {
        if (isObserver(target)) return; // 创造模式玩家旁观：跳过该玩家（所有伤害触发方式）
        // 辖界者攻击效果：每次攻击叠加 5% 绝妄生机（上限 100% = 完全禁疗），持续 7 秒（连续攻击刷新时长）
        net.minecraft.client.yiz.tool.health.VitalitySeveranceHandler.addStackingBan(target, 5.0f, 7 * 20L);
        target.invulnerableTime = 0;
        // 攻方「最初梦幻」通用消费：即使目标免疫/无敌（hurt 返回 false 不走 super.hurt）也直接扣真实血量
        net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, target);
        target.hurt(this.damageSources().mobAttack(this), dmg);
    }

    /** C 重击：原地播放攻击动画 + 对周围 9 格实体造成 45~65 伤害。 */
    private void performHeavyAttack() {
        this.setHeavyAttacking(true);
        this.heavyAttackTick = 7; // 与 Warden 攻击动画时长一致（0.333 秒），避免动画播完还原地发呆（卡住感）
        this.level().broadcastEntityEvent(this, (byte)4);
        // C 重击：攻击力×(0.9~1.3)（随难度缩放）
        float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float dmg = baseAtk * (0.9f + this.random.nextFloat() * 0.4f);
        AABB aabb = this.getBoundingBox().inflate(9.0);
        // 范围伤害排除创造模式旁观玩家（否则重击 AoE 会把旁观玩家算进去）
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e) && this.distanceTo(e) <= 9.0);
        for (LivingEntity e : nearby) {
            this.hit(e, dmg);
        }
    }

    /** 重击每 tick 推进（MeleeGoal 调用）；返回 true 表示重击结束。 */
    public boolean tickHeavyAttack() {
        if (!this.isHeavyAttacking()) return false;
        if (--this.heavyAttackTick <= 0) {
            this.setHeavyAttacking(false);
            return true;
        }
        return false;
    }

    // ═══ 仇恨系统：首次产生仇恨时，对 15 格内目标同类型实体继承仇恨 ═══

    /** 首次产生仇恨（hateSet 为空）时扩散；继承仇恨（hateSet 非空）不刷新不扩散。 */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && !level().isClientSide() && this.hateSet.isEmpty()) {
            spreadHate(target);
        }
    }

    /** 对以自身为中心 15 格内目标同类型实体全部继承仇恨（范围外不继承）。 */
    private void spreadHate(LivingEntity target) {
        EntityType<?> type = target.getType();
        this.hateSet.add(target.getUUID());
        AABB aabb = this.getBoundingBox().inflate(HATE_SPREAD_RANGE);
        List<Entity> sameType = this.level().getEntities(this, aabb,
            e -> e.isAlive() && e != this && e.getType() == type);
        for (Entity e : sameType) {
            this.hateSet.add(e.getUUID());
        }
    }

    /** 每 tick 从仇恨列表维护攻击目标：当前目标有效则保持（避免来回摇摆切换延迟），无效则选最近的仇恨实体。 */
    private void updateTargetFromHate() {
        if (this.hateSet.isEmpty()) return;
        // 当前目标仍有效（存活且在仇恨内）→ 保持，不切换
        LivingEntity cur = this.getTarget();
        if (cur != null && cur.isAlive()
                && this.hateSet.contains(cur.getUUID())
                && isValidRetaliateTarget(cur)) {
            return;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<UUID> it = this.hateSet.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = this.level() instanceof ServerLevel sl ? sl.getEntity(id) : null;
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !isValidRetaliateTarget(le)) {
                it.remove(); // 死亡/无敌/创造 → 移出仇恨
                continue;
            }
            double d = this.distanceToSqr(le);
            if (d < bestDist) {
                bestDist = d;
                best = le;
            }
        }
        if (best != null) {
            // 选最近仇恨目标；hateSet 非空 → setTarget 不会再次扩散（继承仇恨不刷新）
            this.setTarget(best);
        }
    }

    // ═══ Tick：技能 CD 递减（服务端）═══

    @Override
    public void aiStep() {
        super.aiStep();
        // 触须动画进度（客户端也推进，Warden 同款）
        this.tendrilAnimationO = this.tendrilAnimation;
        if (this.tendrilAnimation > 0) this.tendrilAnimation--;
        if (!level().isClientSide()) {
            // 最初梦幻绑定自身攻击力 20%（攻击力变化时才更新属性，避免每 tick 写）
            double dream = this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.2;
            if (dream != this.lastDreamValue) {
                net.minecraft.client.yiz.tool.attribute.EntityAttributeGate.set(
                    this, YizAttributes.FIRST_DREAM, "first_dream", dream);
                this.lastDreamValue = dream;
            }
            for (int i = 0; i < skillCooldowns.length; i++) {
                if (skillCooldowns[i] > 0) skillCooldowns[i]--;
            }
            // 重击计时（独立于 Goal：防止目标丢失/Goal 停止时重击状态残留导致卡住）
            this.tickHeavyAttack();
            // 咆哮结束后恢复站立
            if (this.getPose() == Pose.ROARING && this.tickCount >= this.roarEndTick) {
                this.setPose(Pose.STANDING);
            }
        }
    }

    // ═══ Boss 血条 ═══

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    // ═══ M2：Boss bar 进度 + 半血狂暴 + 雷暴领域驱动 ═══

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // 仇恨系统：从仇恨列表选最近的存活仇恨实体作为攻击目标
        this.updateTargetFromHate();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        // 记录战斗开始 tick（首次有目标）
        if (this.combatStartTick < 0 && this.getTarget() != null) {
            this.combatStartTick = this.tickCount;
        }
        // 狂暴条件：血量 ≤ 最大生命×50%（半血，随难度生命联动）或 战斗开始 5 秒（100 tick）后
        boolean rageCondition = this.getHealth() <= this.getMaxHealth() * 0.5
            || (this.combatStartTick >= 0 && this.tickCount - this.combatStartTick >= 100);
        if (rageCondition) {
            if (this.isRaging()) {
                this.rageEndTick = this.tickCount + 120; // 已狂暴：刷新 6 秒
            } else {
                enterRage();                              // 开启狂暴，持续 6 秒
                this.rageEndTick = this.tickCount + 120;
            }
        } else if (this.tickCount >= this.rageEndTick) {
            exitRage(); // 条件不满足且 6 秒计时到 → 结束狂暴
        }
    }

    private void enterRage() {
        this.setRaging(true);
        // 狂暴触发提示：Warden 愤怒音效（玩家可明确感知进入狂暴）
        this.playSound(SoundEvents.WARDEN_AGITATED, 5.0F, 1.0F);
        var inst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                RAGE_SPEED_ID, RAGE_SPEED_BONUS, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void exitRage() {
        this.setRaging(false);
        this.rageEndTick = -1;
        var inst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(RAGE_SPEED_ID);
        }
    }

    // ═══ 血量外部存储（flashfur 式）：真实血量在 SecureHealthClosure 哈希表，vanilla 字段不参与逻辑血量 ═══
    // 外部模组（如寰宇支配之剑）调 setHealth(0) 会被重定向到 hurt() 走传导限伤，无法秒杀；
    // getHealth/isAlive/isDeadOrDying 全部从哈希表判定，vanilla dead 字段/血量字段不影响逻辑血量。

    @Override
    public float getHealth() {
        if (level().isClientSide()) return super.getHealth(); // 客户端读 vanilla（血条显示用，服务端权威）
        return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
    }

    @Override
    public void setHealth(float health) {
        if (level().isClientSide()) { super.setHealth(health); return; }
        float current = getHealth();
        if (health >= current) {
            // 治疗方向：直接写表（含满血设置/复活）
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, health);
            return;
        }
        // 扣血方向：重定向到 hurt() 走传导限伤（最多扣 maxHealth×CONDUCTION_CAP%）——防 setHealth(0) 秒杀
        net.minecraft.client.yiz.tizMod.LOGGER.info("[QSZ] setHealth {} (current={}) → hurt generic",
            health, current);
        this.hurt(this.damageSources().generic(), current - health);
    }

    @Override
    public boolean isAlive() {
        if (level().isClientSide()) return super.isAlive();
        return !isRemoved() && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0;
    }

    /**
     * 拦截外部强制 DYING pose（寰宇支配之剑自实现 die() 直接 setPose(DYING) 造成"倒地秒杀"视觉）。
     * 只允许我们主动死亡接管（表血量≤0）时设置；外部模组在血量未归零时设 DYING 被拒。
     */
    @Override
    public void setPose(net.minecraft.world.entity.Pose pose) {
        if (pose == net.minecraft.world.entity.Pose.DYING && !level().isClientSide()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return; // 血量未归零：拒绝外部强制倒地
        }
        super.setPose(pose);
    }

    /**
     * 拦截外部强制掉落（寰宇支配之剑自实现 die() 直接调 dropAllDeathLoot）。
     * 血量未归零时不做任何掉落/经验——外部模组无法靠"置 dead=true + 调掉落"伪造死亡。
     */
    @Override
    protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.world.damagesource.DamageSource source) {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return; // 血量未归零：拒绝外部伪造掉落
        }
        super.dropAllDeathLoot(level, source);
    }

    @Override
    public boolean isDeadOrDying() {
        if (level().isClientSide()) return super.isDeadOrDying();
        return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) <= 0;
    }

    /** 被攻击瞬间立即锁定攻击者还手（跳过创造模式玩家/无敌实体），避免轮询延迟。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        if (amount <= 0) return false;

        // ── 传导受击 CD（flashfur 式 iFrames，属性 CONDUCTION_INTERVAL 决定时长）──
        // 寰宇支配之剑每次左键 = hurt(真伤) + setHealth(0) 重定向 = 两次扣血，连点会快速耗血。
        // CD 让每次实际扣血后 N tick 内不再接受任何伤害（含 setHealth 重定向的 hurt）——连点只吃第一下。
        // 注意：lastConductionHitTick 初始 Long.MIN_VALUE，若直接相减会 long 溢出为负数恒 <CD → 全部误挡。
        // 因此先判断是否已初始化（MIN_VALUE 表示从未扣血 → 首次放行）。
        long cdTicks = conductionHitCdTicks();
        long hitCdStart = this.lastConductionHitTick;
        if (hitCdStart != Long.MIN_VALUE
                && this.level().getGameTime() - hitCdStart < cdTicks) {
            return false;
        }

        // ── 传导限伤接管：丢弃模组原值，衰减→限伤→自行写表扣血 ──
        // 不调 super.hurt()（那会走 vanilla 血量字段 + 触发 LivingDeathEvent 等）——完全自管。
        // 闪避/无敌帧（DODGE_CHANCE / INVINCIBILITY_MULT 属性，有值才生效；无属性不影响）
        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.HurtHeadResult head =
            net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onHurtHead(this);
        if (head == net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.HurtHeadResult.CANCEL) {
            return false; // 闪避/无敌帧：挡本次
        }
        // CDR 破无敌帧（攻击来源）
        if (source.getEntity() instanceof LivingEntity attacker) {
            net.minecraft.client.yiz.handler.InvulnBreakHandler.apply(attacker, this);
        }
        // 传导限伤：先衰减计算，再与限伤上限比较取 min。
        //   reduced = 衰减(amount)        —— DAMAGE_REDUCTION（百分比）+ DAMAGE_BLOCK（固定）
        //   limited = min(reduced, cap)   —— cap = maxHealth × CONDUCTION_CAP%（属性决定，未挂载保底 25%）
        // 语义：原始伤害先被减伤/格挡衰减；衰减后仍超过上限（如 MAX 巨伤）才被 cap 兜底到上限；
        // 衰减后已低于上限则按衰减后值扣（如 101 → 74）。上限 = 最大生命值 × 百分比。
        float reduced = amount;
        var redInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_REDUCTION);
        if (redInst != null && redInst.getValue() > 0)
            reduced *= (float) (1.0 - Math.min(1.0, redInst.getValue() / 100.0));
        var blockInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_BLOCK);
        if (blockInst != null && blockInst.getValue() > 0)
            reduced = Math.max(0, reduced - (float) blockInst.getValue());
        var capInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.CONDUCTION_CAP);
        double capPercent = capInst != null ? capInst.getValue() : 0;
        if (capPercent <= 0) capPercent = 25.0; // 保底：写死 25%
        float cap = Math.max(3.0f, (float) (this.getMaxHealth() * capPercent / 100.0));
        float limited = Math.min(reduced, cap);
        if (limited <= 0) return false;

        // 写表扣血
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        // 更新传导受击 CD（本次实际扣血后 1 秒内不再受伤害）
        this.lastConductionHitTick = this.level().getGameTime();
        // 【诊断】限频日志：确认寰宇支配之剑攻击路径与限伤
        if (this.tickCount % 5 == 0) {
            net.minecraft.client.yiz.tizMod.LOGGER.info("[QSZ] hurt src={} raw={} cap={} limited={} hp {}->{}",
                source.getMsgId(), amount, cap, limited, current, next);
        }

        // 受伤反馈（红闪/音效）
        this.hurtTime = 10;
        this.hurtDuration = 10;
        this.level().broadcastEntityEvent(this, (byte) 2);

        // 无敌帧激活（受击后 N tick 完全无敌，= 传导 CD）
        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onHurtSuccess(this, this.level().getGameTime());

        // 死亡判定：血量归零 → 死亡接管（手动保留掉落/经验/动画，不派发 LivingDeathEvent）
        if (next <= 0) {
            this.die(source);
            return true;
        }

        // ── 反击锁定（原逻辑保留）──
        if (source.getEntity() instanceof LivingEntity attacker2) {
            if (isValidRetaliateTarget(attacker2)) {
                this.setTarget(attacker2);
                this.counteringTarget = attacker2.getUUID();
                if (this.distanceToSqr(attacker2) <= 27.5625 && !isCounterInProgress()) {
                    beginCounterWindow();
                    try {
                        this.attackTarget(attacker2);
                    } finally {
                        endCounterWindow();
                    }
                }
            }
            if (attacker2 instanceof Player p && !p.isCreative()) {
                this.lastPlayerHurtBy = p.getUUID();
                this.lastPlayerHurtTime = this.level().getGameTime();
            }
        }
        return true;
    }

    /** 是否为有效反击目标（存活、非无敌、非创造模式玩家）。 */
    public boolean isValidRetaliateTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof Player p && p.isCreative()) return false;
        return true;
    }

    /** 最近 600 tick 内攻击过本 Boss 且仍存活的玩家；无则 null。 */
    @Nullable
    public LivingEntity getRecentPlayerAttacker() {
        if (this.lastPlayerHurtBy == null
                || this.level().getGameTime() - this.lastPlayerHurtTime > 600) {
            return null;
        }
        if (this.level() instanceof ServerLevel sl) {
            Player p = sl.getPlayerByUUID(this.lastPlayerHurtBy);
            return (p != null && p.isAlive()) ? p : null;
        }
        return null;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide()) {
            // ═══ 死亡守卫：只有真实血量确实 ≤0（我们允许死）才执行死亡接管 ═══
            // 外部模组（如寰宇支配之剑）会直接调 target.die(source) 强行判死，绕过 hurt/setHealth 限伤——
            // 若血量 >0 时响应 die()，等于外部模组一句话就能杀掉辖界者。这里用外部表真实血量守卫：
            // 血量 >0 → 忽略本次 die 调用（外部模组无法强行判死）。
            float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            if (hp > 0.0F) return; // 血量未归零：拒绝外部 die()

            this.bossEvent.removeAllPlayers();
            QuanshouzheSkillManager.clear(this.getUUID());
            // ═══ 死亡接管：手动复刻原版 die()，但不派发 LivingDeathEvent ═══
            // 原版第一行 CommonHooks.onLivingDeath() 派发 LivingDeathEvent——模组的死亡粒子/客户端移除渲染
            // 都在这个事件里触发，无法事后拦截。这里完全接管：保留掉落/经验/死亡动画/DYING pose，
            // 但不调 super.die() → 模组死亡特效彻底不触发。
            // 移除由 YizieManager（aiStep 血量≤0 检测）走原版移除链完成，此处不 setRemoved。
            if (!this.isRemoved() && !this.dead) {
                // 击杀计分（计分板/击杀数，非掉落）
                net.minecraft.world.entity.LivingEntity killCredit = this.getKillCredit();
                if (this.deathScore >= 0 && killCredit != null) {
                    killCredit.awardKillScore(this, this.deathScore, source);
                }
                if (this.isSleeping()) this.stopSleeping();
                this.dead = true;
                this.getCombatTracker().recheckStatus();
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    // 掉落 + 经验（dropAllDeathLoot 含 dropEquipment + dropExperience）
                    this.dropAllDeathLoot(sl, source);
                    // 死亡动画（byte 3 = 死亡）
                    this.level().broadcastEntityEvent(this, (byte) 3);
                }
                this.setPose(net.minecraft.world.entity.Pose.DYING);
            }
        }
    }
}
