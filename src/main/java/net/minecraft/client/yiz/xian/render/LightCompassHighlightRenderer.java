package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 光明指南针高亮渲染器（扫描 + 渲染一体）。
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>每 {@link #SCAN_TICKS} 帧读一次玩家 PlayerDataAPI 中 3 个工作槽物品</li>
 *   <li>解析为 {@link CompassHighlightTarget}（方块/生物/掉落物 + 颜色）</li>
 *   <li>扫描玩家周围 {@link #RANGE} 格内的匹配目标并缓存</li>
 *   <li>每帧用穿墙 LINES 渲染缓存的高亮轮廓 / AABB 框</li>
 * </ul>
 *
 * <h3>方块轮廓</h3>
 * 复用 {@link EndPortalGlowRenderer} 的共面合并算法：
 * 相邻同目标方块在共享面上的中间棱被判定为「共面内部接缝」→ 不画。
 * 只画合并后大面的外轮廓棱，天然去重。
 *
 * <h3>实体 / 掉落物</h3>
 * 画 AABB 的 12 条棱线，随实体大小缩放。
 *
 * <h3>颜色</h3>
 * 按工作槽 index 固定（见 {@link CompassHighlightTarget#colorFor}）：
 * slot 0 = 黄、slot 1 = 白、slot 2 = 橙。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "yizxianmod")
public final class LightCompassHighlightRenderer {

    // ════════════════════════════════════════════
    //  常量
    // ════════════════════════════════════════════

    private static final int RANGE = 128;
    private static final int Y_RANGE = 60;
    /** 扫描过期时间（tick），60 秒 = 1200 tick */
    private static final int SCAN_EXPIRY_TICKS = 1200;
    private static final int WORK_SLOTS = 3;
    private static final String DATA_KEY = "yizxianmod:light_compass_work_slots";

    /** 方块高亮上限：最多 300 个方块，超过则只保留离玩家最近的 */
    private static final int MAX_BLOCKS = 300;
    /** 方块高亮上限：超过 5 个分离的连通块则整体不展示 */
    private static final int MAX_BLOCK_CLUSTERS = 5;
    /** 实体/掉落物高亮上限：只展示距离玩家最近的 8 个 */
    private static final int MAX_ENTITIES = 8;

    /** 当前扫描过期 tick；0 表示无有效扫描 */
    private static long scanExpiryTick = 0;

    // ════════════════════════════════════════════
    //  扫描结果缓存（纯静态，无帧间修改）
    // ════════════════════════════════════════════

    /** 每个工作槽的解析后高亮目标；null = 空槽 */
    private static final CompassHighlightTarget[] targets = new CompassHighlightTarget[WORK_SLOTS];

    /** 方块轮廓：共面合并后棱列表 */
    private static final List<List<int[]>> blockEdges = new ArrayList<>(WORK_SLOTS);

    /** 实体追踪：每帧重查 AABB 的网络 ID */
    private static final List<List<Integer>> entityIds = new ArrayList<>(WORK_SLOTS);
    /** 掉落物追踪：同上 */
    private static final List<List<Integer>> itemDropIds = new ArrayList<>(WORK_SLOTS);

    static {
        for (int i = 0; i < WORK_SLOTS; i++) {
            blockEdges.add(Collections.emptyList());
            entityIds.add(Collections.emptyList());
            itemDropIds.add(Collections.emptyList());
        }
    }

    // ════════════════════════════════════════════
    //  RenderType — NO_TRANSPARENCY 免排序，NO_DEPTH_TEST 穿墙
    // ════════════════════════════════════════════

    private static final RenderType GLOW_LINES;
    static {
        var s = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLayeringState(RenderStateShard.NO_LAYERING)
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .createCompositeState(false);
        GLOW_LINES = RenderType.create("light_compass_glow",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            4096, false, false, s);
    }

    private LightCompassHighlightRenderer() {}

    // ════════════════════════════════════════════
    //  API：右键光明指南针时调用，触发一次性扫描
    // ════════════════════════════════════════════

    /** 由 BrightCompassItem.use() 在客户端调用。触发扫描并设定 60 秒过期。 */
    public static void triggerScan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        scanAll(mc);
        scanExpiryTick = mc.level.getGameTime() + SCAN_EXPIRY_TICKS;
    }

    // ════════════════════════════════════════════
    //  渲染入口 — 仅渲染缓存结果，过期自动清除
    // ════════════════════════════════════════════

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // ── 过期检查 ──
        if (scanExpiryTick > 0 && mc.level.getGameTime() > scanExpiryTick) {
            clearAll();
            scanExpiryTick = 0;
            return;
        }

        boolean anyActive = false;
        for (int i = 0; i < WORK_SLOTS; i++) {
            if (targets[i] != null) { anyActive = true; break; }
        }
        if (!anyActive) return;

        // ── 渲染：与 EndPortalGlowRenderer 完全相同的渲染方式 ──
        Vec3 cam = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        MultiBufferSource.BufferSource bfs = mc.renderBuffers().bufferSource();
        VertexConsumer v = bfs.getBuffer(GLOW_LINES);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var m = pose.last();

        for (int i = 0; i < WORK_SLOTS; i++) {
            CompassHighlightTarget t = targets[i];
            if (t == null) continue;
            float r = t.r(), g = t.g(), b = t.b(), a = 1.0f;
            for (int[] e : blockEdges.get(i)) {
                float nx = e[0] != e[3] ? Math.signum(e[3] - e[0]) : 0f;
                float ny = e[1] != e[4] ? Math.signum(e[4] - e[1]) : 0f;
                float nz = e[2] != e[5] ? Math.signum(e[5] - e[2]) : 0f;
                v.addVertex(m, e[0], e[1], e[2]).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
                v.addVertex(m, e[3], e[4], e[5]).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
            }
            // 实体追踪：每帧用网络 ID 重查 AABB（只读，不修改共享数据）
            for (int eid : entityIds.get(i)) {
                Entity e = mc.level.getEntity(eid);
                if (e != null) drawAabbLines(v, m, e.getBoundingBox(), r, g, b, a);
            }
            for (int eid : itemDropIds.get(i)) {
                Entity e = mc.level.getEntity(eid);
                if (e != null) drawAabbLines(v, m, e.getBoundingBox(), r, g, b, a);
            }
        }

        pose.popPose();
        bfs.endBatch(GLOW_LINES);
    }

    /** 清空所有缓存数据。 */
    private static void clearAll() {
        for (int i = 0; i < WORK_SLOTS; i++) {
            targets[i] = null;
            blockEdges.set(i, Collections.emptyList());
            entityIds.set(i, Collections.emptyList());
            itemDropIds.set(i, Collections.emptyList());
        }
    }

    // ════════════════════════════════════════════
    //  扫描
    // ════════════════════════════════════════════

    private static void scanAll(Minecraft mc) {
        clearAll();
        scanExpiryTick = 0; // 先清过期，扫描完成后重新设定

        // ── 从 PlayerDataAPI 读工作槽物品 ID ──
        List<Integer> saved;
        try {
            saved = PlayerDataAPI.get(mc.player, DATA_KEY);
        } catch (Exception e) {
            return; // key 未注册或数据格式异常
        }
        if (saved == null || saved.isEmpty()) return;

        for (int i = 0; i < WORK_SLOTS && i < saved.size(); i++) {
            Integer idObj = saved.get(i);
            int id = (idObj != null) ? idObj : -1;
            if (id < 0) continue;
            Item item = BuiltInRegistries.ITEM.byId(id);
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            CompassHighlightTarget target = CompassHighlightTarget.from(stack, i);
            if (target == null) continue;
            targets[i] = target;
        }

        // 检查是否至少有一个目标
        boolean anyActive = false;
        for (int i = 0; i < WORK_SLOTS; i++) {
            if (targets[i] != null) { anyActive = true; break; }
        }
        if (!anyActive) return;

        Level level = mc.level;
        BlockPos playerPos = mc.player.blockPosition();

        // ── 扫描方块（区块级遍历 + 扫描时距离裁剪）──
        // 每个有 BLOCK 目标的槽位一个 max-heap（最远在顶），边扫边裁到 MAX_BLOCKS
        @SuppressWarnings("unchecked")
        PriorityQueue<BlockPos>[] heaps = new PriorityQueue[WORK_SLOTS];
        boolean[] hasBlockTarget = new boolean[WORK_SLOTS];
        for (int i = 0; i < WORK_SLOTS; i++) {
            if (targets[i] != null && targets[i].kind() == CompassHighlightTarget.Kind.BLOCK) {
                hasBlockTarget[i] = true;
                heaps[i] = new PriorityQueue<>(MAX_BLOCKS + 1,
                    Comparator.comparingDouble((BlockPos p) -> p.getCenter().distanceToSqr(playerPos.getCenter())).reversed());
            }
        }
        if (hasBlockTarget()) {
            int cx = playerPos.getX(), cy = playerPos.getY(), cz = playerPos.getZ();
            int minCX = (cx - RANGE) >> 4, maxCX = (cx + RANGE) >> 4;
            int minCZ = (cz - RANGE) >> 4, maxCZ = (cz + RANGE) >> 4;
            int minSY = (cy - Y_RANGE) >> 4, maxSY = (cy + Y_RANGE) >> 4;

            for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
                for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                    if (!level.hasChunk(chunkX, chunkZ)) continue;      // 跳过未加载区块
                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    int secFrom = Math.max(minSY, chunk.getMinSection());
                    int secTo   = Math.min(maxSY, chunk.getMaxSection());

                    for (int sy = secFrom; sy <= secTo; sy++) {
                        LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));
                        if (section.hasOnlyAir()) continue;              // 跳过全空区段
                        int wyBase = sy << 4;

                        for (int lx = 0; lx < 16; lx++) {
                            int wx = (chunkX << 4) + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                int wz = (chunkZ << 4) + lz;
                                for (int ly = 0; ly < 16; ly++) {
                                    int wy = wyBase + ly;
                                    var state = section.getBlockState(lx, ly, lz);
                                    if (state.isAir()) continue;
                                    for (int i = 0; i < WORK_SLOTS; i++) {
                                        if (!hasBlockTarget[i]) continue;
                                        if (!state.is(targets[i].block())) continue;
                                        BlockPos pos = new BlockPos(wx, wy, wz);
                                        heaps[i].add(pos);
                                        if (heaps[i].size() > MAX_BLOCKS) heaps[i].poll(); // 踢掉最远
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 堆 → Set，过聚类检查
        for (int i = 0; i < WORK_SLOTS; i++) {
            if (heaps[i] == null || heaps[i].isEmpty()) {
                blockEdges.set(i, Collections.emptyList());
                continue;
            }
            Set<BlockPos> limited = new HashSet<>(heaps[i]);
            blockEdges.set(i, countClusters(limited) > MAX_BLOCK_CLUSTERS
                ? Collections.emptyList()
                : computeCoplanarEdges(limited));
        }

        // ── 扫描实体和掉落物（存网络 ID，每帧用 level.getEntity 重查 AABB）──
        AABB scanBox = new AABB(playerPos).inflate(RANGE);
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < WORK_SLOTS; i++) {
            CompassHighlightTarget t = targets[i];
            if (t == null) {
                entityIds.set(i, Collections.emptyList());
                itemDropIds.set(i, Collections.emptyList());
                continue;
            }
            if (t.kind() == CompassHighlightTarget.Kind.ENTITY) {
                List<Entity> list = level.getEntitiesOfClass(
                    Entity.class, scanBox, e -> e.getType() == t.entityType());
                list.sort(Comparator.comparingDouble(e -> e.getEyePosition().distanceToSqr(eye)));
                if (list.size() > MAX_ENTITIES) list = list.subList(0, MAX_ENTITIES);
                entityIds.set(i, list.stream().map(Entity::getId).collect(Collectors.toList()));
                itemDropIds.set(i, Collections.emptyList());
            } else if (t.kind() == CompassHighlightTarget.Kind.ITEM_DROP) {
                List<ItemEntity> list = level.getEntitiesOfClass(
                    ItemEntity.class, scanBox, e -> e.getItem().getItem() == t.item());
                list.sort(Comparator.comparingDouble(e -> e.getEyePosition().distanceToSqr(eye)));
                if (list.size() > MAX_ENTITIES) list = list.subList(0, MAX_ENTITIES);
                entityIds.set(i, Collections.emptyList());
                itemDropIds.set(i, list.stream().map(Entity::getId).collect(Collectors.toList()));
            } else {
                entityIds.set(i, Collections.emptyList());
                itemDropIds.set(i, Collections.emptyList());
            }
        }
    }

    private static boolean hasBlockTarget() {
        for (int i = 0; i < WORK_SLOTS; i++) {
            if (targets[i] != null && targets[i].kind() == CompassHighlightTarget.Kind.BLOCK) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════
    //  聚类检查
    // ════════════════════════════════════════════

    /** BFS 统计 6 面相邻的连通块数量。 */
    private static int countClusters(Set<BlockPos> blocks) {
        Set<BlockPos> visited = new HashSet<>();
        int clusters = 0;
        for (BlockPos start : blocks) {
            if (!visited.add(start)) continue;
            clusters++;
            // BFS 扩展当前连通块
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos nb = p.relative(dir);
                    if (blocks.contains(nb) && visited.add(nb)) {
                        queue.add(nb);
                    }
                }
            }
        }
        return clusters;
    }

    // ════════════════════════════════════════════
    //  AABB 12 棱绘制
    // ════════════════════════════════════════════

    /** 画一个 AABB 的 12 条线。顶点为世界坐标，pose 已含相机偏移。 */
    private static void drawAabbLines(VertexConsumer v, PoseStack.Pose m,
                                       AABB box, float r, float g, float b, float a) {
        float minX = (float)box.minX, minY = (float)box.minY, minZ = (float)box.minZ;
        float maxX = (float)box.maxX, maxY = (float)box.maxY, maxZ = (float)box.maxZ;
        line(v, m, minX,minY,minZ, maxX,minY,minZ, r,g,b,a);
        line(v, m, maxX,minY,minZ, maxX,minY,maxZ, r,g,b,a);
        line(v, m, maxX,minY,maxZ, minX,minY,maxZ, r,g,b,a);
        line(v, m, minX,minY,maxZ, minX,minY,minZ, r,g,b,a);
        line(v, m, minX,maxY,minZ, maxX,maxY,minZ, r,g,b,a);
        line(v, m, maxX,maxY,minZ, maxX,maxY,maxZ, r,g,b,a);
        line(v, m, maxX,maxY,maxZ, minX,maxY,maxZ, r,g,b,a);
        line(v, m, minX,maxY,maxZ, minX,maxY,minZ, r,g,b,a);
        line(v, m, minX,minY,minZ, minX,maxY,minZ, r,g,b,a);
        line(v, m, maxX,minY,minZ, maxX,maxY,minZ, r,g,b,a);
        line(v, m, maxX,minY,maxZ, maxX,maxY,maxZ, r,g,b,a);
        line(v, m, minX,minY,maxZ, minX,maxY,maxZ, r,g,b,a);
    }

    private static void line(VertexConsumer v, PoseStack.Pose m,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        float nx = x1 != x2 ? Math.signum(x2 - x1) : 0f;
        float ny = y1 != y2 ? Math.signum(y2 - y1) : 0f;
        float nz = z1 != z2 ? Math.signum(z2 - z1) : 0f;
        v.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
        v.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
    }

    // ════════════════════════════════════════════
    //  共面合并轮廓算法（来自 EndPortalGlowRenderer，泛化为任意 BlockPos Set）
    // ════════════════════════════════════════════

    /**
     * 计算目标方块集的共面合并后外轮廓棱。
     * 暴露面上插入中间块导致相邻方块共面拼接的接缝会被丢弃，
     * 只留下合并大面的真正外边界。
     */
    private static List<int[]> computeCoplanarEdges(Set<BlockPos> set) {
        Set<EdgeKey> seen = new HashSet<>();
        List<int[]> draw = new ArrayList<>();
        for (BlockPos pos : set) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            for (Direction face : Direction.values()) {
                if (set.contains(pos.relative(face))) continue; // 接触面，跳过
                for (int[] e : faceEdges(x, y, z, face)) {
                    if (!seen.add(EdgeKey.of(e))) continue;          // 已加入，跳过
                    if (isCoplanarSeam(e, x, y, z, face, set)) continue; // 共面内部接缝
                    draw.add(e);
                }
            }
        }
        return draw;
    }

    /** 方块某个暴露面的 4 条边（整数网格坐标）。 */
    private static int[][] faceEdges(int x, int y, int z, Direction face) {
        return switch (face) {
            case DOWN  -> new int[][]{{x,y,z, x+1,y,z},{x+1,y,z, x+1,y,z+1},{x+1,y,z+1, x,y,z+1},{x,y,z+1, x,y,z}};
            case UP    -> new int[][]{{x,y+1,z, x+1,y+1,z},{x+1,y+1,z, x+1,y+1,z+1},{x+1,y+1,z+1, x,y+1,z+1},{x,y+1,z+1, x,y+1,z}};
            case NORTH -> new int[][]{{x,y,z, x+1,y,z},{x+1,y,z, x+1,y+1,z},{x+1,y+1,z, x,y+1,z},{x,y+1,z, x,y,z}};
            case SOUTH -> new int[][]{{x,y,z+1, x+1,y,z+1},{x+1,y,z+1, x+1,y+1,z+1},{x+1,y+1,z+1, x,y+1,z+1},{x,y+1,z+1, x,y,z+1}};
            case WEST  -> new int[][]{{x,y,z, x,y,z+1},{x,y,z+1, x,y+1,z+1},{x,y+1,z+1, x,y+1,z},{x,y+1,z, x,y,z}};
            case EAST  -> new int[][]{{x+1,y,z, x+1,y,z+1},{x+1,y,z+1, x+1,y+1,z+1},{x+1,y+1,z+1, x+1,y+1,z},{x+1,y+1,z, x+1,y,z}};
        };
    }

    /**
     * 判定棱 e（在方块 (x,y,z) 的 face 面上）是否是「共面内部接缝」——
     * 即 e 两侧（在 face 平面内、垂直于 e 的方向）各有一个方块单元，
     * 它们的 face 面共面拼接，e 落在拼接缝上。这种棱属于合并大面的内部，不该画。
     */
    private static boolean isCoplanarSeam(int[] e, int x, int y, int z, Direction face, Set<BlockPos> set) {
        int axis = face.getAxis().ordinal(); // X=0,Y=1,Z=2
        int a1 = (axis == 0) ? 1 : 0;
        int a2 = (axis == 0) ? 2 : (axis == 1 ? 2 : 1);
        boolean eAlongA1 = (coord(e, 0, a1) != coord(e, 1, a1));
        int sideAxis = eAlongA1 ? a2 : a1;

        int eSideCoord = coord(e, 0, sideAxis);
        int blockSideCoord = getCoord(x, y, z, sideAxis);
        boolean eAtLowSide = (eSideCoord == blockSideCoord);
        int sideStep = eAtLowSide ? -1 : +1;

        int[] nb = neighborCoord(x, y, z, sideAxis, sideStep);
        BlockPos nbPos = new BlockPos(nb[0], nb[1], nb[2]);
        if (!set.contains(nbPos)) return false;
        if (set.contains(nbPos.relative(face))) return false;

        int[][] nbEdges = faceEdges(nb[0], nb[1], nb[2], face);
        for (int[] ne : nbEdges) {
            if (sameEdgeUnordered(ne, e)) return true;
        }
        return false;
    }

    private static int coord(int[] e, int endpoint, int axis) {
        return e[axis + endpoint * 3];
    }
    private static int getCoord(int x, int y, int z, int axis) {
        return axis == 0 ? x : (axis == 1 ? y : z);
    }
    private static int[] neighborCoord(int x, int y, int z, int axis, int step) {
        if (axis == 0) return new int[]{x + step, y, z};
        if (axis == 1) return new int[]{x, y + step, z};
        return new int[]{x, y, z + step};
    }
    private static boolean sameEdgeUnordered(int[] a, int[] b) {
        boolean f = a[0]==b[0]&&a[1]==b[1]&&a[2]==b[2]&&a[3]==b[3]&&a[4]==b[4]&&a[5]==b[5];
        boolean r = a[0]==b[3]&&a[1]==b[4]&&a[2]==b[5]&&a[3]==b[0]&&a[4]==b[1]&&a[5]==b[2];
        return f || r;
    }

    /**
     * 无向边的精确 key。两个端点坐标各压成 1 个 long，组成 record，
     * JVM 自动生成无碰撞的 hashCode/equals。端点规范化（小的在前）保证无向。
     */
    private record EdgeKey(long lo, long hi) {
        static EdgeKey of(int[] e) {
            long a = packVert(e[0], e[1], e[2]);
            long b = packVert(e[3], e[4], e[5]);
            return a <= b ? new EdgeKey(a, b) : new EdgeKey(b, a);
        }
    }
    /** 单个网格顶点 → long。每轴 21 bit（±1,048,576），共 63 bit。 */
    private static long packVert(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
