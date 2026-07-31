package net.minecraft.client.yiz.xian.fx;

import net.minecraft.client.yiz.xian.network.S2CZhaoMingLightPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 紫昭明光 — 服务端管理器（权威逻辑 + S2C 状态/位置同步）。
 * <p>状态机权威（伤害/命中/盘旋/返还）在服务端，每 2 tick 全量同步一次 FX 状态与位置
 * 到客户端（修复：每维度独立 gameTime 计时，避免全局计数器溢出/遍历顺序问题）。
 * 客户端据此插值渲染；盘旋阶段客户端本地绕圈平滑。</p>
 */
public final class ZhaoMingLightManager {

    private static final ZhaoMingLightManager INSTANCE = new ZhaoMingLightManager();

    private final List<ZhaoMingLightFX> fx = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    /** 每个维度上次同步的 gameTime（独立计时，避免溢出/遍历顺序打乱） */
    private final Map<ResourceKey<Level>, Long> lastSyncByLevel = new HashMap<>();

    private ZhaoMingLightManager() {}

    public static ZhaoMingLightManager getInstance() { return INSTANCE; }

    /** 施法：创建紫昭明光（服务端权威）。 */
    public void add(Player owner, Vec3 dir) {
        if (owner.level().isClientSide) return;
        if (!(owner.level() instanceof ServerLevel sl)) return;
        // 从法杖物品位置发射（玩家身前手部高度，非视线/眼睛）
        Vec3 pos = new Vec3(owner.getX() + dir.x * 0.6, owner.getY() + 1.0, owner.getZ() + dir.z * 0.6);
        Vec3 vel = dir.normalize().scale(ZhaoMingLightFX.SPEED);
        fx.add(new ZhaoMingLightFX(idCounter.getAndIncrement(), sl, owner.getUUID(), pos, vel));
    }

    /** 每服务器 tick 驱动：更新当前 level 的 FX 状态机 + 该 level 独立周期同步。 */
    public void tick(ServerLevel level) {
        boolean changed = false;
        for (ZhaoMingLightFX f : fx) {
            if (f.level != level) continue;
            if (f.removed) continue;
            try {
                f.tick();
            } catch (Throwable t) {
                t.printStackTrace();
                f.removed = true;
            }
            if (f.removed) changed = true;
        }
        if (changed) fx.removeIf(f -> f.removed);

        // 每 2 tick 全量同步（发空包也清除客户端残留，保证对齐）
        long now = level.getGameTime();
        Long last = lastSyncByLevel.get(level.dimension());
        if (last == null || now - last >= 2) {
            lastSyncByLevel.put(level.dimension(), now);
            sync(level);
        }
    }

    /** 返回指定施法者名下存活的 FX（按 id 排序，用于盘旋角度分配）。 */
    public List<ZhaoMingLightFX> forOwner(UUID ownerUuid) {
        List<ZhaoMingLightFX> list = new ArrayList<>();
        for (ZhaoMingLightFX f : fx) {
            if (f.ownerUuid.equals(ownerUuid) && !f.removed) list.add(f);
        }
        list.sort(Comparator.comparingInt(f -> f.id));
        return list;
    }

    /** 全量同步该 level 的存活 FX 给该 level 所有玩家。 */
    private void sync(ServerLevel level) {
        List<S2CZhaoMingLightPayload.FxEntry> entries = new ArrayList<>();
        for (ZhaoMingLightFX f : fx) {
            if (f.level != level || f.removed) continue;
            entries.add(new S2CZhaoMingLightPayload.FxEntry(
                    f.id, f.ownerUuid, f.state.ordinal(), f.position.x, f.position.y, f.position.z));
        }
        var pkt = new S2CZhaoMingLightPayload(entries);
        for (ServerPlayer sp : level.players()) {
            PacketDistributor.sendToPlayer(sp, pkt);
        }
    }
}
