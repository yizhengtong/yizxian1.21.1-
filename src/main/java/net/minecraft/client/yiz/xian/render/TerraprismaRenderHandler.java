package net.minecraft.client.yiz.xian.render;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.nio.file.*;
import java.util.*;

public final class TerraprismaRenderHandler {

    private static final Path CONFIG_PATH = Path.of("config", "yizxianmod", "terraprisma.json");

    private static final Map<UUID, BladeState[]> BLADES = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_CMD_TARGET = new HashMap<>();
    private static final Map<UUID, Long> CMD_TARGET_EXPIRE = new HashMap<>();
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();
    // ── 翅膀姿态参数（当前最爱配置）──
    static double behindBase   = 0.5;
    static double behindStep   = 0.05;
    static double spreadBase   = 0.35;
    static double spreadStep   = 0.0;
    static double bobAmplitude = 0.0;
    static double bobSpeed     = 0.0;
    static double bobPhaseStep = 0.05;
    static double swordLength  = 1.0;
    static double scaleX       = 1.15;
    static double scaleY       = 1.15;
    static double scaleZ       = 1.15;
    static double tipYOffset   = -3.05;
    static double tipExtendX   = 0.0;
    static boolean useEyeHeight = true;
    static double eyeYRatio    = 0.7;
    static double bodyYOffset  = 0.3;
    static double anchorX      = -0.1;
    static double anchorY      = -0.1;
    static double anchorZ      = 0.2;
    static double tiltAngle    = -90.0;
    static double targetRange  = 37.0;
    static int    glowLight    = 15728880;
    /** 伤害层开关 — /yizxian bypass 3 或 /yizxian bypass 4 独立切换 */
    public static boolean useTrueDamage   = true;  // ③ YizModQZKAPI.trueDamage
    public static boolean useModifyHealth = true;  // ④ EntityASMUtil.modifyHealth

    // ── 攻击参数 ──
    static int    firstStrikeDuration  = 7;
    static int    ellipseBlendTicks    = 6;
    static int    ellipseDuration      = 14;
    static int    piercePrepTicks      = 6;
    static int    pierceAttackTicks    = 8;
    static double pierceCircleRadius   = 5.0;
    static double pierceCircleY        = 3.0;
    static int    dispatchInterval     = 5;
    static int    maxAttackRounds      = 8;

    // ── 渲染偏移 ──
    static float renderScale    = 1.5f;
    static float renderTransX   = -0.5f;
    static float renderTransY   = -0.5f;
    static float renderTransZ   = -0.5f;
    static float renderYawOff   = 0f;
    static float renderPitchOff = 90f;
    static float renderRollOff  = 45f;

    private static long lastConfigMtime;

    private TerraprismaRenderHandler() {}

    // ═══════════════════════════════════════════════════════════
    // 内部类型
    // ═══════════════════════════════════════════════════════════

    record FlightPoint(Vec3 pos, float yaw, float pitch, float roll) {
        static final FlightPoint Z = new FlightPoint(Vec3.ZERO, 0, 0, 0);
        FlightPoint lerpTo(FlightPoint o, float t) {
            return new FlightPoint(
                pos.lerp(o.pos, t), Mth.rotLerp(t, yaw, o.yaw),
                Mth.rotLerp(t, pitch, o.pitch), Mth.rotLerp(t, roll, o.roll));
        }
    }

    static class FlightPlan {
        List<FlightPoint> steps;
        int idx;
        FlightPlan(List<FlightPoint> steps) { this.steps = steps; this.idx = 0; }
        FlightPoint next() { return idx < steps.size() ? steps.get(idx++) : null; }
        boolean done() { return idx >= steps.size(); }
        int remain() { return steps.size() - idx; }
    }

    static class OrbitCurve {
        final Vec3 pointA, pointB, planeNormal;
        final float curvature;
        final Vec3 center, major, minor, majorDir, minorDir;

        OrbitCurve(Vec3 pointA, Vec3 pointB, Vec3 planeNormal, float curvature) {
            this.pointA = pointA; this.pointB = pointB;
            this.planeNormal = planeNormal; this.curvature = curvature;
            this.major = pointB.subtract(pointA).scale(0.5);
            this.center = pointA.add(major);
            this.majorDir = major.normalize();
            Vec3 m = planeNormal.cross(majorDir).normalize();
            if (m.lengthSqr() < 1e-5) m = majorDir.cross(new Vec3(0,1,0)).normalize();
            this.minorDir = m;
            this.minor = minorDir.scale(major.length() * curvature);
        }

        Vec3 pointAt(float progress) {
            float bt = progress - 0.08f * Mth.sin(progress * Mth.TWO_PI);
            float theta = bt * Mth.TWO_PI;
            return center.add(major.scale(Math.cos(theta))).add(minor.scale(Math.sin(theta)));
        }
        Vec3 center() { return center; }

        static Vec3 randomNormal(net.minecraft.util.RandomSource rng, Vec3 a, Vec3 b) {
            Vec3 md = b.subtract(a).normalize();
            Vec3 rv = new Vec3(rng.nextDouble()-0.5, rng.nextDouble()-0.5, rng.nextDouble()-0.5).normalize();
            Vec3 pn = rv.subtract(md.scale(rv.dot(md))).normalize();
            if (pn.lengthSqr() < 1e-5) pn = md.cross(new Vec3(0,1,0)).normalize();
            if (pn.lengthSqr() < 1e-5) pn = new Vec3(1,0,0);
            return pn;
        }
    }

    enum BladeMode { IDLE_WING, RISING, ASSAULT, RETURNING }

    static final class BladeState {
        final int index;
        BladeMode mode = BladeMode.IDLE_WING;
        FlightPlan flightPlan;
        FlightPoint curNode = FlightPoint.Z;
        FlightPoint interpFrom = FlightPoint.Z;
        FlightPoint interpTo   = FlightPoint.Z;
        float interpT;
        UUID targetId;
        Vec3 worldPos;
        Vec3 velocity = Vec3.ZERO;
        float timeInMode;
        int roundCount;
        Vec3 memoPos = Vec3.ZERO;
        Map<UUID, Long> hitCooldowns = new HashMap<>();
        /** 调试标签: A=翅膀, B=上升, C=俯冲, D=椭圆, E=穿刺, F=归巢 */
        String phaseLabel = "A";
        /** 召唤来源等级 (1..5)，决定伤害系数 */
        int sourceLevel = 1;

        BladeState(int index) { this.index = index; }

        void enterRising(UUID target, Vec3 from) {
            mode = BladeMode.RISING; timeInMode = 0; targetId = target;
            worldPos = from; roundCount = 0;
            memoPos = Vec3.ZERO; flightPlan = null;
            curNode = new FlightPoint(from, 0, 0, 0);
            phaseLabel = "B";
        }
        void executePlan(FlightPlan plan) {
            mode = BladeMode.ASSAULT; timeInMode = 0; flightPlan = plan;
            curNode = FlightPoint.Z;
        }
        void returnHome() {
            mode = BladeMode.RETURNING; timeInMode = 0; targetId = null;
            roundCount = 0; flightPlan = null;
            phaseLabel = "F";
        }
        // 轨迹姿态提取
        float getYaw()   { return curNode.yaw; }
        float getPitch() { return curNode.pitch; }
        Vec3 velocity() {
            if (velocity.lengthSqr() > 1e-5) return velocity;
            return Vec3.directionFromRotation(curNode.pitch, -curNode.yaw).normalize();
        }
        Vec3 bladeUp() {
            org.joml.Quaternionf q = new org.joml.Quaternionf()
                .rotateY((float)Math.toRadians(-curNode.yaw))
                .rotateX((float)Math.toRadians(curNode.pitch))
                .rotateZ((float)Math.toRadians(curNode.roll));
            org.joml.Vector3f up = new org.joml.Vector3f(0,1,0).rotate(q);
            return new Vec3(up.x(), up.y(), up.z()).normalize();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 姿态解算（完全复用 Servantry getEulerNode）
    // ═══════════════════════════════════════════════════════════

    static FlightPoint resolveRotation(Vec3 pos, Vec3 tipDir, Vec3 bladeNormal) {
        if (tipDir.lengthSqr() < 1e-4) tipDir = new Vec3(0, 0, 1);
        tipDir = tipDir.normalize();
        float yaw = (float)(Math.atan2(-tipDir.x, tipDir.z) * (180.0 / Math.PI));
        double horiz = Math.sqrt(tipDir.x * tipDir.x + tipDir.z * tipDir.z);
        float pitch = (float)(Math.atan2(-tipDir.y, horiz) * (180.0 / Math.PI));
        Vec3 refUp = new Vec3(0,1,0).xRot((float)Math.toRadians(pitch)).yRot((float)Math.toRadians(yaw));
        Vec3 projNorm = bladeNormal.subtract(tipDir.scale(bladeNormal.dot(tipDir))).normalize();
        if (projNorm.lengthSqr() < 1e-4) projNorm = refUp;
        double dot = refUp.dot(projNorm);
        Vec3 cross = refUp.cross(projNorm);
        float roll = (float)(Math.atan2(cross.dot(tipDir), dot) * (180.0 / Math.PI));
        return new FlightPoint(pos, yaw, pitch, roll);
    }

    static Vec3 bezier3(float t, Vec3... P) {
        if (P.length == 0) return Vec3.ZERO;
        if (P.length == 1) return P[0];
        Vec3[] pts = P.clone();
        for (int k = P.length-1; k > 0; k--)
            for (int i = 0; i < k; i++)
                pts[i] = pts[i].lerp(pts[i+1], t);
        return pts[0];
    }

    // ═══════════════════════════════════════════════════════════
    // 攻击路径规划（完全复用 TerraprismAttackGoal）
    // ═══════════════════════════════════════════════════════════

    private static void planFirstStrike(BladeState blade, LivingEntity target) {
        Vec3 start = blade.worldPos;
        // 目标身体中心 → 穿过目标到达后方
        Vec3 bodyCenter = target.getBoundingBox().getCenter();
        Vec3 approachDir = bodyCenter.subtract(start);
        if (approachDir.lengthSqr() < 1e-4) approachDir = new Vec3(0, 0, 1);
        approachDir = approachDir.normalize();
        // 穿过身体后继续延伸 3 格
        Vec3 through = bodyCenter.add(approachDir.scale(3.0));
        Vec3 planeN = approachDir.cross(new Vec3(0, 1, 0)).normalize();
        if (planeN.lengthSqr() < 1e-4) planeN = new Vec3(1, 0, 0);
        List<FlightPoint> steps = new ArrayList<>();
        double dist = start.distanceTo(through);
        int dur = Math.max(firstStrikeDuration, (int)(dist / 1.5));
        for (int i = 1; i <= dur; i++) {
            float t = (float) i / dur;
            t = t * t;
            steps.add(resolveRotation(start.lerp(through, t), approachDir, planeN));
        }
        blade.phaseLabel = "C";
        blade.executePlan(new FlightPlan(steps));
    }

    private static void planEllipseSlash(BladeState blade, Player owner) {
        Vec3 curPos = blade.worldPos;
        Vec3 T = blade.memoPos;
        float rAng = owner.getRandom().nextFloat() * Mth.TWO_PI;
        float rY = 0.5f + owner.getRandom().nextFloat() * 2.5f;
        Vec3 farPt = T.add(Math.cos(rAng) * 5, rY, Math.sin(rAng) * 5);
        Vec3 planeN = OrbitCurve.randomNormal(owner.getRandom(), T, farPt);
        OrbitCurve ellipse = new OrbitCurve(T, farPt, planeN, 0.75f);
        Vec3 curTip = Vec3.directionFromRotation(blade.getPitch(), blade.getYaw()).normalize();
        Vec3 curUp = blade.bladeUp();

        List<FlightPoint> steps = new ArrayList<>();
        int total = ellipseDuration;
        for (int i = 1; i <= total; i++) {
            float p = (float) i / total;
            Vec3 ep = ellipse.pointAt(p);
            Vec3 td = ep.subtract(ellipse.center()).normalize();
            if (i <= ellipseBlendTicks) {
                float d = (float) i / ellipseBlendTicks;
                float s = d * d * (3f - 2f * d);
                Vec3 pos = curPos.lerp(ep, s);
                Vec3 bTip = curTip.lerp(td, s);
                Vec3 bUp = curUp.lerp(planeN, s);
                steps.add(resolveRotation(pos, bTip, bUp));
            } else {
                steps.add(resolveRotation(ep, td, planeN));
            }
        }
        blade.phaseLabel = "D";
        blade.executePlan(new FlightPlan(steps));
    }

    private static void planPierceSlash(BladeState blade, LivingEntity target) {
        Vec3 startPos = blade.worldPos;
        Vec3 center = new Vec3(target.getX(), target.getY() + pierceCircleY, target.getZ());
        Vec3 toStart = startPos.subtract(center);
        double ang = Math.atan2(toStart.z, toStart.x);
        Vec3 prepPos = center.add(Math.cos(ang) * pierceCircleRadius, 0, Math.sin(ang) * pierceCircleRadius);
        Vec3 endPos = target.getBoundingBox().getCenter().offsetRandom(target.getRandom(), 0.75f);
        Vec3 atkDir = endPos.subtract(prepPos);
        if (atkDir.lengthSqr() < 1e-5) atkDir = new Vec3(0, -1, 0);
        endPos = endPos.add(atkDir.normalize().scale(3));

        Vec3 curVel = blade.velocity();
        Vec3 curTip = Vec3.directionFromRotation(blade.getPitch(), blade.getYaw()).normalize();
        Vec3 curUp = blade.bladeUp();

        List<FlightPoint> steps = new ArrayList<>();
        for (int i = 1; i <= piercePrepTicks; i++) {
            float d = (float) i / piercePrepTicks;
            d = d * d * (3f - 2f * d);
            Vec3 pt = bezier3(d, startPos, startPos.add(curVel), prepPos);
            Vec3 td = curTip.lerp(atkDir, d);
            steps.add(resolveRotation(pt, td, curUp));
        }
        for (int i = 1; i <= pierceAttackTicks; i++) {
            float d = (float) i / pierceAttackTicks;
            d = d * d * (3f - 2f * d);
            Vec3 pt = prepPos.lerp(endPos, d);
            steps.add(resolveRotation(pt, atkDir, curUp));
        }
        blade.phaseLabel = "E";
        blade.executePlan(new FlightPlan(steps));
    }

    private static void applyPosCorrection(BladeState blade, LivingEntity target) {
        Vec3 curCenter = target.getBoundingBox().getCenter();
        // 首次调用 memoPos=ZERO → 只记录位置，不偏移（否则整条轨迹被 shift 到万里之外）
        if (blade.memoPos.lengthSqr() < 1e-5) {
            blade.memoPos = curCenter;
            return;
        }
        Vec3 offset = curCenter.subtract(blade.memoPos);
        if (offset.lengthSqr() > 1e-5 && blade.flightPlan != null) {
            List<FlightPoint> nodes = blade.flightPlan.steps;
            int start = blade.flightPlan.idx;
            int rem = nodes.size() - start;
            for (int i = 0; i < rem; i++) {
                float w = (float)(i + 1) / rem;
                FlightPoint old = nodes.get(start + i);
                nodes.set(start + i, new FlightPoint(old.pos.add(offset.scale(w)), old.yaw, old.pitch, old.roll));
            }
        }
        blade.memoPos = curCenter;
    }

    // ═══════════════════════════════════════════════════════════
    // 配置加载
    // ═══════════════════════════════════════════════════════════

    private static void reloadConfig() {
        try {
            Path path = Path.of("").toAbsolutePath().resolve(CONFIG_PATH);
            if (!Files.exists(path)) return;
            long mtime = Files.getLastModifiedTime(path).toMillis();
            if (mtime == lastConfigMtime) return;
            lastConfigMtime = mtime;
            String raw = Files.readString(path);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

            JsonObject pos = root.getAsJsonObject("八字排列");
            behindBase = d(pos, "behindBase", behindBase);
            behindStep = d(pos, "behindStep", behindStep);
            spreadBase = d(pos, "spreadBase", spreadBase);
            spreadStep = d(pos, "spreadStep", spreadStep);

            JsonObject bob = root.getAsJsonObject("浮动动画");
            bobAmplitude = d(bob, "amplitude", bobAmplitude);
            bobSpeed     = d(bob, "speed", bobSpeed);
            bobPhaseStep = d(bob, "phaseStep", bobPhaseStep);

            JsonObject sz = root.getAsJsonObject("剑身尺寸");
            swordLength = d(sz, "length", swordLength);
            scaleX = d(sz, "scaleX", scaleX);
            scaleY = d(sz, "scaleY", scaleY);
            scaleZ = d(sz, "scaleZ", scaleZ);

            JsonObject tip = root.getAsJsonObject("剑尖默认朝向");
            tipYOffset = d(tip, "yOffset", tipYOffset);
            tipExtendX = d(tip, "extendX", tipExtendX);

            JsonObject ref = root.getAsJsonObject("玩家参考点");
            useEyeHeight = b(ref, "useEyeHeight", true);
            eyeYRatio    = d(ref, "eyeYRatio", eyeYRatio);
            bodyYOffset  = d(ref, "bodyYOffset", bodyYOffset);

            JsonObject anc = root.getAsJsonObject("锚点偏移");
            anchorX = d(anc, "x", anchorX);
            anchorY = d(anc, "y", anchorY);
            anchorZ = d(anc, "z", anchorZ);

            JsonObject tilt = root.getAsJsonObject("倾斜与朝向");
            tiltAngle = d(tilt, "angle", tiltAngle);

            JsonObject ai = root.getAsJsonObject("索敌");
            targetRange = d(ai, "range", targetRange);

            JsonObject gl = root.getAsJsonObject("发光");
            glowLight = i(gl, "brightness", glowLight);
        } catch (Exception ignored) {}
    }

    private static double d(JsonObject o, String key, double def) {
        try { return o.get(key).getAsDouble(); } catch (Exception e) { return def; }
    }
    private static int i(JsonObject o, String key, int def) {
        try { return o.get(key).getAsInt(); } catch (Exception e) { return def; }
    }
    private static boolean b(JsonObject o, String key, boolean def) {
        try { return o.get(key).getAsBoolean(); } catch (Exception e) { return def; }
    }

    // ═══════════════════════════════════════════════════════════
    // 渲染入口
    // ═══════════════════════════════════════════════════════════

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        reloadConfig();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // 检测玩家主动攻击目标
        updateCommandTarget(mc);
        var players = mc.level.players();
        if (players.isEmpty()) return;

        PoseStack stack = event.getPoseStack();
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        ItemRenderer ri = mc.getItemRenderer();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera cam = mc.gameRenderer.getMainCamera();
        double cx = cam.getPosition().x, cy = cam.getPosition().y, cz = cam.getPosition().z;
        long gameTime = mc.level.getGameTime();

        Set<UUID> alive = new HashSet<>();
        for (Player p : players) alive.add(p.getUUID());
        BLADES.keySet().retainAll(alive);

        for (Player player : players) {
            // 玩家死亡复活 → 重置所有剑状态和目标
            UUID puid = player.getUUID();
            if (!player.isAlive()) { DEAD_PLAYERS.add(puid); continue; }
            if (DEAD_PLAYERS.remove(puid)) {
                BLADES.remove(puid);
                PLAYER_CMD_TARGET.remove(puid);
                CMD_TARGET_EXPIRE.clear();
            }
            // 扫描全部泰拉棱镜卷轴 → 总剑数 = 所有卷轴 NBT sword_count 之和
            List<ItemStack> scrolls = findAllScrolls(player);
            if (scrolls.isEmpty()) { BLADES.remove(puid); continue; }
            int count = 0;
            int maxLevel = 0;
            List<Integer> sourcePool = new ArrayList<>();
            for (ItemStack s : scrolls) {
                if (s.getItem() instanceof net.minecraft.client.yiz.xian.item.TerraprismaScrollItem tsi) {
                    int lv = tsi.getLevel();
                    maxLevel = Math.max(maxLevel, lv);
                    int n = net.minecraft.client.yiz.xian.item.TerraprismaScrollItem.getSwordCount(s);
                    for (int j = 0; j < n; j++) sourcePool.add(lv);
                    count += n;
                }
            }
            if (count <= 0) { BLADES.remove(puid); continue; }
            final int bladeCount = count;

            double px = Mth.lerp(partial, player.xo, player.getX());
            double py = Mth.lerp(partial, player.yo, player.getY());
            double pz = Mth.lerp(partial, player.zo, player.getZ());
            float yrDeg = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);

            BladeState[] blades = BLADES.computeIfAbsent(puid, k -> new BladeState[bladeCount]);
            if (blades.length != bladeCount) {
                // 缩容时：优先移除 sourceLevel 与 sourcePool 计数不符的 IDLE 剑
                BladeState[] nw = new BladeState[bladeCount];
                int wi = 0;
                boolean[] keep = new boolean[blades.length];
                // 统计当前各等级存活数
                int[] curLvCount = new int[6]; // 1..5
                for (BladeState b : blades) if (b != null) curLvCount[b.sourceLevel]++;
                int[] tgtLvCount = new int[6];
                for (int lv : sourcePool) tgtLvCount[lv]++;
                // 优先保留与 sourcePool 匹配的，多余 IDLE 的丢弃
                int[] kept = new int[6];
                for (int i = 0; i < blades.length && wi < bladeCount; i++) {
                    BladeState b = blades[i];
                    if (b == null) continue;
                    if (kept[b.sourceLevel] < tgtLvCount[b.sourceLevel] || b.mode != BladeMode.IDLE_WING) {
                        keep[i] = true;
                        kept[b.sourceLevel]++;
                        wi++;
                    }
                }
                // 还不够 → 从被丢弃的中补
                for (int i = 0; i < blades.length && wi < bladeCount; i++) {
                    if (!keep[i] && blades[i] != null) { keep[i] = true; wi++; }
                }
                wi = 0;
                for (int i = 0; i < blades.length; i++) {
                    if (keep[i] && blades[i] != null) nw[wi++] = blades[i];
                }
                blades = nw; BLADES.put(puid, blades);
            }
            for (int i = 0; i < bladeCount; i++) {
                if (blades[i] == null) {
                    blades[i] = new BladeState(i);
                    // 新 blade 从 sourcePool 取来源等级（分配后剔除）
                    if (!sourcePool.isEmpty()) blades[i].sourceLevel = sourcePool.remove(0);
                }
            }

            runScheduler(player, blades, gameTime, px, py, pz, yrDeg, sourcePool);

            ItemStack renderScroll = scrolls.get(0); // 同纹理，取第一个用于渲染
            for (int i = 0; i < bladeCount; i++) {
                BladeState b = blades[i];
                if (b.mode == BladeMode.IDLE_WING) {
                    renderIdleWing(stack, buffer, ri, renderScroll, player, px, py, pz, yrDeg, i, gameTime + partial, cx, cy, cz, b.sourceLevel);
                } else {
                    renderFlightBlade(stack, buffer, ri, renderScroll, player, b, partial, cx, cy, cz);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 调度器
    // ═══════════════════════════════════════════════════════════

    private static void runScheduler(Player player, BladeState[] blades,
                                      long gameTime, double px, double py, double pz, float yrDeg,
                                      List<Integer> sourcePool) {
        List<LivingEntity> hostiles = scanHostiles(player);
        UUID cmdId = PLAYER_CMD_TARGET.get(player.getUUID());
        LivingEntity cmdTgt = cmdId != null ? findTarget(player, cmdId) : null;
        if (cmdTgt != null && !cmdTgt.isAlive()) cmdTgt = null;

        // 构建目标列表：指令目标 + 所有敌对生物
        List<UUID> targetIds = new ArrayList<>();
        List<Integer> quotas = new ArrayList<>();
        int total = blades.length;

        if (hostiles.isEmpty() && cmdTgt == null) {
            for (BladeState b : blades) {
                if (b.mode != BladeMode.IDLE_WING && b.mode != BladeMode.RETURNING) b.returnHome();
            }
        } else {
            // 收集全部敌对目标（去重），按 HP 降序
            Set<UUID> seen = new HashSet<>();
            List<LivingEntity> allTargets = new ArrayList<>();
            if (cmdTgt != null && cmdTgt.isAlive()) { allTargets.add(cmdTgt); seen.add(cmdTgt.getUUID()); }
            for (LivingEntity h : hostiles) {
                if (seen.add(h.getUUID())) allTargets.add(h);
            }
            // 按 HP 降序排列（指令目标已在最前）
            if (allTargets.size() > 1 && cmdTgt != null) {
                LivingEntity first = allTargets.remove(0);
                allTargets.sort((a, b) -> Float.compare(b.getMaxHealth(), a.getMaxHealth()));
                allTargets.add(0, first);
            }
            int N = allTargets.size();
            if (N == 1) {
                targetIds.add(allTargets.get(0).getUUID());
                quotas.add(total);
            } else {
                int cmdQuota = 0, defQuota = 0, spreadQuota;
                if (cmdTgt != null && total > 5) {
                    cmdQuota = total * 30 / 100;  // 30% 指令目标
                }
                if (total > 5) {
                    defQuota = total * 20 / 100;   // 20% 近身防御
                }
                spreadQuota = total - cmdQuota - defQuota;
                // 分配指令目标
                if (cmdQuota > 0) {
                    targetIds.add(cmdTgt.getUUID());
                    quotas.add(cmdQuota);
                }
                // 防御：最近 3 只
                int defCount = Math.min(3, hostiles.size());
                int perDef = defCount > 0 ? defQuota / defCount : 0;
                for (int i = 0; i < defCount && i < hostiles.size(); i++) {
                    UUID id = hostiles.get(i).getUUID();
                    if (targetIds.contains(id)) continue;
                    targetIds.add(id);
                    quotas.add(i == defCount - 1 ? defQuota - perDef * (defCount - 1) : perDef);
                }
                if (defCount == 0 && defQuota > 0) { defQuota = 0; spreadQuota = total - cmdQuota; }
                // 均匀分配剩余
                int spreadCount = 0;
                for (LivingEntity h : allTargets) {
                    if (!targetIds.contains(h.getUUID())) spreadCount++;
                }
                if (spreadCount > 0) {
                    int perSpread = spreadQuota / spreadCount;
                    int acc = 0;
                    int cnt = 0;
                    for (LivingEntity h : allTargets) {
                        if (targetIds.contains(h.getUUID())) continue;
                        targetIds.add(h.getUUID());
                        cnt++;
                        int q = (cnt == spreadCount) ? spreadQuota - acc : Math.max(1, perSpread);
                        quotas.add(q);
                        acc += q;
                    }
                }
            }
            UUID[] ids = targetIds.toArray(UUID[]::new);
            int[] quota = quotas.stream().mapToInt(i -> i).toArray();
            int[] assigned = new int[ids.length];
            for (BladeState b : blades) {
                if (b.mode == BladeMode.IDLE_WING || b.mode == BladeMode.RETURNING) continue;
                int idx = indexOf(ids, b.targetId);
                if (idx >= 0) assigned[idx]++;
            }
            for (BladeState b : blades) {
                if (b.mode == BladeMode.IDLE_WING || b.mode == BladeMode.RETURNING) continue;
                LivingEntity tgt = findTarget(player, b.targetId);
                if (tgt == null || !tgt.isAlive()) {
                    int need = pickNeed(quota, assigned);
                    if (need >= 0) { b.targetId = ids[need]; assigned[need]++; }
                    else b.returnHome();
                }
            }
            // 批量出剑
            int dispatched = 0;
            for (int i = 0; i < ids.length && dispatched < total / 10 + 6; i++) {
                int need = pickNeed(quota, assigned);
                if (need < 0) break;
                for (BladeState b : blades) {
                    if (b.mode == BladeMode.IDLE_WING) {
                        b.enterRising(ids[need], wingWorldPos(player, px, py, pz, yrDeg, b.index, 1f));
                        // sourceLevel 在建剑时已分配，此处不覆写
                        assigned[need]++; dispatched++;
                        if (dispatched >= total / 10 + 6) break;
                    }
                }
            }
            // dispatch tracking removed (dead map)
        }

        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        for (BladeState b : blades) advanceOne(player, b, px, py, pz, yrDeg, dt, partial());
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ═══════════════════════════════════════════════════════════
    // 单剑推进
    // ═══════════════════════════════════════════════════════════

    private static void advanceOne(Player player, BladeState b, double px, double py, double pz,
                                    float yrDeg, float dt, float partial) {
        b.timeInMode += dt;

        switch (b.mode) {
            case IDLE_WING -> {}

            case RISING -> {
                LivingEntity tgt = findTarget(player, b.targetId);
                if (tgt == null) { b.returnHome(); return; }
                Vec3 apex = new Vec3(px + pseudoRand(b.index,0)*0.5,
                    py + player.getEyeHeight() + 2.5, pz + pseudoRand(b.index,1)*0.5);
                if (b.flightPlan == null) {
                    // 贝塞尔弧线飞向头顶 apex
                    double sideOff = pseudoRand(b.index, 5) * 1.8;
                    double fwdOff  = pseudoRand(b.index, 6) * 1.2;
                    float bodyRad = (float) Math.toRadians(yrDeg);
                    Vec3 ctrl = new Vec3(px + sideOff*Math.cos(bodyRad)+fwdOff*Math.sin(bodyRad),
                        py + player.getEyeHeight()+1.0 + pseudoRand(b.index,7)*2.0,
                        pz - sideOff*Math.sin(bodyRad)+fwdOff*Math.cos(bodyRad));
                    Vec3 tipDir = tgt.getEyePosition(1f).subtract(apex).normalize();
                    Vec3 pn = tipDir.cross(new Vec3(0,1,0)).normalize();
                    if (pn.lengthSqr() < 1e-4) pn = new Vec3(1,0,0);
                    List<FlightPoint> st = new ArrayList<>();
                    double riseDist = apex.distanceTo(b.worldPos);
                    int dur = Math.max(6 + b.index % 2, (int)(riseDist / 2.0));
                    for (int j = 1; j <= dur; j++) {
                        float t = (float) j / dur; t = t*t*(3f-2f*t);
                        st.add(resolveRotation(bezier3(t, b.worldPos, ctrl, apex), tipDir, pn));
                    }
                    b.flightPlan = new FlightPlan(st); b.timeInMode = 0;
                }
                int N = b.flightPlan.steps.size();
                float pr = Math.min(1f, b.timeInMode / N);
                if (pr >= 1f) {
                    // 抵达 apex → 从头顶飞向目标眼高，不穿地
                    planFirstStrike(b, tgt);
                    applyPosCorrection(b, tgt);
                    return;
                }
                double raw = pr * (N - 1);
                int lo = Math.min((int) raw, N - 1), hi = Math.min(lo + 1, N - 1);
                b.interpT = (float)(raw - lo);
                b.interpFrom = b.flightPlan.steps.get(lo);
                b.interpTo   = b.flightPlan.steps.get(hi);
                b.worldPos = b.interpFrom.pos.lerp(b.interpTo.pos, b.interpT);
            }

            case ASSAULT -> {
                if (b.flightPlan == null) { b.returnHome(); return; }
                // 每帧检查目标存活 → 已死则立即 DIVE 飞向下一个敌人
                LivingEntity tgt = findTarget(player, b.targetId);
                if (tgt == null || !tgt.isAlive()) {
                    List<LivingEntity> hs = scanHostiles(player);
                    if (!hs.isEmpty()) {
                        b.targetId = hs.get(0).getUUID();
                        planFirstStrike(b, hs.get(0));
                        applyPosCorrection(b, hs.get(0));
                    } else b.returnHome();
                    return;
                }
                int totalTicks = b.flightPlan.steps.size();
                float progress = Math.min(1f, b.timeInMode / totalTicks);
                if (progress >= 1f) {
                    b.roundCount++;
                    if (player.getRandom().nextDouble() < 0.5) planEllipseSlash(b, player);
                    else planPierceSlash(b, tgt);
                    applyPosCorrection(b, tgt);
                    b.timeInMode = 0;
                    return;
                }
                // 平滑分数阶插值
                int Nf = b.flightPlan.steps.size();
                double rawf = progress * (Nf - 1);
                int lof = Math.min((int) rawf, Nf - 1);
                int hif = Math.min(lof + 1, Nf - 1);
                b.interpT = (float)(rawf - lof);
                b.interpFrom = b.flightPlan.steps.get(lof);
                b.interpTo   = b.flightPlan.steps.get(hif);
                b.worldPos = b.interpFrom.pos.lerp(b.interpTo.pos, b.interpT);
                if (lof > 0) {
                    b.velocity = b.interpFrom.pos.subtract(b.flightPlan.steps.get(lof - 1).pos);
                    if (b.velocity.lengthSqr() > 1e-4) b.velocity = b.velocity.normalize();
                }
                // 碰撞伤害：剑路径上所有怪物均受伤
                tryDealDamage(b);
            }

            case RETURNING -> {
                // 归巢中途出现新敌人 → 直接切过去攻击
                List<LivingEntity> hs = scanHostiles(player);
                if (!hs.isEmpty() && b.targetId == null) {
                    b.enterRising(hs.get(0).getUUID(), b.worldPos);
                    return;
                }
                // DIVE 攻击玩家 → 穿过 → 立刻归翼
                Vec3 playerBody = new Vec3(px, py + player.getBbHeight() * 0.5, pz);
                Vec3 wingHome = wingWorldPos(player, px, py, pz, yrDeg, b.index, 1f);
                if (b.flightPlan == null) {
                    Vec3 dir = playerBody.subtract(b.worldPos).normalize();
                    Vec3 through = playerBody.add(dir.scale(0.5));
                    Vec3 pn = dir.cross(new Vec3(0,1,0)).normalize();
                    if (pn.lengthSqr() < 1e-4) pn = new Vec3(1,0,0);
                    double dist = b.worldPos.distanceTo(playerBody);
                    int durt = Math.max(7, (int)(dist / 1.5));
                    List<FlightPoint> st = new ArrayList<>();
                    for (int j = 1; j <= durt; j++) {
                        float t = (float) j / durt; t = t * t;
                        st.add(resolveRotation(b.worldPos.lerp(through, t), dir, pn));
                    }
                    b.flightPlan = new FlightPlan(st); b.timeInMode = 0;
                }
                int Nr = b.flightPlan.steps.size();
                float prr = Math.min(1f, b.timeInMode / Nr);
                if (prr >= 1f) {
                    b.worldPos = wingHome;
                    b.mode = BladeMode.IDLE_WING;
                    b.phaseLabel = "A";
                    b.timeInMode = 0; b.targetId = null; b.roundCount = 0;
                    b.curNode = new FlightPoint(wingHome, 0, 0, 0);
                    return;
                }
                double rawr = prr * (Nr - 1);
                int lor = Math.min((int) rawr, Nr - 1);
                int hir = Math.min(lor + 1, Nr - 1);
                b.interpT = (float)(rawr - lor);
                b.interpFrom = b.flightPlan.steps.get(lor);
                b.interpTo   = b.flightPlan.steps.get(hir);
                b.worldPos = b.interpFrom.pos.lerp(b.interpTo.pos, b.interpT);
            }

        }
    }

    // ═══════════════════════════════════════════════════════════
    // 渲染 — 翅膀（保留）
    // ═══════════════════════════════════════════════════════════

    private static void renderIdleWing(PoseStack stack, MultiBufferSource buffer,
                                        ItemRenderer ri, ItemStack scroll, Player player,
                                        double px, double py, double pz, float yrDeg,
                                        int i, float age, double cx, double cy, double cz,
                                        int sourceLevel) {
        boolean isRight = i % 2 == 0;
        double step   = i / 2.0;
        double behind = behindBase + step * behindStep;
        double spread = spreadBase + step * spreadStep;
        double side   = isRight ? spread : -spread;
        double bob    = Math.sin(age * bobSpeed + i * bobPhaseStep) * bobAmplitude;
        double openDeg = (isRight ? 1 : -1) * (15.0 + step * 10.0 + tipExtendX * 30.0);
        double pitchDeg = tipYOffset * 45.0;
        double bodyY = (useEyeHeight ? player.getEyeHeight() * eyeYRatio : bodyYOffset) + anchorY;

        stack.pushPose();
        stack.translate(px - cx, py - cy, pz - cz);
        stack.mulPose(Axis.YP.rotationDegrees(180f - yrDeg));
        stack.translate(-side + anchorX, bob + bodyY, behind + anchorZ);
        if (Math.abs(openDeg) > 0.01) stack.mulPose(Axis.YP.rotationDegrees((float) openDeg));
        stack.mulPose(Axis.XP.rotationDegrees((float)(pitchDeg - 90.0)));
        if (Math.abs(tiltAngle) > 0.01) stack.mulPose(Axis.YP.rotationDegrees((float) tiltAngle));
        stack.scale((float) scaleX, (float)(scaleY * swordLength), (float) scaleZ);
        // 世界版边缘发光 — 8方向偏移 quad + 分级颜色
        renderWorldEdge(stack, buffer, scroll, player, sourceLevel);
        ri.renderStatic(scroll, ItemDisplayContext.GUI, glowLight, OverlayTexture.NO_OVERLAY,
            stack, buffer, player.level(), player.getId() * 100 + i);
        stack.popPose();
    }

    // ═══════════════════════════════════════════════════════════
    // 渲染 — 飞行（复用 Servantry renderEntityModel）
    // ═══════════════════════════════════════════════════════════

    private static void renderFlightBlade(PoseStack stack, MultiBufferSource buffer,
                                           ItemRenderer ri, ItemStack scroll, Player player,
                                           BladeState b, float partial,
                                           double cx, double cy, double cz) {
        FlightPoint node;
        if (b.flightPlan != null && b.interpFrom != null && b.interpTo != null) {
            node = b.interpFrom.lerpTo(b.interpTo, b.interpT);
        } else {
            node = b.curNode != null ? b.curNode : new FlightPoint(b.worldPos, 0, 0, 0);
        }
        if (node.pos == null) return;

        stack.pushPose();
        stack.translate(node.pos.x - cx, node.pos.y - cy, node.pos.z - cz);

        // 与 Servantry 一致的姿态链：yaw → pitch → roll → 模型偏移
        stack.mulPose(Axis.YN.rotationDegrees(node.yaw));
        stack.mulPose(Axis.XP.rotationDegrees(node.pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(node.roll));

        // 模型偏移 — 适配我们的 GUI Item 渲染
        stack.mulPose(Axis.YN.rotationDegrees(renderYawOff));
        stack.mulPose(Axis.XP.rotationDegrees(renderPitchOff));
        stack.mulPose(Axis.ZP.rotationDegrees(renderRollOff));

        stack.scale(renderScale, renderScale, renderScale);
        stack.translate(renderTransX, renderTransY, renderTransZ);

        renderWorldEdge(stack, buffer, scroll, player, b.sourceLevel);
        ri.renderStatic(scroll, ItemDisplayContext.GUI, glowLight, OverlayTexture.NO_OVERLAY,
            stack, buffer, player.level(), player.getId() * 100 + b.index);
        stack.popPose();
    }

    // ═══════════════════════════════════════════════════════════
    // 世界版边缘发光 — 8方向 quad 偏移 + 分级色
    // ═══════════════════════════════════════════════════════════

    private static void renderWorldEdge(PoseStack ps, MultiBufferSource buf,
                                         ItemStack scroll, Player player, int sourceLevel) {
        var tint = net.minecraft.client.yiz.util.StagedItemHelper.glowColorForLevel(sourceLevel);
        if (tint == null) return;

        var shader = net.minecraft.client.yiz.xian.render.glow.OutlineShaders.getGlowEdge();
        if (shader == null) return;

        BakedModel model = Minecraft.getInstance().getItemRenderer()
            .getModel(scroll, player.level(), player, 0);
        if (model == null) return;

        // 设置着色器 uniform（与物品栏 mixin 一致）
        shader.safeGetUniform("uColor").set(tint.x, tint.y, tint.z, tint.w);
        shader.safeGetUniform("uType").set(0);
        shader.safeGetUniform("time").set((System.currentTimeMillis() % 60000L) / 1000.0F);
        var ss = net.minecraft.client.yiz.xian.render.glow.OutlineShaders.getScreenSize();
        shader.safeGetUniform("screenSize").set(ss.x, ss.y);

        ps.pushPose();
        ps.translate(-0.5f, -0.5f, -0.5f);

        float off = 0.006f;
        var dirs = net.minecraft.client.yiz.xian.render.glow.GlowEdgeBakedModel.getDirections(true);
        var edgeRt = net.minecraft.client.yiz.xian.render.glow.OutlineRenderType.GLOW_EDGE;

        if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
        var vc = buf.getBuffer(edgeRt);

        for (var dir : dirs) {
            ps.pushPose();
            ps.translate(dir.x() * off, dir.y() * off, dir.z() * off);
            for (BakedModel pass : model.getRenderPasses(scroll, true)) {
                for (var quad : pass.getQuads(null, null,
                        net.minecraft.util.RandomSource.create(),
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
                    vc.putBulkData(ps.last(), quad,
                        tint.x, tint.y, tint.z, tint.w,
                        15728880, OverlayTexture.NO_OVERLAY, true);
                }
            }
            ps.popPose();
        }

        if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch(edgeRt);
        ps.popPose();
    }

    // ═══════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════

    /** 碰撞伤害 — 按 sourceLevel 查配置表执行不同伤害组合 */
    private static void tryDealDamage(BladeState b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) return;
        long t = mc.level.getGameTime();
        net.minecraft.server.level.ServerLevel sl = mc.getSingleplayerServer().getLevel(mc.level.dimension());
        net.minecraft.server.level.ServerPlayer sp = (net.minecraft.server.level.ServerPlayer) sl.getPlayerByUUID(mc.player.getUUID());
        if (sp == null) return;
        b.hitCooldowns.values().removeIf(v -> t - v > 6);

        var spec = net.minecraft.client.yiz.xian.item.TerraprismaScrollItem.specOf(b.sourceLevel);

        for (var e : mc.level.getEntities((net.minecraft.world.entity.Entity) null,
                new AABB(b.worldPos.add(-2.5,-2.5,-2.5), b.worldPos.add(2.5,2.5,2.5)),
                ent -> ent instanceof LivingEntity && ent != mc.player && ((LivingEntity) ent).isAlive())) {
            if (b.hitCooldowns.containsKey(e.getUUID())) continue;
            net.minecraft.world.entity.Entity se = sl.getEntity(e.getUUID());
            if (!(se instanceof LivingEntity st) || !st.isAlive()) continue;
            if (!(st instanceof Monster) && !(st instanceof net.minecraft.world.entity.monster.Enemy)
                && !(st instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == sp)
                && !(st.getLastHurtByMob() == sp)) continue;

            // ① 假受伤动画（永远触发）
            net.minecraft.client.yiz.api.YizModQZKWZAPI.fakeHurt(st, sp);
            net.minecraft.client.yiz.api.YizModQZKWZAPI.fakeKnockback(st, b.velocity.x, b.velocity.z, 0.05);

            // ② 按等级配置的原版 hurt
            switch (spec.kind()) {
                case PHYSICAL -> st.hurt(sp.damageSources().playerAttack(sp), (float)spec.hurtDmg());
                case MAGIC     -> st.hurt(sp.damageSources().indirectMagic(sp, sp), (float)spec.hurtDmg());
                default -> {} // TRUE_DAMAGE / HYBRID 不走原版 hurt
            }

            // ③ trueDamage
            if (spec.trueDmg() > 0 && useTrueDamage)
                net.minecraft.client.yiz.api.YizModQZKAPI.trueDamage(st, (float)spec.trueDmg(), sp);

            // ④ modifyHealth
            if (spec.modHp() > 0 && useModifyHealth) {
                float modDmg = (float)(spec.modHp() + st.getMaxHealth() * spec.modHpPctOfMax());
                net.minecraft.client.yiz.tool.health.EntityASMUtil.modifyHealth(st, -modDmg);
            }

            // 禁疗
            if (spec.antiHealSec() > 0) applyAntiHeal(se.getUUID(), spec.antiHealSec(), spec.antiHealCapSec());

            ((LivingEntity) e).hurtTime = 10;
            b.hitCooldowns.put(e.getUUID(), t);
        }
    }

    // ═══ 禁疗系统 ═══
    private static final Map<UUID, Long> ANTI_HEAL_EXPIRE = new HashMap<>();
    private static final Map<UUID, Integer> ANTI_HEAL_CAP = new HashMap<>();

    private static void applyAntiHeal(UUID targetId, int addSec, int capSec) {
        long now = System.currentTimeMillis();
        long remaining = Math.max(0, ANTI_HEAL_EXPIRE.getOrDefault(targetId, 0L) - now);
        // 叠加后不超过上限
        long total = Math.min(remaining + addSec * 1000L, capSec * 1000L);
        ANTI_HEAL_EXPIRE.put(targetId, now + total);
    }

    /** 检查目标是否处于禁疗状态（由 HealMixin 调用） */
    public static boolean isAntiHealed(UUID targetId) {
        Long exp = ANTI_HEAL_EXPIRE.get(targetId);
        return exp != null && System.currentTimeMillis() < exp;
    }

    /** 检测玩家左键攻击的实体，设为最高优先级指令目标（3秒过期） */
    private static void updateCommandTarget(Minecraft mc) {
        long now = System.currentTimeMillis();
        CMD_TARGET_EXPIRE.values().removeIf(t -> now - t > 3000);
        UUID puid = mc.player.getUUID();
        if (mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
            PLAYER_CMD_TARGET.put(puid, le.getUUID());
            CMD_TARGET_EXPIRE.put(le.getUUID(), now);
        } else if (!mc.options.keyAttack.isDown()) {
            CMD_TARGET_EXPIRE.computeIfPresent(PLAYER_CMD_TARGET.get(puid), (k, v) -> now - v > 3000 ? null : v);
        }
    }

    private static List<LivingEntity> scanHostiles(Player player) {
        List<LivingEntity> list = new ArrayList<>();
        for (var e : player.level().getEntities(player,
                player.getBoundingBox().inflate(targetRange),
                e -> e instanceof LivingEntity le && le.isAlive() && le != player
                    && isThreatToPlayer(le, player))) {
            list.add((LivingEntity) e);
        }
        list.sort(Comparator.comparingDouble(player::distanceToSqr));
        return list;
    }

    /**
     * 通用敌意判断。客户端预筛 + 服务端 target 精确判定。
     *  不会误伤友善生物（牛羊等 canAttack 也返回 true 但不设 target）。
     */
    private static boolean isThreatToPlayer(LivingEntity le, Player player) {
        // 客户端快速筛查
        if (le instanceof Monster) return true;
        if (le instanceof net.minecraft.world.entity.monster.Enemy) return true;
        if (le.getLastHurtByMob() == player) return true;
        // 服务端精确检测：只有 target 指向玩家 / 有任意 target 的才算
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            net.minecraft.server.level.ServerLevel sl = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            net.minecraft.world.entity.Entity se = sl.getEntity(le.getUUID());
            net.minecraft.server.level.ServerPlayer sp =
                (net.minecraft.server.level.ServerPlayer) sl.getPlayerByUUID(player.getUUID());
            if (se instanceof net.minecraft.world.entity.Mob sm) {
                if (sm.getTarget() == sp) return true;       // 锁定玩家
                if (sm.getTarget() != null) return true;     // 无差别敌对（史莱姆等）
            }
            if (se instanceof LivingEntity sle && sle.getLastHurtByMob() == sp) return true;
        }
        return false;
    }

    private static LivingEntity findTarget(Player player, UUID id) {
        if (id == null) return null;
        for (var e : player.level().getEntities(player,
                player.getBoundingBox().inflate(targetRange * 2))) {
            if (e instanceof LivingEntity le && le.getUUID().equals(id) && le.isAlive()) return le;
        }
        return null;
    }

    private static Vec3 wingWorldPos(Player player, double px, double py, double pz,
                                       float yrDeg, int i, float _p) {
        boolean isRight = i % 2 == 0;
        double step = i / 2.0;
        double behind = behindBase + step * behindStep;
        double spread = spreadBase + step * spreadStep;
        double side = isRight ? spread : -spread;
        double bodyY = (useEyeHeight ? player.getEyeHeight() * eyeYRatio : bodyYOffset) + anchorY;
        double lx = -side + anchorX, lz = behind + anchorZ;
        double rot = Math.toRadians(180.0 - yrDeg);
        double cs = Math.cos(rot), sn = Math.sin(rot);
        return new Vec3(px + lx*cs + lz*sn, py + bodyY, pz + -lx*sn + lz*cs);
    }

    private static List<ItemStack> findAllScrolls(Player player) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof net.minecraft.client.yiz.xian.item.TerraprismaScrollItem) result.add(s);
        }
        return result;
    }

    private static int indexOf(UUID[] arr, UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < arr.length; i++) if (id.equals(arr[i])) return i;
        return -1;
    }

    private static int pickNeed(int[] quota, int[] cur) {
        int best = -1, mg = 0;
        for (int i = 0; i < quota.length; i++) {
            int g = quota[i] - cur[i];
            if (g > mg) { mg = g; best = i; }
        }
        return best;
    }

    // ═══════════════════════════════════════════════════════════
    // 调试 HUD — 屏幕左侧显示每剑当前阶段
    // ═══════════════════════════════════════════════════════════

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var gui = event.getGuiGraphics();
        var font = mc.font;
        UUID puid = mc.player.getUUID();
        BladeState[] blades = BLADES.get(puid);
        if (blades == null || blades.length == 0) return;

        int x = 4, y = 4;
        int count = 0;
        for (BladeState b : blades) {
            if (b == null) continue;
            count++;
        }

        gui.drawString(font, "§l§n 泰拉棱镜 [" + count + "/" + blades.length + "]", x, y, 0xFFFFFFFF);
        y += 12;

        // 按阶段分组显示
        String[] phaseNames = {"A:翅膀", "B:上升", "C:俯冲", "D:椭圆", "E:穿刺", "F:归巢", "G:追击"};
        String[] phaseKeys  = {"A","B","C","D","E","F","G"};
        for (int pi = 0; pi < phaseKeys.length; pi++) {
            StringBuilder sb = new StringBuilder(phaseNames[pi] + "  ");
            int cnt = 0;
            for (BladeState b : blades) {
                if (b != null && phaseKeys[pi].equals(b.phaseLabel)) {
                    if (cnt > 0) sb.append(",");
                    if (cnt >= 12) { sb.append("…"); break; }
                    sb.append(b.index);
                    cnt++;
                }
            }
            if (cnt == 0) continue;
            int color = cnt > 10 ? 0xFF00FF00 : cnt > 5 ? 0xFFFFFF00 : cnt > 0 ? 0xFFFF8800 : 0xFFFF0000;
            gui.drawString(font, sb.toString(), x, y, color);
            y += 10;
        }
    }

    private static double pseudoRand(int index, int salt) {
        int h = index * 0x27d4eb2d + salt * 0x165667b1;
        h = (h ^ (h >>> 15)) * 0x2c1b3c6d;
        h = (h ^ (h >>> 12)) * 0x297a2d39;
        h ^= h >>> 15;
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2.0 - 1.0;
    }
}
