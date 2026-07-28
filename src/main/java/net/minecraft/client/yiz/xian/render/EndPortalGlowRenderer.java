package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.*;

@EventBusSubscriber(value = Dist.CLIENT, modid = "yizxianmod")
public final class EndPortalGlowRenderer {

    private static final int RANGE = 128;
    private static final int SCAN_TICKS = 100;
    private static int tick = 0;
    private static final Set<BlockPos> frameSet = new HashSet<>();

    private static final RenderType GLOW_LINES;
    static {
        var s = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(3.0)))
            // 关键修复：
            //  - NO_LAYERING：VIEW_OFFSET_Z_LAYERING 会按深度分多层偏移，穿墙时同一条线被画到多个 z-offset 层 → 重影/“双线”假象
            //  - MAIN_TARGET：ITEM_ENTITY_TARGET 带后处理光晕，会把单线糊成宽亮带，看着像“接触面轮廓”
            //  - COLOR_WRITE：穿墙只写颜色，不污染深度缓冲（原 COLOR_DEPTH_WRITE 会干扰后续渲染）
            .setLayeringState(RenderStateShard.NO_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .createCompositeState(false);
        GLOW_LINES = RenderType.create("end_portal_glow",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            1536, false, false, s);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;
        if (!hasBrightEye(p)) return;

        tick++;
        if (tick % SCAN_TICKS == 1) {
            frameSet.clear();
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            var lvl = p.level(); var c = p.blockPosition();
            for (int dx = -RANGE; dx <= RANGE; dx++)
                for (int dz = -RANGE; dz <= RANGE; dz++)
                    for (int dy = -30; dy <= 30; dy++) {
                        mp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        if (lvl.getBlockState(mp).is(Blocks.END_PORTAL_FRAME))
                            frameSet.add(mp.immutable());
                    }
        }
        if (frameSet.isEmpty()) return;

        // ── 生成外轮廓棱（共面合并后的大面边界） ──
        // 思路：遍历每个暴露面，收集它的 4 条棱为候选；但若某条棱是「两个共面相邻单元的内部接缝」，
        // 即两侧的方块在该棱所属的同一个面上共面相连，则该棱属于合并大面的内部，不画。
        // 这能正确处理任意形状（L 形、环形、T 形），且天然去重（用 EdgeKey Set）。
        Set<EdgeKey> seen = new HashSet<>();
        List<int[]> draw = new ArrayList<>(); // each = {x1,y1,z1, x2,y2,z2}

        for (BlockPos pos : frameSet) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            for (Direction face : Direction.values()) {
                if (frameSet.contains(pos.relative(face))) continue; // 接触面，跳过

                for (int[] e : faceEdges(x, y, z, face)) {
                    if (!seen.add(EdgeKey.of(e))) continue;          // 已加入，跳过
                    if (isCoplanarSeam(e, x, y, z, face, frameSet)) continue; // 共面内部接缝，丢弃
                    draw.add(e);
                }
            }
        }

        // ── 渲染：MultiBufferSource + 自定义穿墙 LINES RenderType（能稳定画出的基线） ──
        Vec3 cam = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        MultiBufferSource.BufferSource bfs = mc.renderBuffers().bufferSource();
        VertexConsumer v = bfs.getBuffer(GLOW_LINES);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var m = pose.last();
        float r = 0.2f, g = 1.0f, b = 0.8f, a = 1.0f;

        for (int[] e : draw) {
            float nx = e[0] != e[3] ? Math.signum(e[3] - e[0]) : 0f;
            float ny = e[1] != e[4] ? Math.signum(e[4] - e[1]) : 0f;
            float nz = e[2] != e[5] ? Math.signum(e[5] - e[2]) : 0f;
            v.addVertex(m, e[0], e[1], e[2]).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
            v.addVertex(m, e[3], e[4], e[5]).setColor(r, g, b, a).setNormal(m, nx, ny, nz);
        }

        pose.popPose();
        bfs.endBatch(GLOW_LINES);
    }

    /** 方块某个暴露面的 4 条边（整数网格坐标） */
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
     *
     * 等价条件：沿 e 的「面内侧向」迈一步得到邻居单元 nb（在 frameSet），
     * 且 nb 的同 face 也暴露（nb+face法向 不在 set），且 e 也是 nb 该 face 面的一条边。
     * 对 e 的两个面内侧向都要检查（e 可能两侧都有共面邻居）。
     */
    private static boolean isCoplanarSeam(int[] e, int x, int y, int z, Direction face, Set<BlockPos> frameSet) {
        // face 的法向轴 + 面内两个轴
        int axis = face.getAxis().ordinal(); // X=0,Y=1,Z=2
        // 面内两个轴 a1, a2
        int a1 = (axis == 0) ? 1 : 0;        // 非 face 轴的第一个
        int a2 = (axis == 0) ? 2 : (axis == 1 ? 2 : 1); // 非 face 轴的第二个
        // e 沿哪个轴延伸？比较端点
        boolean eAlongA1 = (coord(e, 0, a1) != coord(e, 1, a1));
        // e 的「面内侧向」= 面内两轴中 e 不沿的那个
        int sideAxis = eAlongA1 ? a2 : a1;

        // e 在 face 平面上的「侧向」有两个方向：+side / -side。
        // 计算 e 的中点在 sideAxis 上的坐标（整数），判断 e 位于方块 (x,y,z) 的哪一侧。
        // 取 e 端点在 sideAxis 上的值（两端相同，因为 e 不沿 sideAxis）
        int eSideCoord = coord(e, 0, sideAxis);
        int blockSideCoord = getCoord(x, y, z, sideAxis);
        // e 在方块的下侧（坐标 == blockSideCoord）或上侧（== blockSideCoord+1）
        boolean eAtLowSide = (eSideCoord == blockSideCoord);
        int sideStep = eAtLowSide ? -1 : +1;

        // 邻居单元 = (x,y,z) 沿 sideAxis 移动 sideStep
        int[] nb = neighborCoord(x, y, z, sideAxis, sideStep);
        BlockPos nbPos = new BlockPos(nb[0], nb[1], nb[2]);
        if (!frameSet.contains(nbPos)) return false;
        // 邻居的同 face 必须也暴露（否则 e 是邻居的接触面边，仍该画）
        if (frameSet.contains(nbPos.relative(face))) return false;

        // 验证 e 确实是邻居该 face 面的一条边（几何一致性）
        int[][] nbEdges = faceEdges(nb[0], nb[1], nb[2], face);
        for (int[] ne : nbEdges) {
            if (sameEdgeUnordered(ne, e)) return true;
        }
        return false;
    }

    private static int coord(int[] e, int endpoint, int axis) {
        return e[axis + endpoint * 3]; // e = {x1,y1,z1, x2,y2,z2}
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
     * 由 JVM 自动生成无碰撞的 hashCode/equals，彻底避免手工位压缩的 XOR 重叠问题。
     * 端点规范化（小的在前）保证 (A→B) 与 (B→A) 视为同一条边。
     */
    private record EdgeKey(long lo, long hi) {
        static EdgeKey of(int[] e) {
            long a = packVert(e[0], e[1], e[2]);
            long b = packVert(e[3], e[4], e[5]);
            return a <= b ? new EdgeKey(a, b) : new EdgeKey(b, a);
        }
    }
    /** 单个网格顶点 → long。每轴 21 bit（±1,048,576，远超扫描半径），共 63 bit，无符号扩展。 */
    private static long packVert(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static boolean hasBrightEye(Player p) {
        for (ItemStack s : p.getInventory().items)
            if (s.getItem() == net.minecraft.client.yiz.xian.YizxianMod.BRIGHT_ENDER_EYE.get())
                return true;
        return false;
    }
}
