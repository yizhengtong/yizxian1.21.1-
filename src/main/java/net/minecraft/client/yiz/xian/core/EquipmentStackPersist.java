package net.minecraft.client.yiz.xian.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装备叠层持久化 — 存档目录下的 JSON 文件。
 *
 * <p>存于 {@code saves/<存档>/yizmodqzk/equipment-stacks.json}（本模组专用子目录），
 * 天然按存档隔离（删档=数据随之消失，同名新档=真干净），且可直接查看/编辑 JSON。
 * 取代 PlayerDataAPI/AttachmentType（受 codec 类型约束、不易排查）。</p>
 *
 * <p>结构：{@code { "<playerUUID>": { "<equipId>": [6槽层数...], ... }, ... }}</p>
 */
public final class EquipmentStackPersist {
    private static final Logger LOGGER = LoggerFactory.getLogger("EquipmentStackPersist");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 存档内专用子目录（堆叠覆盖表同款约定） */
    private static final String DIR_NAME = "yizmodqzk";
    private static final String FILE_NAME = "equipment-stacks.json";

    /** UUID → equipId → 6 槽层数 */
    private static final Map<UUID, Map<String, int[]>> DATA = new ConcurrentHashMap<>();

    private EquipmentStackPersist() {}

    /** 当前存档目录（null = 无存档）。 */
    @javax.annotation.Nullable
    private static File getWorldSaveDir() {
        try {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                return server.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent().toFile();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static File getFile() {
        File dir = getWorldSaveDir();
        return dir != null ? new File(dir, DIR_NAME + "/" + FILE_NAME) : null;
    }

    /** 从当前存档的 JSON 加载全部装备叠层数据。 */
    public static synchronized void load() {
        File file = getFile();
        if (file == null || !file.exists()) {
            DATA.clear();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            // {"uuid":{"guinsoo":[0,0,0,0,0,0],...},...}
            Map<String, Map<String, java.util.List<Number>>> raw = GSON.fromJson(reader,
                new com.google.gson.reflect.TypeToken<Map<String, Map<String, java.util.List<Number>>>>() {}.getType());
            DATA.clear();
            if (raw != null) {
                for (var e : raw.entrySet()) {
                    try {
                        UUID id = UUID.fromString(e.getKey());
                        Map<String, int[]> eqMap = new ConcurrentHashMap<>();
                        if (e.getValue() != null) {
                            for (var eq : e.getValue().entrySet()) {
                                if (eq.getValue() != null) {
                                    int[] arr = new int[6];
                                    for (int i = 0; i < 6 && i < eq.getValue().size(); i++) {
                                        arr[i] = Math.max(0, eq.getValue().get(i).intValue());
                                    }
                                    eqMap.put(eq.getKey(), arr);
                                }
                            }
                        }
                        DATA.put(id, eqMap);
                    } catch (IllegalArgumentException ignored) {}
                }
                LOGGER.info("Loaded equipment stacks from {} ({} players)", FILE_NAME, DATA.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load equipment stacks from {}", file, e);
        }
    }

    /** 写入当前存档的 JSON。 */
    private static synchronized void save() {
        File file = getFile();
        if (file == null) return;
        try {
            file.getParentFile().mkdirs();
            Map<String, Map<String, java.util.List<Integer>>> out = new java.util.TreeMap<>();
            DATA.forEach((uuid, equips) -> {
                Map<String, java.util.List<Integer>> eqOut = new java.util.TreeMap<>();
                equips.forEach((k, v) -> {
                    java.util.List<Integer> list = new java.util.ArrayList<>(6);
                    for (int i = 0; i < 6; i++) list.add(v[i]);
                    eqOut.put(k, list);
                });
                out.put(uuid.toString(), eqOut);
            });
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save equipment stacks to {}", file, e);
        }
    }

    /** 读取某玩家某装备的层数（没有则返回 null）。 */
    @javax.annotation.Nullable
    public static int[] get(UUID uuid, String equipId) {
        var eq = DATA.get(uuid);
        return eq != null ? eq.get(equipId) : null;
    }

    /** 写入某玩家某装备的层数并持久化。 */
    public static void put(UUID uuid, String equipId, int[] stacks) {
        int[] copy = stacks.clone();
        DATA.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(equipId, copy);
        save();
    }

    /** 移除某玩家的全部装备数据（死亡/退出清理）。 */
    public static void remove(UUID uuid) {
        DATA.remove(uuid);
        save();
    }

    // ══════════════════════════════════════════════════════════
    //  登录/登出钩子
    // ══════════════════════════════════════════════════════════

    /** 玩家登入时：加载文件 + 恢复该玩家数据到装备类。 */
    public static void onPlayerLogin(ServerPlayer sp) {
        load();
        UUID uuid = sp.getUUID();
        int[] guinsoo = get(uuid, "guinsoo");
        if (guinsoo != null) {
            net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem.loadFromPersist(sp, guinsoo);
        }
        int[] explorer = get(uuid, "explorer");
        if (explorer != null) {
            net.minecraft.client.yiz.xian.item.equipment.ExplorerVambraceItem.loadFromPersist(sp, explorer);
        }
    }

    /** 玩家登出时：把装备类当前层数写入 JSON。 */
    public static void onPlayerLogout(ServerPlayer sp) {
        UUID uuid = sp.getUUID();
        int[] guinsoo = net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem.snapshotStacks(sp);
        if (guinsoo != null) put(uuid, "guinsoo", guinsoo);
        int[] explorer = net.minecraft.client.yiz.xian.item.equipment.ExplorerVambraceItem.snapshotStacks(sp);
        if (explorer != null) put(uuid, "explorer", explorer);
        save();
    }
}
