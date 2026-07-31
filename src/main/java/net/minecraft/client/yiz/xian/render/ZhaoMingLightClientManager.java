package net.minecraft.client.yiz.xian.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.yiz.xian.network.S2CZhaoMingLightPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 紫昭明光 — 客户端本地模拟 + 服务端校准（混合方案）。
 * <p>本地状态机每 tick 模拟运动（平滑），服务端每 2 tick 通过 S2C 校准位置/状态/移除
 * （贴合实际轨迹：命中、寻敌、盘旋结束都跟服务端一致）。</p>
 * <ul>
 *   <li>本地预测：施法时立即创建本地 FX（负数 id），S2C 到达后用服务端 id 接管（无缝）</li>
 *   <li>状态对齐：服务端 state 映射到本地（FLYING/SEEKING/HOVERING）</li>
 *   <li>位置逼近：本地位置每 tick 向服务端同步位置逼近 30%，不瞬移</li>
 *   <li>移除同步：服务端移除（命中/盘旋结束）→ 本地移除</li>
 * </ul>
 */
public final class ZhaoMingLightClientManager {

    private static final ZhaoMingLightClientManager INSTANCE = new ZhaoMingLightClientManager();
    private static final double SPEED = 0.4;       // 8 格/秒
    private static final int FLYING_TICKS = 40;    // 2 秒
    private static final int HOVER_TICKS = 300;    // 15 秒
    private static final double SEEK_RANGE = 24.0;
    private static final double APPROACH = 0.3;    // 每 tick 向服务端位置逼近比例

    /** 本地紫昭明光（客户端状态机 + 服务端校准）。 */
    public static final class LocalFX {
        public final int id;
        public final UUID owner;
        public Vec3 position;
        public Vec3 prevPosition;
        public Vec3 velocity;
        /** 服务端校准目标位置（null = 无校准） */
        public Vec3 target;
        /** 0=FLYING 1=SEEKING 2=HOVERING */
        public int state;
        public int flyingTicks;
        public int hoverTicks;
        public boolean removed;

        LocalFX(int id, UUID owner, Vec3 pos, Vec3 vel, int state) {
            this.id = id;
            this.owner = owner;
            this.position = pos;
            this.prevPosition = pos;
            this.velocity = vel;
            this.state = state;
        }

        void tick(ClientLevel level) {
            prevPosition = position;
            Player owner = level.getPlayerByUUID(this.owner);
            if (owner == null) { removed = true; return; }
            // 本地状态机移动（平滑）
            switch (state) {
                case 0 -> {   // FLYING：直线
                    position = position.add(velocity);
                    if (++flyingTicks >= FLYING_TICKS) state = 1;
                }
                case 2 -> { if (++hoverTicks >= HOVER_TICKS) removed = true; }   // 盘旋计时（位置渲染时绕圈）
                default -> {}   // SEEKING/RETURNING：位置全靠服务端 target 逼近（贴合实际轨迹，不抽搐）
            }
            // 服务端校准：向 target 逼近（贴合实际轨迹，不瞬移）
            if (target != null) {
                Vec3 d = target.subtract(position);
                position = position.add(d.scale(APPROACH));
            }
        }
    }

    private final Map<Integer, LocalFX> fx = new HashMap<>();
    private int nextPredId = -1;

    private ZhaoMingLightClientManager() {}

    public static ZhaoMingLightClientManager getInstance() { return INSTANCE; }

    /** 施法：客户端本地预测（立即显示，S2C 后用服务端 id 接管）。 */
    public void add(Player owner, Vec3 dir) {
        if (!owner.level().isClientSide) return;
        // 从法杖物品位置发射（玩家身前手部高度，非视线/眼睛）
        Vec3 pos = new Vec3(owner.getX() + dir.x * 0.6, owner.getY() + 1.0, owner.getZ() + dir.z * 0.6);
        Vec3 vel = dir.normalize().scale(SPEED);
        fx.put(nextPredId--, new LocalFX(0, owner.getUUID(), pos, vel, 0));
    }

    /** S2C 校准：服务端权威状态/位置修正本地，贴合实际轨迹。 */
    public void syncFromServer(List<S2CZhaoMingLightPayload.FxEntry> entries) {
        Set<Integer> serverIds = new HashSet<>();
        for (S2CZhaoMingLightPayload.FxEntry e : entries) {
            LocalFX f = fx.get(e.id());
            if (f == null) {
                // 服务端有、本地无 → 创建在服务端位置
                fx.put(e.id(), new LocalFX(e.id(), e.owner(),
                        new Vec3(e.x(), e.y(), e.z()), Vec3.ZERO, mapState(e.state())));
            } else {
                f.state = mapState(e.state());
                f.target = new Vec3(e.x(), e.y(), e.z());
            }
            serverIds.add(e.id());
        }
        // 本地有但服务端没有（预测被拒 / 服务端已移除）→ 移除，贴合实际
        fx.keySet().removeIf(id -> !serverIds.contains(id));
    }

    /** 服务端 state(0-4) → 本地 state(0-2)。 */
    private static int mapState(int serverState) {
        return serverState == 4 ? 2 : (serverState == 0 ? 0 : 1);
    }

    /** 客户端每 tick 驱动本地状态机。 */
    public void tick(ClientLevel level) {
        for (Iterator<Map.Entry<Integer, LocalFX>> it = fx.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            e.getValue().tick(level);
            if (e.getValue().removed) it.remove();
        }
    }

    public Map<Integer, LocalFX> all() { return fx; }

    /** ClientTickEvent 入口。 */
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level instanceof ClientLevel cl) getInstance().tick(cl);
    }
}
