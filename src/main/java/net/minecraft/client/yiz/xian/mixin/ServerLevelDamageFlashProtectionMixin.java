package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 受击红闪门控 — 拦截 {@link ServerLevel#broadcastDamageEvent}，只放行本模组传导扣血流程的红闪。
 *
 * <p><b>问题</b>：红闪唯一正确触发 = {@code broadcastDamageEvent} → 客户端 {@code handleDamageEvent}
 * → {@code hurtTime=10} → 变红。寰宇支配之剑（InfinitySwordItem）绕过 hurt() 直接调
 * {@code victim.level().broadcastDamageEvent}（其源码 161 行），每次攻击都红、不经过传导 CD → 疯狂变红。</p>
 *
 * <p><b>门控</b>：对 {@link YizxianMob}（本模组实体），仅当当前线程处于 {@link QuanshouzheEntity}
 * 的传导扣血流程（{@link QuanshouzheEntity#isConductionHitFlash()}）才放行广播；
 * 外部模组直接调（标记未设）→ 拦截 → 不红。传导 CD 内 {@code hurt()} return false 本就不广播 → 不红。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelDamageFlashProtectionMixin {

    @Inject(method = "broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$gateDamageFlash(Entity entity, DamageSource damageSource, CallbackInfo ci) {
        // 只门控本模组实体（YizxianMob）——玩家/普通实体不受影响
        if (!(entity instanceof YizxianMob)) return;
        // 传导扣血流程（hurt() 内 set 标记）→ 放行本次红闪
        if (QuanshouzheEntity.isConductionHitFlash()) return;
        // 外部模组绕过 hurt() 直接调 broadcastDamageEvent → 拦截（不疯狂变红）
        ci.cancel();
    }
}
