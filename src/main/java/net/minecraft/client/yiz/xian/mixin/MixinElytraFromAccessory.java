package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 让饰品槽中的鞘翅也能启动和维持滑翔飞行。
 *
 * <p>注入点：{@code LivingEntity.updateFallFlying()} 中
 * {@code setSharedFlag(7, bool)} 的 bool 参数。
 * 原版判断胸甲槽无鞘翅 → 标志置 false → 本 Mixin 检查饰品槽有鞘翅 → 改为 true。</p>
 *
 * <p>能力判定走统一入口 {@link AccessoryContainer#hasHeartWings}（双端跑：客户端查 _c、
 * 服务端查 _s；内部 getIfExists 不创建空实例）。参照 Caelus 的实现模式，不处理耐久度消耗。</p>
 */
@Mixin(LivingEntity.class)
public abstract class MixinElytraFromAccessory extends Entity {

    public MixinElytraFromAccessory(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * 修改 updateFallFlying() 内 setSharedFlag(7, bool) 的 bool 参数。
     */
    @ModifyArg(
        method = "updateFallFlying",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"),
        index = 1
    )
    private boolean yizxian_keepElytraFlagFromAccessory(boolean original) {
        if (original) return true;

        if ((Object) this instanceof Player player) {
            // 落地时尊重原版 false（停飞），否则会"永久飞"。
            // 注意：不能加 isInWater —— 原版鞘翅入水仍保持滑翔，加了会导致本模组滑翔入水瞬间停飞。
            if (player.onGround()) {
                return false;
            }
            // 统一能力入口（getIfExists，不创建空实例并缓存）
            if (AccessoryContainer.hasHeartWings(player)) return true;
        }
        return false;
    }
}
