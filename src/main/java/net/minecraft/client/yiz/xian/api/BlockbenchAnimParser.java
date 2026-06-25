package net.minecraft.client.yiz.xian.api;

import com.google.gson.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Blockbench .bbmodel 动画解析器。
 *
 * <p>读取 Blockbench 导出的 .bbmodel JSON，提取骨骼旋转关键帧。
 * 运行时按 swing 进度线性插值输出 Euler 角。</p>
 */
public final class BlockbenchAnimParser {

    private BlockbenchAnimParser() {}

    /** 动画数量 */
    public static final int ANIM_COUNT = 4;

    /** 解析后的动画数据 */
    public static final AnimData[] ANIMS = new AnimData[ANIM_COUNT];

    /** 是否成功加载 */
    public static boolean loaded = false;

    /** 第一个 element 的旋转（度），用于把 2D 贴图转为 3D 朝向 */
    public static float elemRotX, elemRotY, elemRotZ;

    /**
     * 从 classpath 加载 .bbmodel。
     * @param resourcePath 如 "/assets/yizxianmod/models/animations/attack.bbmodel"
     */
    public static void load(String resourcePath) {
        try (InputStreamReader r = new InputStreamReader(
                BlockbenchAnimParser.class.getResourceAsStream(resourcePath),
                StandardCharsets.UTF_8)) {
            if (r == null) {
                System.err.println("[yizxianmod] .bbmodel not found: " + resourcePath);
                return;
            }
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

            // 提取骨骼 origin（枢轴点），key=bones UUID
            Map<String, float[]> origins = new HashMap<>();
            JsonArray groups = root.getAsJsonArray("groups");
            if (groups != null) {
                for (int i = 0; i < groups.size(); i++) {
                    JsonObject g = groups.get(i).getAsJsonObject();
                    String uuid = g.get("uuid").getAsString();
                    JsonArray origArr = g.getAsJsonArray("origin");
                    if (origArr != null && origArr.size() >= 3) {
                        origins.put(uuid, new float[]{
                            origArr.get(0).getAsFloat(),
                            origArr.get(1).getAsFloat(),
                            origArr.get(2).getAsFloat()
                        });
                    }
                }
            }

            // 提取第一个 element 的旋转（用于 2D→3D 朝向转换）
            float[] elemRot = new float[]{0, 0, 0};
            float[] elemPivot = new float[]{0, 0, 0};
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements != null && elements.size() > 0) {
                JsonObject elem = elements.get(0).getAsJsonObject();
                JsonArray er = elem.getAsJsonArray("rotation");
                if (er != null && er.size() >= 3) {
                    elemRot[0] = er.get(0).getAsFloat();
                    elemRot[1] = er.get(1).getAsFloat();
                    elemRot[2] = er.get(2).getAsFloat();
                }
                JsonArray ep = elem.getAsJsonArray("local_pivot");
                if (ep != null && ep.size() >= 3) {
                    elemPivot[0] = ep.get(0).getAsFloat();
                    elemPivot[1] = ep.get(1).getAsFloat();
                    elemPivot[2] = ep.get(2).getAsFloat();
                }
            }
            System.out.printf("[yizxianmod] Element rotation: [%.0f, %.0f, %.0f]%n",
                elemRot[0], elemRot[1], elemRot[2]);
            elemRotX = elemRot[0]; elemRotY = elemRot[1]; elemRotZ = elemRot[2];

            JsonArray animsArr = root.getAsJsonArray("animations");
            if (animsArr == null || animsArr.size() == 0) {
                System.err.println("[yizxianmod] .bbmodel has 0 animations");
                return;
            }

            int count = Math.min(animsArr.size(), ANIM_COUNT);
            for (int i = 0; i < count; i++) {
                JsonObject animObj = animsArr.get(i).getAsJsonObject();
                ANIMS[i] = parseAnimation(animObj, origins);
            }
            // 不足 ANIM_COUNT 个则复用最后一个
            for (int i = count; i < ANIM_COUNT; i++) {
                ANIMS[i] = ANIMS[count - 1];
            }
            loaded = true;
            System.out.println("[yizxianmod] Loaded " + count + " animations from " + resourcePath);
        } catch (Exception e) {
            System.err.println("[yizxianmod] Failed to load .bbmodel: " + e.getMessage());
        }
    }

    private static AnimData parseAnimation(JsonObject animObj, Map<String, float[]> origins) {
        String name = animObj.get("name").getAsString();
        float length = animObj.get("length").getAsFloat();

        List<float[]> times = new ArrayList<>();
        List<float[]> rots  = new ArrayList<>();
        float[] pivot = new float[]{0, 0, 0}; // 默认无枢轴

        JsonObject animators = animObj.getAsJsonObject("animators");
        for (String boneUuid : animators.keySet()) {
            // 提取枢轴
            float[] orig = origins.get(boneUuid);
            if (orig != null) { pivot[0] = orig[0]; pivot[1] = orig[1]; pivot[2] = orig[2]; }

            JsonObject bone = animators.getAsJsonObject(boneUuid);
            JsonArray kfs = bone.getAsJsonArray("keyframes");
            if (kfs == null) continue;

            for (int i = 0; i < kfs.size(); i++) {
                JsonObject kf = kfs.get(i).getAsJsonObject();
                String channel = kf.get("channel").getAsString();
                if (!"rotation".equals(channel)) continue;

                float time = kf.get("time").getAsFloat();
                JsonArray dp = kf.getAsJsonArray("data_points");
                if (dp == null || dp.size() == 0) continue;
                JsonObject pt = dp.get(0).getAsJsonObject();
                float rx = pt.get("x").getAsFloat();
                float ry = pt.get("y").getAsFloat();
                float rz = pt.get("z").getAsFloat();

                times.add(new float[]{time});
                rots.add(new float[]{rx, ry, rz});
            }
            break;
        }

        // 按时间排序
        Integer[] idx = new Integer[times.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(a -> times.get(a)[0]));

        AnimData data = new AnimData();
        data.name = name;
        data.length = length;
        data.pivotX = pivot[0]; data.pivotY = pivot[1]; data.pivotZ = pivot[2];
        data.times = new float[times.size()];
        data.rotX = new float[times.size()];
        data.rotY = new float[times.size()];
        data.rotZ = new float[times.size()];

        for (int i = 0; i < idx.length; i++) {
            int j = idx[i];
            data.times[i] = times.get(j)[0];
            data.rotX[i] = rots.get(j)[0];
            data.rotY[i] = rots.get(j)[1];
            data.rotZ[i] = rots.get(j)[2];
        }

        System.out.printf("[yizxianmod]   anim '%s': %.3fs, %d keyframes%n",
            name, length, data.times.length);
        return data;
    }

    /**
     * 按 swing 进度 (0→1) 插值输出 Euler 角。
     * @param animIdx 动画索引 0~{@link #ANIM_COUNT}-1
     * @param swing   攻击进度 0→1
     * @param outRot  输出 float[3] = {rotX, rotY, rotZ} 单位度
     */
    public static void getRotation(int animIdx, float swing, float[] outRot) {
        if (!loaded || animIdx < 0 || animIdx >= ANIM_COUNT) {
            outRot[0] = outRot[1] = outRot[2] = 0;
            return;
        }
        AnimData a = ANIMS[animIdx];
        float time = swing * a.length;

        int n = a.times.length;
        if (n == 0) { outRot[0]=outRot[1]=outRot[2]=0; return; }
        if (n == 1 || time <= a.times[0]) {
            outRot[0]=a.rotX[0]; outRot[1]=a.rotY[0]; outRot[2]=a.rotZ[0];
            return;
        }
        if (time >= a.times[n-1]) {
            outRot[0]=a.rotX[n-1]; outRot[1]=a.rotY[n-1]; outRot[2]=a.rotZ[n-1];
            return;
        }

        // 二分查找 time 所在区间
        int lo = 0, hi = n - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) / 2;
            if (a.times[mid] <= time) lo = mid;
            else hi = mid;
        }

        float t = (time - a.times[lo]) / (a.times[hi] - a.times[lo]);
        outRot[0] = lerp(a.rotX[lo], a.rotX[hi], t);
        outRot[1] = lerp(a.rotY[lo], a.rotY[hi], t);
        outRot[2] = lerp(a.rotZ[lo], a.rotZ[hi], t);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** 单条动画数据 */
    public static class AnimData {
        public String name;
        public float length;
        /** 骨骼枢轴点（Blockbench Bedrock 单位，除以 16 得方块单位） */
        public float pivotX, pivotY, pivotZ;
        public float[] times;
        public float[] rotX, rotY, rotZ;
    }
}
