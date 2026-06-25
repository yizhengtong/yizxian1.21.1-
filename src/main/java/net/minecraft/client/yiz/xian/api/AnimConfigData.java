package net.minecraft.client.yiz.xian.api;

import com.google.gson.*;

import java.nio.file.*;
import java.util.*;

/**
 * 攻击动画 4 套配置 — 每套 7 关键帧（Catmull-Rom 样条插值），
 * 每帧含 rotation[3] + position[3] + scale。
 * 由 AnimConfigScreen 写入，BlockbenchAnimLoader 读取。
 */
public final class AnimConfigData {

    /** 每套动画 7 帧，每帧 7 个 float: rotX,rotY,rotZ, posX,posY,posZ, scale */
    public static final float[][][] KEYFRAMES = new float[4][7][7];

    static {
        // ── A 左→右平砍 (Anim 0): Z轴横扫 ──
        set(0, 0, 0,0,0, 0,0,0, 1);                    // 帧0 待机
        set(0, 1, 0,0,-15, 0.08f,0,0, 1);              // 帧1 蓄力(微抬)
        set(0, 2, 0,0,-30, 0.18f,0,0, 1);              // 帧2 起手
        set(0, 3, 0,0,-50, 0.28f,0,0, 1);              // 帧3 峰值(横扫)
        set(0, 4, 0,0,-25, 0.15f,0,0, 1);              // 帧4 过峰
        set(0, 5, 0,0,-5,  0.04f,0,0, 1);              // 帧5 收刀
        set(0, 6, 0,0,0,   0,0,0, 1);                   // 帧6 回位

        // ── B 右→左平砍 (Anim 1): X轴斩下（临时占位，待 Blockbench 替换） ──
        set(1, 0, -60,-20,0, 0,0.3f,0, 1);
        set(1, 1, -30,-10,0, 0,0.18f,0, 1);
        set(1, 2, 10,0,-3, 0,-0.05f,0, 1);
        set(1, 3, 15,0,-5, 0,-0.12f,0, 1);
        set(1, 4, 5,0,-2, 0,-0.05f,0, 1);
        set(1, 5, 0,0,0, 0,0,0, 1);
        set(1, 6, 0,0,0, 0,0,0, 1);

        // ── C 左下→左上挥砍 (Anim 2): Z+X 复合斜撩 ──
        set(2, 0, -20,0,-40, -0.15f,-0.1f,0, 1);
        set(2, 1, -5,0,-15, -0.05f,-0.03f,0, 1);
        set(2, 2, 10,0,5,  0.05f,0.05f,0, 1);
        set(2, 3, 18,0,22, 0.18f,0.12f,0, 1);
        set(2, 4, 8,0,10, 0.08f,0.05f,0, 1);
        set(2, 5, 0,0,0, 0,0,0, 1);
        set(2, 6, 0,0,0, 0,0,0, 1);

        // ── D 左上→右下挥砍 (Anim 3): Z轴反向横扫（临时占位，待 Blockbench 替换） ──
        set(3, 0, 0,0,0, 0,0,0, 1);                    // 帧0 待机
        set(3, 1, 0,0,15, -0.08f,0,0, 1);              // 帧1 蓄力(微抬)
        set(3, 2, 0,0,30, -0.18f,0,0, 1);              // 帧2 起手
        set(3, 3, 0,0,50, -0.28f,0,0, 1);              // 帧3 峰值(反向横扫)
        set(3, 4, 0,0,25, -0.15f,0,0, 1);              // 帧4 过峰
        set(3, 5, 0,0,5,  -0.04f,0,0, 1);              // 帧5 收刀
        set(3, 6, 0,0,0,   0,0,0, 1);                   // 帧6 回位
    }

    static void set(int anim, int frame, float rx, float ry, float rz,
                    float px, float py, float pz, float sc) {
        float[] f = KEYFRAMES[anim][frame];
        f[0]=rx; f[1]=ry; f[2]=rz; f[3]=px; f[4]=py; f[5]=pz; f[6]=sc;
    }

    // ── JSON 持久化（BlockbenchAnimLoader legacy 回退用）──
    private static final Path CONFIG_PATH = Path.of("config", "yizxianmod", "anim_attack.json");

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) return;
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_PATH)).getAsJsonObject();
            for (int i = 0; i < 4; i++) {
                JsonArray framesArr = root.getAsJsonArray("anim_" + i);
                int frameCount = Math.min(framesArr.size(), 7);
                for (int f = 0; f < frameCount; f++) {
                    JsonArray v = framesArr.get(f).getAsJsonArray();
                    int valCount = Math.min(v.size(), 7);
                    for (int k = 0; k < valCount; k++)
                        KEYFRAMES[i][f][k] = v.get(k).getAsFloat();
                }
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("_说明", "每套7关键帧(Catmull-Rom): [rotX,rotY,rotZ, posX,posY,posZ, scale] — /yizxian dh 1|2|3|4");
            for (int i = 0; i < 4; i++) {
                JsonArray framesArr = new JsonArray();
                for (int f = 0; f < 7; f++) {
                    JsonArray v = new JsonArray();
                    for (int k = 0; k < 7; k++) v.add(KEYFRAMES[i][f][k]);
                    framesArr.add(v);
                }
                root.add("anim_" + i, framesArr);
            }
            Path p = Path.of("").toAbsolutePath().resolve(CONFIG_PATH);
            Files.createDirectories(p.getParent());
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) {}
    }

    static { load(); }
}
