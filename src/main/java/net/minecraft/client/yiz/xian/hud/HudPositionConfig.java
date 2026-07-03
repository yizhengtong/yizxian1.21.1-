package net.minecraft.client.yiz.xian.hud;

import com.google.gson.*;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD 位置/缩放/显隐持久化（纯客户端 JSON）。
 *
 * <p>路径 {@code config/yizxianmod/hud_positions.json}，结构：
 * <pre>{@code
 * { "_version": 1,
 *   "huds": { "boost": {"x":150,"y":200,"enabled":true,"scale":1.5} } }
 * }</pre>
 * 读写模式仿 {@code AnimConfigData}。启动加载 + 编辑器 onClose/变更时存盘。</p>
 */
public final class HudPositionConfig {

    private static final Path PATH = Path.of("config", "yizxianmod", "hud_positions.json");

    private static final Map<String, Entry> HUDS = new ConcurrentHashMap<>();

    /** 单个 HUD 的位置条目。 */
    public record Entry(int x, int y, boolean enabled, float scale) {}

    private HudPositionConfig() {}

    public static Entry get(String id) { return HUDS.get(id); }

    public static void put(String id, int x, int y, boolean enabled, float scale) {
        HUDS.put(id, new Entry(x, y, enabled, scale));
        save();
    }

    /** 清空全部（重置为默认）。 */
    public static void clear() {
        HUDS.clear();
        save();
    }

    public static void load() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonObject huds = root.getAsJsonObject("huds");
            if (huds == null) return;
            for (String id : huds.keySet()) {
                JsonObject o = huds.getAsJsonObject(id);
                HUDS.put(id, new Entry(
                    o.get("x").getAsInt(),
                    o.get("y").getAsInt(),
                    o.has("enabled") ? o.get("enabled").getAsBoolean() : true,
                    o.has("scale") ? o.get("scale").getAsFloat() : 1f
                ));
            }
        } catch (Exception ignored) {
            // 损坏则用空（全部回退默认）
        }
    }

    public static void save() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            Files.createDirectories(p.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("_version", 1);
            JsonObject huds = new JsonObject();
            HUDS.forEach((id, e) -> {
                JsonObject o = new JsonObject();
                o.addProperty("x", e.x());
                o.addProperty("y", e.y());
                o.addProperty("enabled", e.enabled());
                o.addProperty("scale", e.scale());
                huds.add(id, o);
            });
            root.add("huds", huds);
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {
        }
    }
}
