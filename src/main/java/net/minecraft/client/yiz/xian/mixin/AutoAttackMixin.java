package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动攻击 Mixin，三触发源（任一满足即生效）：
 * <ol>
 *   <li><b>ILeftHandRender 武器</b>（泰拉刃等）：按住攻击键 + 冷却满 → 自动攻击</li>
 *   <li><b>自动攻击附魔</b>（yizmodqzk:auto_attack）：持有附魔物品时按住攻击键 + 冷却满 → 自动攻击</li>
 *   <li><b>自动攻击属性</b>（yizmodqzk:auto_attack，疾射火炮等装备授予）：装备时按住攻击键 + 冷却满 → 自动攻击（免附魔）</li>
 * </ol>
 */
@Mixin(Minecraft.class)
public abstract class AutoAttackMixin {

    @Shadow public LocalPlayer player;
    @Shadow public abstract boolean startAttack();

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizxian_autoAttack(CallbackInfo ci) {
        if (player == null || player.isSpectator()) return;
        if (player.getAttackStrengthScale(0f) < 1.0f) return;

        boolean shouldAttack = false;

        // 条件 1：ILeftHandRender 武器 + 按住攻击键
        if (player.getMainHandItem().getItem() instanceof ILeftHandRender
                 && Minecraft.getInstance().options.keyAttack.isDown()) {
            shouldAttack = true;
        }
        // 条件 2：自动攻击附魔 + 按住攻击键
        else if (hasAutoAttackEnchantment(player)
                 && Minecraft.getInstance().options.keyAttack.isDown()) {
            shouldAttack = true;
        }
        // 条件 3：自动攻击属性（疾射火炮等装备授予，免附魔）+ 按住攻击键
        else if (player.getAttributeValue(YizAttributes.AUTO_ATTACK) > 0
                 && Minecraft.getInstance().options.keyAttack.isDown()) {
            shouldAttack = true;
        }

        // 必须瞄准实体且在近战范围内，避免空挥浪费冷却或误拆方块
        if (shouldAttack && !isAimingAtAttackableEntity(player)) {
            return;
        }

        if (shouldAttack) {
            startAttack();
        }
    }

    /** 检查玩家是否正在瞄准一个可攻击的实体且在近战范围内 */
    private static boolean isAimingAtAttackableEntity(LocalPlayer player) {
        var hit = Minecraft.getInstance().hitResult;
        if (hit == null) return false;
        if (!(hit instanceof net.minecraft.world.phys.EntityHitResult ehr)) return false;
        var entity = ehr.getEntity();
        if (entity == null || entity == player) return false;
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)) return false;
        // 必须在近战攻击范围内
        return player.distanceTo(entity) <= player.entityInteractionRange();
    }

    /** 检查主手物品是否拥有自动攻击附魔（yizmodqzk:auto_attack） */
    private static boolean hasAutoAttackEnchantment(LocalPlayer player) {
        var itemEnchantments = player.getMainHandItem()
            .getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : itemEnchantments.entrySet()) {
            var key = entry.getKey().getKey();
            if (key != null && key.location().toString().equals("yizmodqzk:auto_attack")) {
                return true;
            }
        }
        return false;
    }
}
