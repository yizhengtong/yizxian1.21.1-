package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.client.yiz.api.TargetFrameProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 会心一击锁定框供应商 — 从 EntityLockAPI 读客户端锁数据。
 */
public class CriticalStrikeProvider implements TargetFrameProvider {

    @Override
    public Entity getTarget(Player player) {
        var entry = EntityLockAPI.getClient();
        if (entry == null) return null;
        var cl = Minecraft.getInstance().level;
        if (cl == null) return null;
        for (var e : cl.entitiesForRendering()) {
            if (e != null && e.getUUID().equals(entry.targetUuid())) return e;
        }
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
