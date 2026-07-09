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
 * 让饰品槽中的鞘翅（心之翅）能<b>维持</b>滑翔飞行。
 *
 * <p>注入点：{@code LivingEntity.updateFallFlying()} 中
 * {@code setSharedFlag(7, bool)} 的 bool 参数。</p>
 *
 * <h3>updateFallFlying 的语义（关键）</h3>
 * <p>原版 {@code updateFallFlying} <b>只在玩家当前已在飞时</b>检查是否要<b>关闭</b>飞行
 * （胸甲槽无鞘翅 / 落地 / 骑乘 / 漂浮 → 关）；<b>不会</b>"开启"飞行（开启由
 * {@code tryToStartFallFlying} 玩家主动展翅负责）。所以传给 {@code setSharedFlag(7, bool)}
 * 的 {@code bool}：玩家在飞且胸甲有效 → true；其余 → false。</p>
 *
 * <h3>本 Mixin 的职责（仅"维持"，不"开启"）</h3>
 * <p>胸甲槽无鞘翅但饰品槽有心之翅时，原版会因为"胸甲无效"把<b>正在飞</b>的玩家关飞。
 * 本 Mixin 在此时兜底维持 true。<b>必须</b>确认玩家当前 {@code isFallFlying()==true}
 * （本来就在飞）才维持 —— 否则会把"没在飞的玩家"{@code setSharedFlag(7,false)} 改成 true，
 * 等于每 tick 强制开飞行 → 玩家离地瞬间自动展翅（用户 2026-07-05 反馈的 bug）。</p>
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
     * 只在「玩家本来就在飞、需饰品槽兜底维持」时返回 true，绝不主动开启飞行。
     */
    @ModifyArg(
        method = "updateFallFlying",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setSharedFlag(IZ)V"),
        index = 1
    )
    private boolean yizxian_keepElytraFlagFromAccessory(boolean original) {
        if (original) return true;   // 原版要开/保持飞行 → 照常

        if ((Object) this instanceof Player player) {
            // 落地必须停飞（否则永久飞）
            if (player.onGround()) return false;
            // ★ 关键：只在玩家「当前正在飞」时才维持 true（兜底饰品槽胸甲无效的情况）。
            // 若玩家没在飞（isFallFlying()==false），绝不能把 false 改成 true —— 那会每 tick
            // 强制开启飞行，导致离地瞬间自动展翅。
            if (player.isFallFlying() && AccessoryContainer.hasHeartWings(player)) {
                return true;
            }
        }
        return false;
    }
}
