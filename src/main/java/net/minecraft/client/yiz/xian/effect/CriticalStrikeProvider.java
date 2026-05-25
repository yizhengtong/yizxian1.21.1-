package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.client.yiz.api.TargetFrameProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.yiz.tizMod;

/**
 * 会心一击锁定框供应商 — 从 EntityLockAPI 读客户端锁数据。
 */
public class CriticalStrikeProvider implements TargetFrameProvider {

    private int logCounter = 0;
    @Override
    public Entity getTarget(Player player) {
        var entry = EntityLockAPI.getClient();
        if (entry == null) {
            if (++logCounter % 40 == 0) tizMod.LOGGER.info("[PROV] no client entry");
            return null;
        }
        var cl = Minecraft.getInstance().level;
        if (cl == null) return null;
        for (var e : cl.entitiesForRendering()) {
            if (e != null && e.getUUID().equals(entry.targetUuid())) {
                if (++logCounter % 40 == 0) tizMod.LOGGER.info("[PROV] found target {} charge={}", e.getName().getString(), entry.charge());
                return e;
            }
        }
        if (++logCounter % 40 == 0) tizMod.LOGGER.info("[PROV] entry exists but target not in world");
        return null;
    }

    @Override
    public float getCharge() {
        var entry = EntityLockAPI.getClient();
        return entry != null ? entry.charge() : 0;
    }

    @Override
    public boolean isReady() {
        var entry = EntityLockAPI.getClient();
        return entry != null && entry.ready();
    }

    @Override
    public int getPriority() { return 10; }
}
