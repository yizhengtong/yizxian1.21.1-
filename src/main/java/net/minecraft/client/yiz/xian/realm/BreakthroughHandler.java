package net.minecraft.client.yiz.xian.realm;

import net.minecraft.client.yiz.api.DaoPalaceAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.RealmStage;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 境界突破条件检测 — 对齐文档站蓝图。
 *
 * <h3>蓝图条件总览</h3>
 * <table>
 *   <tr><td>筑命</td><td>经验≥20级 + 道宫≥1座 + 天赋≥6</td></tr>
 *   <tr><td>谌我</td><td>经验≥30级 + 道宫≥576方块 + 天赋≥12 + 史诗≥1</td></tr>
 *   <tr><td>揖别</td><td>道宫≥2座 + 总规模≥32k方块 + 天赋≥24 + 传说≥2</td></tr>
 *   <tr><td>证我</td><td>道宫≥3座 + 每座≥64k方块 + 天赋≥64 + 神话≥6 + 传说≥12</td></tr>
 * </table>
 */
public final class BreakthroughHandler {

    private BreakthroughHandler() {}

    /**
     * 每 tick 调用一次，检测并触发突破。
     */
    public static void checkAndBreakthrough(ServerPlayer player) {
        RealmStage next = RealmProgressionAPI.getNextStage(player);
        if (next == null) return; // 已是证我，无法突破

        // 经验值
        int xpLevel = player.experienceLevel;

        // 道宫信息
        var palaces = DaoPalaceAPI.getPalaces(player);
        int palaceCount = palaces.size();
        int totalBlocks = palaces.stream().mapToInt(p -> p.currentVolume()).sum();

        // 天赋计数
        Map<String, Integer> talents = YizModQZKAPI.countTalentsByRarity(player);
        int totalTalents = talents.getOrDefault("total", 0);
        int mythic    = talents.getOrDefault("mythic", 0);
        int legendary = talents.getOrDefault("legendary", 0);
        int epic      = talents.getOrDefault("epic", 0);

        boolean canBreakthrough = switch (next.order()) {
            // 筑命: 经验20级 + 道宫≥1座 + 天赋≥6
            case 0 -> xpLevel >= 20 && palaceCount >= 1 && totalTalents >= 6;

            // 谌我: 经验30级 + 道宫≥576方块 + 天赋≥12 + 史诗≥1
            case 1 -> xpLevel >= 30 && totalBlocks >= 576 && totalTalents >= 12 && epic >= 1;

            // 揖别: 道宫≥2座 + 总规模≥32k方块 + 天赋≥24 + 传说≥2
            case 2 -> palaceCount >= 2 && totalBlocks >= 32000 && totalTalents >= 24 && legendary >= 2;

            // 证我: 道宫≥3座 + 每座≥64k方块 + 天赋≥64 + 神话≥6 + 传说≥12
            case 3 -> palaceCount >= 3
                && palaces.stream().allMatch(p -> p.currentVolume() >= 64000)
                && totalTalents >= 64
                && mythic >= 6
                && legendary >= 12;

            default -> false;
        };

        if (canBreakthrough) {
            RealmProgressionAPI.breakthrough(player, next.id());
        }
    }
}
