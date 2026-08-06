package net.minecraft.client.yiz.xian.entity.base;

import net.minecraft.client.yiz.editor.PoshiBearer;
import net.minecraft.client.yiz.editor.PoshiBypassBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 本模组所有实体共用基类 —— 通用底层机制集中在这里。
 *
 * <p>核心原则：<b>正常移动（AI 追击/物理）完全保留原版，只免疫"主动外力"</b>——不拦截移动链，避免误伤。</p>
 * <ul>
 *   <li><b>防 TP</b>：瞬移入口（setPos/moveTo/teleportTo/absMoveTo/teleportRelative/randomTeleport）
 *       除出生/初始化、物理移动中、本模组 {@link #withGate} 受控外一律拒绝 —— 原版 /tp 命令、其他模组 TP 全部无效；</li>
 *   <li><b>防速度注入</b>：setDeltaMovement/addDeltaMovement/knockback 过调用来源门禁，其他模组直接注入被拒；</li>
 *   <li><b>不允许任何状态效果（Buff/Debuff）</b>：isAffectedByPotions/canBeAffected/addEffect/forceAddEffect 全拒，
 *       漂浮/缓速/迅捷/中毒等一律无效；</li>
 *   <li><b>防流体推动</b>：{@link #isPushedByFluid} 恒 false（水流/气泡柱移不动实体）；</li>
 *   <li><b>防击退</b>：原版击退靠击退抗性 + 速度量门禁；</li>
 *   <li><b>不可上船 / 不可骑乘</b>：{@link #startRiding} 恒 false；</li>
 *   <li><b>蜘蛛网免疫</b>：{@link #makeStuckInBlock} 空实现；</li>
 *   <li><b>免疫水下减速 + 水上行走</b>：{@link #getWaterSlowDown} 恒 1.0，{@link #travel} 水面层不沉。</li>
 * </ul>
 *
 * <p>未来新增实体一律继承本基类；通用机制集中加在这里，不单独写给某个实体。</p>
 */
public abstract class YizxianMob extends Mob implements PoshiBearer {

    /** 256 位随机门禁 key（每次游戏启动随机，外部不可预测）。 */
    private static final byte[] DOOR_KEY = new byte[32];
    static {
        new SecureRandom().nextBytes(DOOR_KEY);
    }

    /** 当前线程门禁令牌：本模组受控操作前由 {@link #withGate} 设置，操作结束后清除。 */
    private static final ThreadLocal<byte[]> GATE_TOKEN = new ThreadLocal<>();

    /** 本模组包名前缀。 */
    private static final String MOD_PREFIX = "net.minecraft.client.yiz.xian.";

    /** 引擎白名单前缀 —— 原版引擎 / NeoForge / Mojang 库。 */
    private static final String[] ENGINE_PREFIXES = {
        "net.minecraft.",   // 原版
        "net.neoforged.",   // NeoForge 框架
        "com.mojang.",      // Mojang 库
    };

    /** 引擎白名单中需排除的前缀：命令执行链属于"外部主动操作"，不算引擎必要流程。 */
    private static final String[] COMMAND_FRAME_PREFIXES = {
        "net.minecraft.server.commands.", // /tp、/spreadplayers 等主动命令
    };

    /** 引擎白名单中需排除的外力帧：爆炸等主动外力经 {@code setDeltaMovement} 施加，不算引擎必要流程。
     *  原版爆炸击退（TNT/苦力怕/末影水晶等）不走 {@code knockback}、也不乘击退抗性，直接 setDeltaMovement。 */
    private static final String[] EXTERNAL_FORCE_PREFIXES = {
        "net.minecraft.world.level.Explosion", // 爆炸冲击波
    };

    /** 门禁 override 方法名：方法之间的内部调用链不算"本模组主动操作"。 */
    private static final Set<String> GATED_METHODS = Set.of(
        "setPos", "moveTo", "teleportTo", "absMoveTo", "teleportRelative", "randomTeleport",
        "setDeltaMovement", "addDeltaMovement", "knockback");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger REJECT_LOG_COUNT = new AtomicInteger();

    /** 药水免疫开关（默认 true=全药水免疫，与 Boss 定位一致；false=临时关闭用于测试）。 */
    private static volatile boolean potionImmunity = true;

    public static void setPotionImmunity(boolean enabled) { potionImmunity = enabled; }
    public static boolean isPotionImmunity() { return potionImmunity; }

    /** 当前是否处于物理移动流程（{@link Entity#move} 内部）：true=移动，false=直接坐标变动。 */
    private boolean inPhysicalMove;
    /** 首次合法位置与 tick：出生/加载后第一次坐标设置放行并记录。 */
    private Vec3 lastGatedPos;
    private long lastGatedTick = -1;

    /** 受保护实体属性是否已分配（生成/加载后第一 tick 分配一次，防重复覆盖外部临时 buff）。 */
    private boolean yizxianAttrsApplied;

    /** 原版战斗属性困难模板 baseValue（首次应用时记录，难度重算用 模板×倍率）。 */
    private double templateMaxHealth = -1;
    private double templateAttackDamage = -1;

    /** 上次镜像的 ARMOR/法术防御值（double 比较，值变化才写原版护甲/韧性，避免每 tick 开销）。 */
    private double lastMirrorArmor = Double.NaN;
    private double lastMirrorSpellDefense = Double.NaN;

    protected YizxianMob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    // ═══════════════════ 门禁判定 ═══════════════════

    /**
     * 本模组实体整体 tick 包在门禁令牌内：本模组 AI/物理链内的受控操作天然带 key。
     * 并监听药水效果：若被施加则立刻移除（配合效果免疫，双保险）。
     */
    @Override
    public void aiStep() {
        withGate(() -> {
            boolean server = !this.level().isClientSide();
            if (server) {
                // 生成/加载后第一 tick：分配一次受保护实体属性（子类覆写 applyEntityAttributes）；
                // 难度切换由 DifficultyChangeEvent 事件回调 refreshDifficultyAttributes() 统一重算，不做每 tick 检测
                if (!this.yizxianAttrsApplied) {
                    this.yizxianAttrsApplied = true;
                    this.applyEntityAttributes();
                }
                // 防御镜像：ARMOR/法术防御 1:1 写原版护甲/韧性（值变化才写，避免每 tick 开销）
                this.mirrorDefensiveAttributes();
                // 死亡放行：生命值 ≤0 → 标记移除白名单（ServerLevel 移除保护据此放行原版死亡移除）
                // + 走 YizieManager 原版移除链主动移除（兜底：自研血量/隐匿实体绕过 die() 时确保实体消失）
                if (this.getHealth() <= 0.0F) {
                    net.minecraft.client.yiz.xian.core.EntityRemoveProtection.allowDeathRemove(this.getUUID());
                    net.minecraft.client.yiz.tool.YizieManager.checkAndRemove(this);
                }
                if (potionImmunity) this.removeAllEffects(); // 兜底①：先清除上 tick 遗留（仅免疫开启时清）
            }
            super.aiStep();
            if (server && potionImmunity) {
                this.removeAllEffects(); // 兜底②：本 tick 被新施加的效果立刻移除（仅免疫开启时清）
            }
        });
    }

    /**
     * 实体生成/加载后分配受保护实体属性。子类覆写。
     *
     * <p>用 {@link net.minecraft.client.yiz.tool.attribute.EntityAttributeGate#set} 给实体挂
     * yizmodqzk 自定义属性值（受「调用栈+包名」鉴权 + mixin 防外部移除）。只在生成/加载后
     * 第一 tick 调用一次，避免每 tick 覆盖外部临时 buff。示例：</p>
     * <pre>{@code
     * EntityAttributeGate.set(this, YizAttributes.ATTACK_STRENGTH, "attack_strength", 30.0);
     * }</pre>
     */
    protected void applyEntityAttributes() {
        // 空实现：本模组实体未分配受保护属性时无需动作
    }

    // ═══════════════════ 难度机制（全部战斗属性随世界难度缩放）═══════════════════

    /**
     * 难度倍率：困难 1.0 / 普通 0.75 / 简单 0.5 / 和平 0.5（按简单处理）。
     * 本模组实体全部战斗属性按此倍率缩放，随世界难度实时跟随。
     */
    protected double difficultyMultiplier() {
        return switch (this.level().getDifficulty()) {
            case HARD -> 1.0;
            case NORMAL -> 0.75;
            case EASY, PEACEFUL -> 0.5;
        };
    }

    /**
     * 自定义属性难度缩放：正值 → {@code max(1, 模板×倍率)}（最低 1 点）；
     * 非正值保持原值（值为 0 的属性不因钳位变成 1）。子类 applyEntityAttributes 里调用。
     */
    protected double scaleDifficulty(double templateValue) {
        if (templateValue <= 0) return templateValue;
        return Math.max(1.0, templateValue * difficultyMultiplier());
    }

    /**
     * 原版战斗属性难度缩放：MAX_HEALTH / ATTACK_DAMAGE 以首次应用的 baseValue 为困难模板，
     * 重算为 {@code 模板×倍率}（移速/击退抗性/跟随/步高等非战斗属性不缩放）。
     * 切到更低难度时若当前血量超过新上限则同步钳制。
     */
    protected void applyVanillaDifficultyScale() {
        double mult = difficultyMultiplier();
        var hp = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        var atk = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (hp != null) {
            if (this.templateMaxHealth < 0) this.templateMaxHealth = hp.getBaseValue();
            // 难度变化时保持「已损生命比例」：改 maxHealth 前记录当前比例，改后按比例缩放当前生命，
            // 否则只变上限、当前值不变，血条比例会错乱（如 400/400 满血切普通若只剩上限变化会变 300/400）
            double oldMax = this.getMaxHealth();
            float ratio = oldMax > 0 ? this.getHealth() / (float) oldMax : 1.0F;
            hp.setBaseValue(this.templateMaxHealth * mult);
            if (oldMax != this.getMaxHealth() && ratio > 0) {
                this.setHealth((float) (this.getMaxHealth() * ratio));
            }
        }
        if (atk != null) {
            if (this.templateAttackDamage < 0) this.templateAttackDamage = atk.getBaseValue();
            atk.setBaseValue(this.templateAttackDamage * mult);
        }
    }

    /**
     * 世界难度变化（{@code DifficultyChangeEvent}）时由事件回调：按新难度重算全部战斗属性。
     * 只处理服务端；客户端不参与难度数值。
     */
    public void refreshDifficultyAttributes() {
        if (this.level().isClientSide()) return;
        this.applyEntityAttributes();
    }

    /** 防御属性镜像：ARMOR → 原版护甲+韧性、SPELL_DEFENSE → 击退韧性/免疫（与玩家镜像逻辑一致）。值变化才写。 */
    private void mirrorDefensiveAttributes() {
        double armor = this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.ARMOR);
        if (armor != this.lastMirrorArmor) {
            this.lastMirrorArmor = armor;
            net.minecraft.client.yiz.tizMod.mirrorArmor(this);
        }
        double sd = this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.SPELL_DEFENSE);
        if (sd != this.lastMirrorSpellDefense) {
            this.lastMirrorSpellDefense = sd;
            net.minecraft.client.yiz.tizMod.mirrorSpellDefense(this);
        }
    }

    /**
     * 旁观者后门：创造模式玩家 → 本模组实体的<b>所有</b>伤害触发方式跳过该玩家
     * （单点攻击、范围 AoE、反击锁定、攻方属性消费等）。
     * 供玩家开创造在旁边观战（如辖界者 vs 其他生物），不被单点/范围伤害波及。
     */
    protected boolean isObserver(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player p && p.isCreative();
    }

    /**
     * 速度量入口（setDeltaMovement/addDeltaMovement/knockback）的调用来源门禁：
     * 从调用栈顶向下跳过门禁 override 方法自身，定位第一个"真实调用者"——
     * 本模组业务帧 → 需 key；引擎白名单帧 → 放行；其他模组帧 → 拒绝。
     */
    private boolean motionGate() {
        if (this.tickCount == 0) return true; // 生成/初始化
        if (Arrays.equals(GATE_TOKEN.get(), DOOR_KEY)) return true; // 本模组受控操作
        // 全栈区段白名单检查（本家包 / 引擎帧且非外部 mixin）
        return net.minecraft.client.yiz.tool.ExternalCallGuard.isTrustedCall(GATED_METHODS);
    }

    /**
     * 位置变动放行判定：仅放行四类——客户端（位置由服务端同步）、出生/初始化（首次）、
     * 物理移动中（{@link Entity#move} 内部）、本模组受控（withGate）。其余直接坐标变动一律拒绝。
     */
    private boolean isAllowedPositionChange(double x, double y, double z) {
        if (this.level().isClientSide()) return true; // 客户端位置由服务端同步，不设防
        if (this.tickCount == 0 || this.lastGatedPos == null) {
            this.lastGatedPos = new Vec3(x, y, z);
            this.lastGatedTick = this.tickCount;
            return true; // 出生 / 存档加载首次放置
        }
        if (this.inPhysicalMove) return true; // 物理移动（move 内部应用位置）
        if (Arrays.equals(GATE_TOKEN.get(), DOOR_KEY)) return true; // 本模组受控传送/放置
        return false; // 直接坐标变动（命令 / 其他模组 TP）→ 拒绝
    }

    /**
     * 物理移动入口：设置"正在移动"标志（区分移动 vs 直接传送）。
     * 防击退/推动由 knockback、addDeltaMovement 门禁 + 击退抗性完成，此处不钳制（避免误伤正常追击）。
     */
    @Override
    public void move(MoverType type, Vec3 pos) {
        this.inPhysicalMove = true;
        try {
            super.move(type, pos);
        } finally {
            this.inPhysicalMove = false;
        }
    }


    private static void logRejection(StackTraceElement caller) {
        if (REJECT_LOG_COUNT.incrementAndGet() <= 30) {
            LOGGER.warn("[YizxianMob] 门禁拒绝，真实调用者: {}", caller);
        }
    }

    /**
     * 本模组受控操作入口：执行前出示 256 位门禁 key，结束后清除。
     * 后续自己写 AI 需要显式改实体位置 / 速度时，用本方法包裹。
     */
    protected static void withGate(Runnable action) {
        GATE_TOKEN.set(DOOR_KEY);
        try {
            action.run();
        } finally {
            GATE_TOKEN.remove();
        }
    }

    // ═══════════════════ 反击递归保护 ═══════════════════

    /**
     * 反击递归保护（静态 ThreadLocal 重入锁）：{@code hurt()} 内「贴脸立即反击」会同步调用
     * {@code attackTarget → hit → 目标.hurt}，当两个带反击逻辑的实体互击（辖界者 vs 辖界者，
     * 或存在负生命值 / 不死实体导致反击链失去自然截断）时会产生无限递归 → StackOverflowError。
     *
     * <p>用法：发起同步即时反击前检查 {@link #isCounterInProgress()}——已在反击链内则跳过
     * 即时反击（目标仍锁定、counteringTarget 已设，下 tick 由 MeleeGoal 补上反击），打断循环。</p>
     */
    private static final ThreadLocal<Boolean> COUNTER_RECURSION_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 当前线程是否处于反击链内（嵌套 hurt → 反击 → hurt 中）。 */
    protected static boolean isCounterInProgress() {
        return COUNTER_RECURSION_GUARD.get();
    }

    /** 进入反击窗口：发起同步即时反击前调用（须在 finally 中 {@link #endCounterWindow()} 清除）。 */
    protected static void beginCounterWindow() {
        COUNTER_RECURSION_GUARD.set(Boolean.TRUE);
    }

    /** 退出反击窗口。 */
    protected static void endCounterWindow() {
        COUNTER_RECURSION_GUARD.set(Boolean.FALSE);
    }

    // ═══════════════════ 坐标变动入口（直接传送 → 除出生/受控外拒绝）═══════════════════

    @Override
    public void setPos(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.moveTo(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.moveTo(x, y, z, yRot, xRot);
    }

    @Override
    public void moveTo(Vec3 position) {
        if (!isAllowedPositionChange(position.x, position.y, position.z)) return;
        super.moveTo(position);
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.teleportTo(x, y, z);
    }

    /** 跨维度/带朝向传送（/tp 命令、维度转换入口）。 */
    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z,
                              Set<RelativeMovement> relativeMovements, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return false;
        return super.teleportTo(level, x, y, z, relativeMovements, yRot, xRot);
    }

    /** 绝对位置设置（命令传送 / 存档加载）。 */
    @Override
    public void absMoveTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.absMoveTo(x, y, z);
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.absMoveTo(x, y, z, yRot, xRot);
    }

    /** 相对传送（部分模组/命令实现用）。 */
    @Override
    public void teleportRelative(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.teleportRelative(x, y, z);
    }

    @Override
    public boolean randomTeleport(double x, double y, double z, boolean mayPlaceOn) {
        if (!isAllowedPositionChange(x, y, z)) return false;
        return super.randomTeleport(x, y, z, mayPlaceOn);
    }

    // ═══════════════════ 速度量入口（调用来源门禁）═══════════════════

    @Override
    public void setDeltaMovement(double x, double y, double z) {
        if (!motionGate()) return;
        super.setDeltaMovement(x, y, z);
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
        if (!motionGate()) return;
        super.setDeltaMovement(deltaMovement);
    }

    @Override
    public void addDeltaMovement(Vec3 deltaMovement) {
        if (!motionGate()) return;
        super.addDeltaMovement(deltaMovement);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!motionGate()) return;
        super.knockback(strength, x, z);
    }

    // ═══════════════════ 特判免疫 ═══════════════════

    /** 底层机制：不可被装载为乘客（不可上船/矿车等载具），也不可骑乘任何实体。 */
    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    /**
     * 底层机制：攻击带破时 —— 清目标无敌帧 + 激活破时绕过
     * （目标 hurt 检测到破时携带者 {@link PoshiBearer} 直接走 super.hurt，跳过自定义伤害处理）。
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        target.invulnerableTime = 0; // 清目标无敌帧（Entity public 字段）
        PoshiBypassBridge.beginBypass();
        try {
            return super.doHurtTarget(target);
        } finally {
            PoshiBypassBridge.endBypass();
        }
    }

    /** 底层机制：蜘蛛网免疫 —— 蜘蛛网减速走 CobwebBlock→entityInside→makeStuckInBlock，直接切断。 */
    @Override
    public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
        // 本模组实体不被任何"卡住"方块减速（蜘蛛网、蜂蜜块等）
    }

    /** 底层机制：免疫水下水平减速（水的阻力系数恒为 1.0）。 */
    @Override
    public float getWaterSlowDown() {
        return 1.0F;
    }

    /**
     * 底层机制：水上行走 —— 水面层（已入水未完全没入）垂直归零不沉入；
     * 其余移动完全走原版，不干扰 AI 追击（防御外力靠免疫，不靠拦截移动）。
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater() && !this.isUnderWater() && this.getDeltaMovement().y < 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
        super.travel(travelVector);
    }

    /** 底层机制：药水免疫（默认关闭=药水生效；开启才拒绝状态效果）。 */
    @Override
    public boolean isAffectedByPotions() {
        return potionImmunity ? false : super.isAffectedByPotions();
    }

    /** 底层机制：药水免疫（默认关闭）。 */
    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return potionImmunity ? false : super.canBeAffected(effectInstance);
    }

    /** 底层机制：药水免疫（默认关闭）。 */
    @Override
    public boolean addEffect(MobEffectInstance effectInstance, Entity entity) {
        return potionImmunity ? false : super.addEffect(effectInstance, entity);
    }

    /** 底层机制：药水免疫（默认关闭）。 */
    @Override
    public void forceAddEffect(MobEffectInstance effectInstance, Entity entity) {
        if (potionImmunity) return; // 免疫开启：空实现拒绝
        super.forceAddEffect(effectInstance, entity);
    }

    /** 底层机制：不被流体推动（水流/气泡柱无法移动实体）。 */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
