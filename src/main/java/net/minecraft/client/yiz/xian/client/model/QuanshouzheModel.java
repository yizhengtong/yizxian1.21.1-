package net.minecraft.client.yiz.xian.client.model;

import net.minecraft.client.animation.definitions.WardenAnimation;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.util.Mth;

/**
 * 辖界者 — 同人坚守者。
 * 模型完全照抄原版 {@link net.minecraft.client.model.WardenModel} 的骨骼层级
 * （root→bone(24)→body→head→tendrils/arms/ribcages，bone→legs），
 * 头顶两个装饰（原 head2）挂到 head 下随头转动。
 * 动画同样照抄原版：idle/walk/tendrils + WardenAnimation 关键帧（attack/roar/sonic/dig/emerge/sniff）。
 */
public class QuanshouzheModel<T extends QuanshouzheEntity> extends HierarchicalModel<T> {

    private final ModelPart root;
    private final ModelPart bone;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightTendril;
    private final ModelPart leftTendril;
    private final ModelPart leftLeg;
    private final ModelPart leftArm;
    private final ModelPart leftRibcage;
    private final ModelPart rightLeg;
    private final ModelPart rightArm;
    private final ModelPart rightRibcage;

    public QuanshouzheModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.bone = root.getChild("bone");
        this.body = this.bone.getChild("body");
        this.head = this.body.getChild("head");
        this.rightTendril = this.head.getChild("right_tendril");
        this.leftTendril = this.head.getChild("left_tendril");
        this.rightLeg = this.bone.getChild("right_leg");
        this.leftLeg = this.bone.getChild("left_leg");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightRibcage = this.body.getChild("right_ribcage");
        this.leftRibcage = this.body.getChild("left_ribcage");
    }

    /** 照抄原版 WardenModel.createBodyLayer + 头顶两个装饰（head2）挂在 head 下。 */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-9.0F, -13.0F, -4.0F, 18.0F, 21.0F, 11.0F), PartPose.offset(0.0F, -21.0F, 0.0F));
        body.addOrReplaceChild("right_ribcage", CubeListBuilder.create()
            .texOffs(90, 11).addBox(-2.0F, -11.0F, -0.1F, 9.0F, 21.0F, 0.0F), PartPose.offset(-7.0F, -2.0F, -4.0F));
        body.addOrReplaceChild("left_ribcage", CubeListBuilder.create()
            .texOffs(90, 11).mirror().addBox(-7.0F, -11.0F, -0.1F, 9.0F, 21.0F, 0.0F).mirror(false), PartPose.offset(7.0F, -2.0F, -4.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 32).addBox(-8.0F, -16.0F, -5.0F, 16.0F, 16.0F, 10.0F), PartPose.offset(0.0F, -13.0F, 0.0F));
        head.addOrReplaceChild("right_tendril", CubeListBuilder.create()
            .texOffs(52, 32).addBox(-16.0F, -13.0F, 0.0F, 16.0F, 16.0F, 0.0F), PartPose.offset(-8.0F, -12.0F, 0.0F));
        head.addOrReplaceChild("left_tendril", CubeListBuilder.create()
            .texOffs(58, 0).addBox(0.0F, -13.0F, 0.0F, 16.0F, 16.0F, 0.0F), PartPose.offset(8.0F, -12.0F, 0.0F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create()
            .texOffs(44, 50).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 28.0F, 8.0F), PartPose.offset(-13.0F, -13.0F, 1.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create()
            .texOffs(0, 58).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 28.0F, 8.0F), PartPose.offset(13.0F, -13.0F, 1.0F));
        bone.addOrReplaceChild("right_leg", CubeListBuilder.create()
            .texOffs(76, 48).addBox(-3.1F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F), PartPose.offset(-5.9F, -13.0F, 0.0F));
        bone.addOrReplaceChild("left_leg", CubeListBuilder.create()
            .texOffs(76, 76).addBox(-2.9F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F), PartPose.offset(5.9F, -13.0F, 0.0F));
        // 头顶两个装饰（辖界者原 head2），挂 head 下随头转动；box 相对 head（y=-14 到 -6）
        head.addOrReplaceChild("head2", CubeListBuilder.create()
            .texOffs(0, 35).addBox(8.0F, -14.0F, -5.0F, 8.0F, 8.0F, 8.0F)
            .texOffs(0, 35).addBox(-15.0F, -14.0F, -5.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** 照抄原版 WardenModel.setupAnim。 */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float f = ageInTicks - entity.tickCount;
        animateHeadLookTarget(netHeadYaw, headPitch);
        animateWalk(limbSwing, limbSwingAmount);
        animateIdlePose(ageInTicks);
        animateTendrils(entity, ageInTicks, f);
        // A/B/C 攻击统一使用常规攻击动画、正常播放时间（重击不再放缓）
        this.animate(entity.attackAnimationState, WardenAnimation.WARDEN_ATTACK, ageInTicks);
        this.animate(entity.sonicBoomAnimationState, WardenAnimation.WARDEN_SONIC_BOOM, ageInTicks);
        this.animate(entity.diggingAnimationState, WardenAnimation.WARDEN_DIG, ageInTicks);
        this.animate(entity.emergeAnimationState, WardenAnimation.WARDEN_EMERGE, ageInTicks);
        this.animate(entity.roarAnimationState, WardenAnimation.WARDEN_ROAR, ageInTicks);
        this.animate(entity.sniffAnimationState, WardenAnimation.WARDEN_SNIFF, ageInTicks);
    }

    private void animateHeadLookTarget(float yaw, float pitch) {
        this.head.xRot = pitch * Mth.DEG_TO_RAD;
        this.head.yRot = yaw * Mth.DEG_TO_RAD;
    }

    private void animateIdlePose(float ageInTicks) {
        float f = ageInTicks * 0.1F;
        float f1 = Mth.cos(f);
        float f2 = Mth.sin(f);
        this.head.zRot += 0.06F * f1;
        this.head.xRot += 0.06F * f2;
        this.body.zRot += 0.025F * f2;
        this.body.xRot += 0.025F * f1;
    }

    private void animateWalk(float limbSwing, float limbSwingAmount) {
        float f = Math.min(0.5F, 3.0F * limbSwingAmount);
        float f1 = limbSwing * 0.8662F;
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Math.min(0.35F, f);
        this.head.zRot += 0.3F * f3 * f;
        this.head.xRot = this.head.xRot + 1.2F * Mth.cos(f1 + Mth.HALF_PI) * f4;
        this.body.zRot = 0.1F * f3 * f;
        this.body.xRot = 1.0F * f2 * f4;
        this.leftLeg.xRot = 1.0F * f2 * f;
        this.rightLeg.xRot = 1.0F * Mth.cos(f1 + Mth.PI) * f;
        this.leftArm.xRot = -(0.8F * f2 * f);
        this.leftArm.zRot = 0.0F;
        this.rightArm.xRot = -(0.8F * f3 * f);
        this.rightArm.zRot = 0.0F;
        this.resetArmPoses();
    }

    private void resetArmPoses() {
        this.leftArm.yRot = 0.0F;
        this.leftArm.z = 1.0F;
        this.leftArm.x = 13.0F;
        this.leftArm.y = -13.0F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.z = 1.0F;
        this.rightArm.x = -13.0F;
        this.rightArm.y = -13.0F;
    }

    private void animateTendrils(T entity, float ageInTicks, float partialTick) {
        float f = entity.getTendrilAnimation(partialTick) * (float) (Math.cos(ageInTicks * 2.25) * Math.PI * 0.1);
        this.leftTendril.xRot = f;
        this.rightTendril.xRot = -f;
    }
}
