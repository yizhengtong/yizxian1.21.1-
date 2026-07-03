package net.minecraft.client.yiz.xian.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让饰品槽中的鞘翅/心之翅渲染在玩家背上。
 *
 * <p>两个拦截点：</p>
 * <ol>
 *   <li>{@code getItemBySlot(CHEST)} → 胸甲槽空时查饰品槽，返回鞘翅或心之翅</li>
 *   <li>{@code RenderType.armorCutoutNoCull(texture)} → 心之翅时替换为自定义纹理</li>
 * </ol>
 */
@Mixin(ElytraLayer.class)
public class MixinElytraLayerRender {

    private static final ResourceLocation HEART_WINGS_TEX =
        ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/entity/heart_wings.png");

    @Unique
    private boolean yizxian$isHeartWings;

    // ── 1) 渲染前检测是否为心之翅 ──

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"))
    private void yizxian_checkHeartWings(PoseStack a, MultiBufferSource b, int c, LivingEntity entity,
                                          float d, float e, float f, float g, float h, float i, CallbackInfo ci) {
        this.yizxian$isHeartWings = false;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            // 心之翅纹理判定（区分于普通鞘翅）：查饰品槽是否装备心之翅
            ItemStack elytra = AccessoryContainer.findElytra(player);
            if (elytra.getItem() instanceof HeartWingsItem) {
                this.yizxian$isHeartWings = true;
            }
        }
    }

    // ── 2) 胸甲槽空 → 查饰品槽 ──

    @Redirect(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE", target = "net/minecraft/world/entity/LivingEntity.getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;")
    )
    private ItemStack yizxian_fakeChestElytra(LivingEntity entity, EquipmentSlot slot) {
        ItemStack real = entity.getItemBySlot(slot);
        if (slot != EquipmentSlot.CHEST) return real;
        if (!real.isEmpty()) return real;

        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            ItemStack accessory = AccessoryContainer.findElytra(player);
            if (accessory != ItemStack.EMPTY) {
                // 心之翅 → 返回假原版鞘翅骗过 vanilla is(ELYTRA) 检查；
                // 真纹理由下方的 @Redirect armorsCutoutNoCull 替换为自定义纹理
                return accessory.getItem() instanceof HeartWingsItem ? new ItemStack(Items.ELYTRA) : accessory;
            }
        }
        return real;
    }

    // ── 3) 心之翅 → Y轴180度旋转（修正纹理方向）──
    // 注入点选 pushPose() 之后，此时 PoseStack 已准备好

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void yizxian_rotateHeartWings(PoseStack a, MultiBufferSource b, int c, LivingEntity entity,
                                           float d, float e, float f, float g, float h, float i, CallbackInfo ci) {
        // 心之翅模型方向已正确，无需额外旋转
    }

    // ── 4) 心之翅 → 自定义纹理 ──

    @Redirect(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;")
    )
    private RenderType yizxian_heartWingsTexture(ResourceLocation original) {
        if (this.yizxian$isHeartWings) {
            return RenderType.armorCutoutNoCull(HEART_WINGS_TEX);
        }
        return RenderType.armorCutoutNoCull(original);
    }
}
