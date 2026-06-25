package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimLoader;
import net.minecraft.client.yiz.xian.api.BlockbenchAnimParser;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 动画预览渲染器 — 在世界中固定位置循环渲染武器动画,
 * 玩家可绕圈观察各角度效果。
 */
public final class AnimationPreviewRenderer {

    private AnimationPreviewRenderer() {}

    /** 预览位置（世界坐标），null = 不显示 */
    public static Vec3 previewPos = null;
    /** 当前预览的动画索引 0/1/2 */
    public static int previewAnimIdx = 0;
    /** 预览用的武器 ItemStack */
    public static ItemStack previewStack = ItemStack.EMPTY;

    /** 切换预览 */
    public static void start(int animIdx, ItemStack stack, Vec3 pos) {
        previewAnimIdx = animIdx;
        previewStack = stack.copy();
        previewPos = pos;
    }

    public static void stop() {
        previewPos = null;
    }

    /** 在 RenderLevelStageEvent 中调用 */
    public static void render(PoseStack ps, MultiBufferSource.BufferSource buf, float partialTick) {
        if (previewPos == null || previewStack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemRenderer itemRenderer = mc.getItemRenderer();

        // 基于系统时间循环 swing
        long ms = System.currentTimeMillis() % 2000;
        float swing = ms / 2000f; // 0→1 每2秒一圈

        // 世界坐标 → 相机相对坐标
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        // ── 枢轴黑点标记（面朝相机，十字线 + 文字） ──
        ps.pushPose();
        ps.translate(previewPos.x - cam.x, previewPos.y - cam.y, previewPos.z - cam.z);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        mc.font.drawInBatch("§0●", -4, -4, 0x000000, false,
            ps.last().pose(), buf, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0, 15728880);
        mc.font.drawInBatch("§7枢轴", -10, 8, 0xAAAAAA, false,
            ps.last().pose(), buf, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0, 15728880);
        ps.popPose();

        // ── 武器模型 ──
        ps.pushPose();
        ps.translate(previewPos.x - cam.x, previewPos.y - cam.y, previewPos.z - cam.z);

        // 应用 Blockbench 动画旋转
        BlockbenchAnimLoader.applyAttack(ps, previewAnimIdx, swing);

        // 用 NONE 上下文 — 不加模型自带 display 变换，与 Blockbench 视角一致
        itemRenderer.renderStatic(previewStack, ItemDisplayContext.NONE,
            15728880, 0, ps, buf, mc.player.level(), 0);
        ps.popPose();

        // 显示动画名
        String[] names;
        if (BlockbenchAnimParser.loaded) {
            names = new String[BlockbenchAnimParser.ANIM_COUNT];
            for (int i = 0; i < names.length; i++) {
                names[i] = BlockbenchAnimParser.ANIMS[i] != null
                    ? BlockbenchAnimParser.ANIMS[i].name : "anim_" + i;
            }
        } else {
            names = new String[]{"左平砍","右平砍","左下→左上","左上→右下"};
        }
        var font = mc.font;
        ps.pushPose();
        ps.translate(previewPos.x - cam.x, previewPos.y - cam.y + 1.5, previewPos.z - cam.z);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        String label = String.format("§e▶ %s §7枢轴(%.2f,%.2f,%.2f)",
            names[previewAnimIdx],
            BlockbenchAnimLoader.pivotX, BlockbenchAnimLoader.pivotY, BlockbenchAnimLoader.pivotZ);
        font.drawInBatch(label, -font.width(label)/2f, 0, 0xFFFFFF, false,
            ps.last().pose(), buf, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
            0, 15728880);
        ps.popPose();
    }
}
