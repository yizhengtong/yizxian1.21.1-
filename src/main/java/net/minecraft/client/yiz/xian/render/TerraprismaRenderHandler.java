package net.minecraft.client.yiz.xian.render;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TerraprismaRenderHandler {

    private static final Path CONFIG_PATH = Path.of("config", "yizxianmod", "terraprisma.json");

    private static final Map<UUID, BladeState[]> BLADES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_DISPATCH_TICK = new HashMap<>();

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

    // ── 攻击参数 ──
    static int    firstStrikeDuration  = 7;
    static int    ellipseBlendTicks    = 6;
    static int    ellipseDuration      = 14;
    static int    piercePrepTicks      = 6;
    static int    pierceAttackTicks    = 8;
    static int    chaseStrikeTicks     = 7;
    static double pierceCircleRadius   = 5.0;
    static double pierceCircleY        = 3.0;
    static int    dispatchInterval     = 5;
    static int    maxAttackRounds      = 8;
    static int    prepEngageTicks      = 10;
    static int    retreatTicks         = 30;

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
        FlightPoint prevNode = FlightPoint.Z;
        FlightPoint interpFrom = FlightPoint.Z;
        FlightPoint interpTo   = FlightPoint.Z;
        float interpT;
        UUID targetId;
        Vec3 worldPos;
        Vec3 velocity = Vec3.ZERO;
        float timeInMode;
        int roundCount;
        int launchPhase;
        boolean didFirstStrike;
        Vec3 memoPos = Vec3.ZERO;
        Vec3 retreatStart;
        Vec3 retreatCtrl;
        Vec3 retreatHome;
        long lastHitTick;

        BladeState(int index) { this.index = index; }

        void enterRising(UUID target, Vec3 from) {
            mode = BladeMode.RISING; timeInMode = 0; targetId = target;
            worldPos = from; roundCount = 0; didFirstStrike = false;
            launchPhase = 0; memoPos = Vec3.ZERO; flightPlan = null;
            curNode = new FlightPoint(from, 0, 0, 0);
            retreatStart = null; retreatCtrl = null; retreatHome = null;
        }
        void swapTarget(UUID newTarget) {
            mode = BladeMode.RISING; timeInMode = 0; targetId = newTarget;
            roundCount = 0; launchPhase = 1; memoPos = Vec3.ZERO; flightPlan = null;
            retreatStart = null; retreatCtrl = null; retreatHome = null;
        }
        void executePlan(FlightPlan plan) {
            mode = BladeMode.ASSAULT; timeInMode = 0; flightPlan = plan;
            curNode = FlightPoint.Z; prevNode = FlightPoint.Z;
        }
        void returnHome() {
            mode = BladeMode.RETURNING; timeInMode = 0; targetId = null;
            roundCount = 0; launchPhase = 0; flightPlan = null;
            retreatStart = worldPos;
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
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 dir = end.subtract(start);
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 0, 1);
        end = end.add(dir.normalize().scale(2.5));
        Vec3 planeN = dir.cross(new Vec3(0, 1, 0)).normalize();
        if (planeN.lengthSqr() < 1e-4) planeN = new Vec3(1, 0, 0);
        List<FlightPoint> steps = new ArrayList<>();
        int dur = firstStrikeDuration;
        for (int i = 1; i <= dur; i++) {
            float t = (float) i / dur;
            t = t * t;
            steps.add(resolveRotation(start.lerp(end, t), dir, planeN));
        }
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
        blade.executePlan(new FlightPlan(steps));
    }

    private static void planChaseStrike(BladeState blade, LivingEntity target) {
        Vec3 endPos = target.getBoundingBox().getCenter();
        int dur = chaseStrikeTicks;
        Vec3 curUp = blade.bladeUp();
        List<FlightPoint> steps = new ArrayList<>();
        OrbitCurve el = new OrbitCurve(endPos, blade.worldPos, curUp, 0.25f);
        for (int i = 1; i <= dur; i++) {
            float p = (float) i / dur;
            Vec3 ep = el.pointAt(p * 0.5f);
            Vec3 td = ep.subtract(el.center()).normalize();
            steps.add(resolveRotation(ep, td, curUp));
        }
        blade.executePlan(new FlightPlan(steps));
    }

    private static void applyPosCorrection(BladeState blade, LivingEntity target) {
        Vec3 curCenter = target.getBoundingBox().getCenter();
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
        NEXT_DISPATCH_TICK.keySet().retainAll(alive);

        for (Player player : players) {
            ItemStack scroll = findScroll(player);
            if (scroll.isEmpty()) { BLADES.remove(player.getUUID()); continue; }
            int count = net.minecraft.client.yiz.xian.item.TerraprismaScrollItem.getSwordCount(scroll);
            if (count <= 0) { BLADES.remove(player.getUUID()); continue; }

            double px = Mth.lerp(partial, player.xo, player.getX());
            double py = Mth.lerp(partial, player.yo, player.getY());
            double pz = Mth.lerp(partial, player.zo, player.getZ());
            float yrDeg = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);

            BladeState[] blades = BLADES.computeIfAbsent(player.getUUID(), k -> new BladeState[count]);
            if (blades.length != count) {
                BladeState[] r = Arrays.copyOf(blades, count);
                blades = r; BLADES.put(player.getUUID(), blades);
            }
            for (int i = 0; i < count; i++)
                if (blades[i] == null) blades[i] = new BladeState(i);

            runScheduler(player, blades, gameTime, px, py, pz, yrDeg);

            for (int i = 0; i < count; i++) {
                BladeState b = blades[i];
                if (b.mode == BladeMode.IDLE_WING) {
                    renderIdleWing(stack, buffer, ri, scroll, player, px, py, pz, yrDeg, i, gameTime + partial, cx, cy, cz);
                } else {
                    renderFlightBlade(stack, buffer, ri, scroll, player, b, partial, cx, cy, cz);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 调度器
    // ═══════════════════════════════════════════════════════════

    private static void runScheduler(Player player, BladeState[] blades, long gameTime,
                                      double px, double py, double pz, float yrDeg) {
        List<LivingEntity> hostiles = scanHostiles(player);
        List<LivingEntity> atkList = hostiles.size() > 3 ? hostiles.subList(0, 3) : hostiles;
        int t = blades.length;
        int[] quota = switch (atkList.size()) {
            case 0 -> new int[0];
            case 1 -> new int[]{t};
            case 2 -> new int[]{t/2, t - t/2};
            default -> { int a = t * 3/8, b = t * 3/8; yield new int[]{a, b, t - a - b}; }
        };
        UUID[] ids = atkList.stream().map(LivingEntity::getUUID).toArray(UUID[]::new);
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
                if (need >= 0) { b.swapTarget(ids[need]); assigned[need]++; }
                else b.returnHome();
                continue;
            }
            int idx = indexOf(ids, b.targetId);
            if (idx < 0) {
                int need = pickNeed(quota, assigned);
                if (need >= 0) { b.swapTarget(ids[need]); assigned[need]++; }
                else b.returnHome();
                continue;
            }
            if (assigned[idx] > quota[idx]) {
                int need = pickNeed(quota, assigned);
                if (need >= 0 && need != idx) { b.swapTarget(ids[need]); assigned[idx]--; assigned[need]++; }
            }
        }

        long last = NEXT_DISPATCH_TICK.getOrDefault(player.getUUID(), -999L);
        if (!atkList.isEmpty() && gameTime - last >= dispatchInterval) {
            int need = pickNeed(quota, assigned);
            if (need >= 0) {
                for (BladeState b : blades) {
                    if (b.mode == BladeMode.IDLE_WING) {
                        Vec3 wp = wingWorldPos(player, px, py, pz, yrDeg, b.index, 1f);
                        b.enterRising(ids[need], wp);
                        assigned[need]++;
                        NEXT_DISPATCH_TICK.put(player.getUUID(), gameTime);
                        break;
                    }
                }
            }
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
                    Vec3 tp = tgt.getBoundingBox().getCenter();
                    // 贝塞尔弧线飞向 apex：控制点偏目标方向，造出"先转向再爬升"的弧线
                    double sideOff = pseudoRand(b.index, 5) * 1.8;   // 左右散布
                    double fwdOff  = pseudoRand(b.index, 6) * 1.2;   // 前后散布
                    float bodyRad = (float) Math.toRadians(yrDeg);
                    Vec3 ctrl = new Vec3(px + sideOff * Math.cos(bodyRad) + fwdOff * Math.sin(bodyRad),
                        py + player.getEyeHeight() + 1.0 + pseudoRand(b.index,7) * 2.0,
                        pz - sideOff * Math.sin(bodyRad) + fwdOff * Math.cos(bodyRad));
                    Vec3 tipDir = tp.subtract(apex).normalize();
                    Vec3 pn = tipDir.cross(new Vec3(0,1,0)).normalize();
                    if (pn.lengthSqr() < 1e-4) pn = new Vec3(1,0,0);
                    List<FlightPoint> st = new ArrayList<>();
                    int dur = 12 + b.index % 4;  // 12~15 tick，每把剑稍不同
                    for (int j = 1; j <= dur; j++) {
                        float t = (float) j / dur;
                        t = t * t * (3f - 2f * t);
                        Vec3 p = bezier3(t, b.worldPos, ctrl, apex);
                        st.add(resolveRotation(p, tipDir, pn));
                    }
                    b.flightPlan = new FlightPlan(st); b.timeInMode = 0;
                }
                // 平滑分数阶插值（消除闪烁）
                int N = b.flightPlan.steps.size();
                float pr = Math.min(1f, b.timeInMode / N);
                if (pr >= 1f) {
                    planFirstStrike(b, tgt); applyPosCorrection(b, tgt);
                    return;
                }
                double raw = pr * (N - 1);
                int lo = Math.min((int) raw, N - 1);
                int hi = Math.min(lo + 1, N - 1);
                b.interpT = (float)(raw - lo);
                b.interpFrom = b.flightPlan.steps.get(lo);
                b.interpTo   = b.flightPlan.steps.get(hi);
                b.worldPos = b.interpFrom.pos.lerp(b.interpTo.pos, b.interpT);
            }

            case ASSAULT -> {
                if (b.flightPlan == null) { b.returnHome(); return; }
                int totalTicks = b.flightPlan.steps.size();
                float progress = Math.min(1f, b.timeInMode / totalTicks);
                if (progress >= 1f) {
                    b.roundCount++;
                    LivingEntity tgt = findTarget(player, b.targetId);
                    if (tgt == null || !tgt.isAlive()) { b.returnHome(); return; }
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
                // 伤害判定：剑在目标附近时造成 9 点伤害
                LivingEntity atk = findTarget(player, b.targetId);
                if (atk != null && atk.isAlive()) tryDealDamage(b, atk);
            }

            case RETURNING -> {
                // DIVE 攻击玩家 → 穿过 → 立刻归翼
                Vec3 playerBody = new Vec3(px, py + player.getBbHeight() * 0.5, pz);
                Vec3 wingHome = wingWorldPos(player, px, py, pz, yrDeg, b.index, 1f);
                if (b.flightPlan == null) {
                    Vec3 dir = playerBody.subtract(b.worldPos).normalize();
                    Vec3 through = playerBody.add(dir.scale(1.5));
                    Vec3 pn = dir.cross(new Vec3(0,1,0)).normalize();
                    if (pn.lengthSqr() < 1e-4) pn = new Vec3(1,0,0);
                    List<FlightPoint> st = new ArrayList<>();
                    for (int j = 1; j <= 7; j++) {
                        float t = (float) j / 7f; t = t * t;
                        st.add(resolveRotation(b.worldPos.lerp(through, t), dir, pn));
                    }
                    b.flightPlan = new FlightPlan(st); b.timeInMode = 0;
                }
                int Nr = b.flightPlan.steps.size();
                float prr = Math.min(1f, b.timeInMode / Nr);
                if (prr >= 1f) {
                    b.worldPos = wingHome;
                    b.mode = BladeMode.IDLE_WING; b.timeInMode = 0;
                    b.targetId = null; b.roundCount = 0;
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
                                        int i, float age, double cx, double cy, double cz) {
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

        ri.renderStatic(scroll, ItemDisplayContext.GUI, glowLight, OverlayTexture.NO_OVERLAY,
            stack, buffer, player.level(), player.getId() * 100 + b.index);
        stack.popPose();
    }

    // ═══════════════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════════════

    /** 单机通过 integrated server 对目标造成 9 点伤害并破解无敌帧 */
    private static void tryDealDamage(BladeState b, LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long t = mc.level.getGameTime();
        if (b.lastHitTick > 0 && t - b.lastHitTick < 5) return;
        if (b.worldPos.distanceTo(target.getBoundingBox().getCenter()) > 1.8) return;
        if (!mc.hasSingleplayerServer()) return;
        net.minecraft.server.level.ServerLevel sl = mc.getSingleplayerServer().overworld();
        net.minecraft.world.entity.Entity se = sl.getEntity(target.getUUID());
        net.minecraft.server.level.ServerPlayer sp = (net.minecraft.server.level.ServerPlayer) sl.getPlayerByUUID(mc.player.getUUID());
        if (sp == null) return;
        if (se instanceof LivingEntity st && st.isAlive()) {
            st.hurt(sp.damageSources().playerAttack(sp), 9f);
            st.invulnerableTime = 0;
            b.lastHitTick = t;
        }
    }

    private static List<LivingEntity> scanHostiles(Player player) {
        List<LivingEntity> list = new ArrayList<>();
        for (var e : player.level().getEntities(player,
                player.getBoundingBox().inflate(targetRange),
                e -> e instanceof Monster && ((LivingEntity) e).isAlive())) {
            list.add((LivingEntity) e);
        }
        list.sort(Comparator.comparingDouble(player::distanceToSqr));
        return list;
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

    private static ItemStack findScroll(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof net.minecraft.client.yiz.xian.item.TerraprismaScrollItem) return s;
        }
        return ItemStack.EMPTY;
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

    private static double pseudoRand(int index, int salt) {
        int h = index * 0x27d4eb2d + salt * 0x165667b1;
        h = (h ^ (h >>> 15)) * 0x2c1b3c6d;
        h = (h ^ (h >>> 12)) * 0x297a2d39;
        h ^= h >>> 15;
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2.0 - 1.0;
    }
}
