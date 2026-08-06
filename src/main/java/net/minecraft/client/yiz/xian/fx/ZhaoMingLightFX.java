package net.minecraft.client.yiz.xian.fx;

import net.minecraft.client.yiz.api.YizDamageTypes;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.ManaTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 紫昭明光 — 服务端纯数据对象（无真实实体）。
 * <p>状态机（服务端权威，由 {@link ZhaoMingLightManager} 每 tick 驱动）：
 * <pre>
 * FLYING          直线无重力 4 格/秒，2 秒内路径命中第 1 个实体 → 8 法伤 → 移除
 *                 （撞方块 → 以碰撞方块位置为中心 → SEEKING_BLOCK；2 秒未命中 → SEEKING_PLAYER）
 * SEEKING_PLAYER  以施法者为中心 24 格寻最近实体，接触 → 4 法伤 → 移除；无 → RETURNING
 * SEEKING_BLOCK   以碰撞方块位置为中心 24 格寻最近实体，接触 → 4 法伤 → 移除；无 → RETURNING
 * RETURNING       以 4 格/秒平滑飞回玩家头顶盘旋点（不瞬移）；途中出现敌人 → SEEKING_PLAYER
 * HOVERING        玩家头顶绕 Y 轴画圆（多球 360/N 分配角度，半径随数量增大，平滑跟随），
 *                 期间 24 格出现敌人 → SEEKING_PLAYER；15 秒结束 → 返还 2 耐久 + 10 法力
 * </pre>
 * 伤害为 spell 法强类型，按法强百分比放大。</p>
 */
public class ZhaoMingLightFX {

    /** 飞行速度：16 格/秒 = 0.8 格/tick（4→8 翻倍） */
    public static final double SPEED = 0.8;
    /** 直线飞行时长：2 秒 */
    public static final int FLYING_TICKS = 40;
    /** 盘旋时长：15 秒 */
    public static final int HOVER_TICKS = 300;
    /** 寻敌范围 */
    public static final double SEEK_RANGE = 24.0;
    /** 直线阶段基础伤害 */
    public static final double DMG_FLY = 8.0;
    /** 寻敌阶段基础伤害 */
    public static final double DMG_SEEK = 4.0;
    /** 盘旋消失返还 */
    public static final double REFUND_DURABILITY = 2.0;
    public static final float REFUND_MANA = 10.0f;
    /** 盘旋半径参数 */
    public static final double HOVER_RADIUS_BASE = 0.7;
    public static final double HOVER_RADIUS_STEP = 0.4;
    public static final double HOVER_ROT_SPEED = 0.05;

    public enum State { FLYING, SEEKING_PLAYER, SEEKING_BLOCK, RETURNING, HOVERING }

    public final int id;
    public final UUID ownerUuid;
    public final ServerLevel level;
    public Vec3 position;
    public Vec3 velocity;
    public State state = State.FLYING;
    public Vec3 blockHitPos;
    /** 准心方向（发射时视线方向，初始轨迹朝准心） */
    public Vec3 lookDir;
    /** 准心线上的倾斜目标点（发射时 眼睛 + look*5） */
    public Vec3 alignTarget;
    private int alignTicks;
    public int flyingTicks;
    public int hoverTicks;
    public boolean hasRefunded;
    public boolean removed;

    public ZhaoMingLightFX(int id, ServerLevel level, UUID ownerUuid, Vec3 pos, Vec3 vel,
                           Vec3 lookDir, Vec3 alignTarget) {
        this.id = id;
        this.level = level;
        this.ownerUuid = ownerUuid;
        this.position = pos;
        this.velocity = vel;
        this.lookDir = lookDir;
        this.alignTarget = alignTarget;
    }

    public void tick() {
        Player owner = level.getPlayerByUUID(ownerUuid);
        if (owner == null || !owner.isAlive()) {
            removed = true;
            return;
        }
        switch (state) {
            case FLYING -> tickFlying();
            case SEEKING_PLAYER -> tickSeekingPlayer(owner);
            case SEEKING_BLOCK -> tickSeekingBlock(owner);
            case RETURNING -> tickReturning(owner);
            case HOVERING -> tickHovering(owner);
        }
    }

    // ── FLYING：直线无重力，4 格/秒，2 秒 ────────────────────────────

    private void tickFlying() {
        // 倾斜到准心：前 6 tick 朝准心线上的目标点收敛（快速转向），之后沿准心直线
        if (alignTicks < 6 && lookDir != null && alignTarget != null) {
            Vec3 to = alignTarget.subtract(position);
            double d = to.length();
            velocity = d < 0.3 ? lookDir.scale(SPEED) : to.normalize().scale(SPEED);
            alignTicks++;
        } else if (lookDir != null) {
            velocity = lookDir.scale(SPEED);
        }
        LivingEntity hit = findFirstHit();
        if (hit != null) {
            dealDamage(hit, DMG_FLY);
            removed = true;
            return;
        }
        Vec3 next = position.add(velocity);
        // 撞方块 → 以碰撞位置为中心寻敌
        if (level.getBlockState(BlockPos.containing(next)).isSolid()) {
            blockHitPos = position;
            state = State.SEEKING_BLOCK;
            return;
        }
        position = next;
        if (++flyingTicks >= FLYING_TICKS) {
            state = State.SEEKING_PLAYER;
        }
    }

    // ── SEEKING_PLAYER：以玩家为中心 24 格寻最近实体 ──────────────────

    private void tickSeekingPlayer(Player owner) {
        LivingEntity target = findNearestEnemy(owner.position(), SEEK_RANGE);
        if (target == null) {
            state = State.RETURNING;
            return;
        }
        chaseAndHit(target);
    }

    // ── SEEKING_BLOCK：以碰撞方块位置为中心 24 格寻最近实体 ───────────

    private void tickSeekingBlock(Player owner) {
        Vec3 center = blockHitPos != null ? blockHitPos : position;
        LivingEntity target = findNearestEnemy(center, SEEK_RANGE);
        if (target == null) {
            state = State.RETURNING;
            return;
        }
        chaseAndHit(target);
    }

    private void chaseAndHit(LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 toTarget = targetCenter.subtract(position);
        double dist = toTarget.length();
        if (dist < 0.5 || new AABB(position, position).inflate(0.3).intersects(target.getBoundingBox())) {
            dealDamage(target, DMG_SEEK);
            removed = true;
            return;
        }
        Vec3 step = toTarget.normalize().scale(Math.min(SPEED, dist));
        position = position.add(step);
    }

    // ── RETURNING：以 4 格/秒平滑飞回玩家头顶盘旋点（不瞬移）─────────

    private void tickReturning(Player owner) {
        if (findNearestEnemy(owner.position(), SEEK_RANGE) != null) {
            state = State.SEEKING_PLAYER;
            return;
        }
        Vec3 targetPos = computeHoverSlot(owner);
        Vec3 delta = targetPos.subtract(position);
        double dist = delta.length();
        if (dist < 0.2) {
            position = targetPos;
            state = State.HOVERING;
            hoverTicks = 0;
            return;
        }
        Vec3 step = delta.normalize().scale(Math.min(SPEED, dist));
        position = position.add(step);
    }

    // ── HOVERING：头顶绕 Y 轴画圆盘旋，平滑跟随，15 秒 ────────────────

    private void tickHovering(Player owner) {
        if (findNearestEnemy(owner.position(), SEEK_RANGE) != null) {
            state = State.SEEKING_PLAYER;
            return;
        }
        Vec3 targetPos = computeHoverSlot(owner);
        Vec3 delta = targetPos.subtract(position);
        double dist = delta.length();
        if (dist < 0.05) {
            position = targetPos;
        } else {
            double step = Math.min(SPEED * 1.5, dist);
            position = position.add(delta.normalize().scale(step));
        }
        if (++hoverTicks >= HOVER_TICKS) {
            refund(owner);
            removed = true;
        }
    }

    // ── 盘旋点：玩家头顶 Y 轴画圆，多球 360/N 分配角度，半径随数量增大 ──

    private Vec3 computeHoverSlot(Player owner) {
        List<ZhaoMingLightFX> list = ZhaoMingLightManager.getInstance().forOwner(ownerUuid);
        int index = list.indexOf(this);
        int count = Math.max(1, list.size());
        double radius = HOVER_RADIUS_BASE + HOVER_RADIUS_STEP * (count - 1);
        double angle = 2 * Math.PI * index / count + level.getGameTime() * HOVER_ROT_SPEED;
        double y = owner.getY() + owner.getEyeHeight() + 1.8;
        return new Vec3(owner.getX() + radius * Math.cos(angle), y, owner.getZ() + radius * Math.sin(angle));
    }

    // ── 命中检测：路径上第 1 个实体（最近） ──────────────────────────

    private LivingEntity findFirstHit() {
        AABB box = new AABB(position, position).expandTowards(velocity).inflate(0.3);
        List<Entity> list = level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive());
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : list) {
            if (e.getUUID().equals(ownerUuid)) continue;
            double d = position.distanceToSqr(e.position());
            if (d < bestDist) { bestDist = d; best = (LivingEntity) e; }
        }
        return best;
    }

    /** 以 center 为中心 range 格内最近的敌人。 */
    private LivingEntity findNearestEnemy(Vec3 center, double range) {
        AABB box = new AABB(center, center).inflate(range);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !e.getUUID().equals(ownerUuid) && e.isAlive());
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = center.distanceToSqr(e.position());
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    // ── spell 法伤：法强百分比放大，破无敌帧 ─────────────────────────

    private void dealDamage(LivingEntity target, double base) {
        Player owner = level.getPlayerByUUID(ownerUuid);
        if (owner == null || target == null) return;
        double sp = YizAttributes.getEffectiveSpellPower(owner);
        float dmg = (float) (base * sp / 100.0);
        if (dmg <= 0) dmg = 0.01f;
        DamageSource ds = owner.damageSources().source(YizDamageTypes.SPELL, owner);
        int saved = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            // spell 法伤标准模板：玩家为来源，只 hurt 一次（无 setHealth 兜底）。
            // SPELL 类型无物理 tags → 跳过护甲/减伤/格挡/盾牌；
            // LivingEntityMixin 处理抗性(90%封顶)+保护附魔(80%)+法术防御。
            target.hurt(ds, dmg);
        } finally {
            target.invulnerableTime = saved;
        }
    }

    // ── 盘旋自然结束：返还 2 耐久 + 10 法力 ──────────────────────────

    private void refund(Player owner) {
        if (hasRefunded) return;
        hasRefunded = true;
        ManaTracker.add(owner, REFUND_MANA);
        net.minecraft.world.item.ItemStack refunded = null;
        if (owner.getMainHandItem().getItem()
                instanceof net.minecraft.client.yiz.xian.item.WupinItem) {
            refunded = owner.getMainHandItem();
        } else {
            for (net.minecraft.world.item.ItemStack s : owner.getInventory().items) {
                if (s.getItem() instanceof net.minecraft.client.yiz.xian.item.WupinItem) {
                    refunded = s;
                    break;
                }
            }
        }
        if (refunded != null && refunded.isDamageableItem()) {
            int cur = refunded.getDamageValue();
            refunded.setDamageValue(Math.max(0, cur - (int) REFUND_DURABILITY));
        }
    }
}
