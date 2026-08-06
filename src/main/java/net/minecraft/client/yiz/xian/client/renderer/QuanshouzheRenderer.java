package net.minecraft.client.yiz.xian.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.client.model.QuanshouzheModel;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * 全首者渲染器 — 单图集纹理（3 纹理已合并）。
 * 模型整体缩小（翅膀展开比身高宽，翼尖下垂，碰撞箱按身体）。
 */
public class QuanshouzheRenderer extends MobRenderer<QuanshouzheEntity, QuanshouzheModel<QuanshouzheEntity>> {

    /** 模型缩放：原模型高约 66 像素，放大 1.5 倍后 Boss 高约 4.5 格（与碰撞箱 1.8×3.9 匹配）。 */
    private static final float MODEL_SCALE = 1.2F;

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID, "quanshouzhe"), "main");

    public QuanshouzheRenderer(EntityRendererProvider.Context context) {
        super(context, new QuanshouzheModel<>(context.bakeLayer(LAYER)), 1.0F);
    }

    @Override
    protected void scale(QuanshouzheEntity entity, PoseStack poseStack, float partialTick) {
        // 辖界者 = 原版 Warden 骨架（Blockbench 导出，mesh root 与 Warden 同基准），
        // 原版 WardenRenderer 无 translate 即脚踩地；之前旧翅膀模型的 translate(1.5) 会让模型下陷一格。
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(QuanshouzheEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID,
            "textures/entity/quanshouzhe/warden.png");
    }
}
