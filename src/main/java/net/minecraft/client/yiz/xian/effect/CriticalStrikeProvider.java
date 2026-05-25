package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.api.TargetFrameProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会心一击锁定框 — 客户端独立充能（60°锥 + 本地计时）。
 * 0秒=透明，1.25秒=50%，2.5秒=100%不透明。
 */
public class CriticalStrikeProvider implements TargetFrameProvider {

    private static final double RANGE = 32.0;
    private static final double CONE_DOT = 0.5;
    private static final int CHARGE_TICKS = 50; // 2.5秒

    // 每个玩家独立的锁定状态
    private static final ConcurrentHashMap<UUID, LockState> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_FRAME = new ConcurrentHashMap<>();

    private record LockState(UUID targetUuid, int timer) {}

    @Override
    public Entity getTarget(Player player) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;

        // 60° 锥扫描（复刻母模板）
        Vec3 eye = player.getEyePosition();
        var look = player.getLookAngle();
        Entity best = null;
        double bestDot = CONE_DOT;
        for (var e : mc.level.getEntities(player, player.getBoundingBox().inflate(RANGE),
                e -> e instanceof LivingEntity && e != player && e.isAlive())) {
            Vec3 to = e.position().subtract(eye);
            double d2 = to.lengthSqr();
            if (d2 > RANGE * RANGE) continue;
            double dot = look.dot(to) / Math.sqrt(d2);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }

        LockState state = STATES.get(player.getUUID());
        if (best == null) {
            STATES.remove(player.getUUID());
            LAST_FRAME.remove(player.getUUID());
            return null;
        }

        // 防止同帧多次调用（Manager + Renderer 各调一次）
        long frame = mc.level.getGameTime();
        Long prev = LAST_FRAME.get(player.getUUID());
        boolean sameFrame = prev != null && prev == frame;
        LAST_FRAME.put(player.getUUID(), frame);
        if (sameFrame) return best;

        int timer;
        if (state != null && state.targetUuid.equals(best.getUUID())) {
            timer = Math.min(state.timer + 1, CHARGE_TICKS);
        } else {
            timer = 1;
        }
        STATES.put(player.getUUID(), new LockState(best.getUUID(), timer));
        return best;
    }

    @Override
    public float getCharge() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        var state = STATES.get(mc.player.getUUID());
        return state != null ? (float) state.timer / CHARGE_TICKS : 0;
    }

    @Override
    public boolean isReady() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var state = STATES.get(mc.player.getUUID());
        return state != null && state.timer >= CHARGE_TICKS;
    }

    @Override
    public int getPriority() { return 10; }

    // 满蓄力用母模板2纹理
    private static final ResourceLocation[] READY_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_tr.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_tl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_bl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_br.png"),
    };

    @Override
    public ResourceLocation[] getCornerTextures() {
        return isReady() ? READY_TEX : null; // null=用默认
    }

    /** 攻击后重置 */
    public static void reset(Player player) {
        STATES.remove(player.getUUID());
    }
}
