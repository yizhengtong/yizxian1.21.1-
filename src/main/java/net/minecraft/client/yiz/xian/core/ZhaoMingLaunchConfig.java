package net.minecraft.client.yiz.xian.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 昭明法杖发射点偏移配置（纯客户端 JSON，可用 /yizxian zhaoming 指令调整）。
 * <p>偏移三参数：
 * <ul>
 *   <li>{@code forward} — 玩家身前距离（沿视线方向）</li>
 *   <li>{@code height} — 相对玩家脚底的高度</li>
 *   <li>{@code side} — 相对视线左右的偏移（左负右正）</li>
 * </ul>
 * 路径 {@code config/yizxianmod/zhaoming_launch.json}。</p>
 */
public final class ZhaoMingLaunchConfig {

    private static final Path PATH = Path.of("config", "yizxianmod", "zhaoming_launch.json");

    private static double forward = 0.4;
    private static double height = 0.0;
    private static double side = 0.0;

    private ZhaoMingLaunchConfig() {}

    public static double forward() { return forward; }
    public static double height() { return height; }
    public static double side() { return side; }

    public static void set(double f, double h, double s) {
        forward = f;
        height = h;
        side = s;
        save();
    }

    public static void shift(double df, double dh, double ds) {
        set(forward + df, height + dh, side + ds);
    }

    /** 计算发射点（玩家视线内部出发：眼睛位置 + 视线方向偏移，跟随视角低头/抬头）。
     *  初始轨迹朝准心（速度用 look 方向）。 */
    public static Vec3 launchPos(Player owner, Vec3 dir) {
        Vec3 look = dir.normalize();
        Vec3 sideVec = new Vec3(-look.z, 0, look.x);
        Vec3 up = look.cross(sideVec).normalize();
        return owner.getEyePosition(1.0f)
                .add(look.scale(forward))
                .add(sideVec.scale(side))
                .add(up.scale(height));
    }

    public static void load() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            if (!Files.exists(p)) return;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            forward = o.get("forward").getAsDouble();
            height = o.get("height").getAsDouble();
            side = o.get("side").getAsDouble();
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            Files.createDirectories(p.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("forward", forward);
            o.addProperty("height", height);
            o.addProperty("side", side);
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {}
    }
}
