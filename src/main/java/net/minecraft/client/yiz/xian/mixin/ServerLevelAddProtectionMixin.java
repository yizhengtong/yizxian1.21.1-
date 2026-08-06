package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ServerLevel 实体新增保护 —— 本模组实体（{@link YizxianMob}）的新增闸门（配合 EntityRemoveProtectionMixin）。
 *
 * <p><b>addFreshEntity</b>（一般新增：召唤/存档加载）：本模组包生成 + 引擎存档加载恢复放行，
 * 其余（其他模组直接新增本模组实体）拦截。</p>
 *
 * <p><b>addDuringTeleport</b>（跨维度传送新增专用方法）：对 {@link YizxianMob} 一律拦截 ——
 * 跨维度传送在移除端（Entity.remove）已被断，新增端再兜一层双保险。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelAddProtectionMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof YizxianMob)) return; // 只保护本模组实体
        if (shouldAllowAdd(entity)) return;
        cir.setReturnValue(false); // 新增失败
    }

    @Inject(method = "addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectAddDuringTeleport(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        ci.cancel(); // 跨维度传送新增一律拦截
    }

    private boolean shouldAllowAdd(Entity entity) {
        // 1. 本模组包生成/召唤
        if (isYizCaller()) return true;
        // 2. 引擎帧（存档加载 LevelChunk 等恢复流程）→ 放行
        return isEngineRestore();
    }

    /** 调用栈第一个决定性帧属于本模组包 → 本模组主动操作。 */
    private static boolean isYizCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.world.level.ServerLevel")) continue;
            if (cn.startsWith("net.minecraft.client.yiz")) return true;
            return false;
        }
        return false;
    }

    /** 引擎帧（存档加载等）→ 放行；其他模组直接调用 → 拦。 */
    private static boolean isEngineRestore() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.world.level.ServerLevel")) continue;
            if (cn.startsWith("net.minecraft.")
                    || cn.startsWith("net.neoforged.")
                    || cn.startsWith("com.mojang.")) {
                return true;
            }
            return false;
        }
        return false;
    }
}
