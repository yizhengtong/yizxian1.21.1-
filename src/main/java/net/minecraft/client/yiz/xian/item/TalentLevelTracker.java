package net.minecraft.client.yiz.xian.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家天赋等级追踪器 — 内存 + NBT 持久化。
 */
public final class TalentLevelTracker {

    private static final ConcurrentHashMap<UUID, CompoundTag> DATA = new ConcurrentHashMap<>();

    private TalentLevelTracker() {}

    public static int getLevel(Player player, ResourceLocation effectId) {
        CompoundTag tag = DATA.get(player.getUUID());
        if (tag == null) return 0;
        return tag.getInt(effectId.toString());
    }

    public static void setLevel(Player player, ResourceLocation effectId, int level) {
        CompoundTag tag = DATA.computeIfAbsent(player.getUUID(), k -> new CompoundTag());
        String key = effectId.toString();
        if (level <= 0) {
            tag.remove(key);
            if (tag.isEmpty()) DATA.remove(player.getUUID());
        } else {
            tag.putInt(key, level);
        }
    }

    public static int increaseLevel(Player player, ResourceLocation effectId, int max) {
        int current = getLevel(player, effectId);
        int next = Math.min(current + 1, max);
        setLevel(player, effectId, next);
        return next;
    }

    public static int decreaseLevel(Player player, ResourceLocation effectId) {
        int current = getLevel(player, effectId);
        if (current <= 1) {
            setLevel(player, effectId, 0);
            return 0;
        }
        setLevel(player, effectId, current - 1);
        return current - 1;
    }

    /** 获取某玩家的持久化 NBT */
    public static CompoundTag save(Player player) {
        CompoundTag tag = DATA.get(player.getUUID());
        return tag != null ? tag.copy() : new CompoundTag();
    }

    /** 数据加载回内存 */
    public static void load(Player player, CompoundTag tag) {
        if (tag.isEmpty()) {
            DATA.remove(player.getUUID());
        } else {
            DATA.put(player.getUUID(), tag.copy());
        }
    }

    /** 清除玩家数据 */
    public static void clear(Player player) {
        DATA.remove(player.getUUID());
    }
}
