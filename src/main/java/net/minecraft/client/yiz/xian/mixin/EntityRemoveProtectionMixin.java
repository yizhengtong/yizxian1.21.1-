package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.core.EntityRemoveProtection;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体移除保护 —— 本模组实体（{@link YizxianMob}）的移除总闸门。
 *
 * <p>拦截 {@link Entity#setRemoved} —— 这是 1.21.1 所有实体移除的<b>最终汇聚点</b>：
 * {@link Entity#remove}（discard→DISCARDED、kill/die→KILLED 等）内部调它，
 * 维度传送（{@code changeDimension}/{@code teleportTo}）也<b>直接调它</b>
 * （跳过 remove，字节码验证：{@code setRemoved(CHANGED_DIMENSION)} + {@code addDuringTeleport}）。</p>
 *
 * <p>白名单放行：</p>
 * <ol>
 *   <li>服务器停止 / 世界保存（退出游戏序列化保存实体后移除）</li>
 *   <li>本模组死亡监听（实体生命值 ≤0，见 {@link EntityRemoveProtection}）</li>
 *   <li>本模组包调用者（/yiz remove 强制移除、未来本模组维度传送方法等）</li>
 * </ol>
 * 其余任何模组/引擎的移除方式一律拦截 → 本模组实体无法被外力移除。
 */
@Mixin(Entity.class)
public abstract class EntityRemoveProtectionMixin {

    @Inject(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof YizxianMob)) return; // 只保护本模组实体
        if (self.level().isClientSide()) return;   // 客户端视觉移除放行
        if (shouldAllowRemove((YizxianMob) self)) return;
        ci.cancel(); // 其余移除一律拒绝
    }

    private boolean shouldAllowRemove(YizxianMob mob) {
        // 1. 服务器停止 / 世界保存（退出游戏序列化）
        if (mob.level() instanceof ServerLevel sl && !sl.getServer().isRunning()) return true;
        // 2. 本模组死亡监听放行（生命值 ≤0，测试期间临时关闭由 EntityRemoveProtection 管理）
        if (EntityRemoveProtection.consumeDeathAllow(mob.getUUID())) return true;
        // 3. 本模组包调用者（/yiz remove、未来本模组维度传送方法等）
        if (isYizCaller()) return true;
        return false;
    }

    /** 调用栈第一个决定性帧属于本模组包（net.minecraft.client.yiz，前置库+下游共用根）→ 本模组主动操作。 */
    private static boolean isYizCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            // 跳过框架帧：原版 Entity 内部链（setRemoved/remove）+ 本 Mixin 注入方法自身
            //（getStackTrace 的 i=3 就是 yizxianmod$protectRemove 注入帧，若不跳过，任何外部 remove
            //   都会因这一帧命中 net.minecraft.client.yiz 而被误判为"本模组操作" → 移除保护形同虚设）
            if (cn.equals("net.minecraft.world.entity.Entity")) continue;
            if (cn.equals("net.minecraft.client.yiz.xian.mixin.EntityRemoveProtectionMixin")) continue;
            if (cn.startsWith("net.minecraft.client.yiz")) return true;
            return false; // 第一个决定性帧不是本模组 → 不是本模组操作
        }
        return false;
    }
}
