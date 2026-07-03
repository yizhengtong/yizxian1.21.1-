package net.minecraft.client.yiz.xian.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.xian.api.BoostData;
import net.minecraft.client.yiz.xian.api.BoostRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 通用突进系统 —— 按键由 {@link HeartWingsKeyMappings} 注册，玩家可在设置中自定义。
 *
 * <p>突进来源由 {@link BoostRegistry} 提供（心之翅等 {@code BoostProvider}），
 * 与具体物品解耦。突进数据见 {@link BoostData}。</p>
 */
public final class BoostHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    private BoostHandler() {}

    private static boolean wasFlying;

    // ── 客户端：按键处理 ──

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isFallFlying()) return;
        if (!BoostRegistry.hasActive(mc.player)) return;

        // TAB：推进（consumeClick 只触发一次，防多次消耗）
        if (HeartWingsKeyMappings.BOOST.consumeClick()) {
            if (BoostData.tryConsume(mc.player)) {
                Vec3 look = mc.player.getLookAngle();
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(look.scale(1.8)));
                mc.player.hurtMarked = true;
            }
        }

        // G：悬停（在 MixinHoverLock.travel HEAD 中焊死，此处仅保证客户端即时反馈）
        if (HeartWingsKeyMappings.HOVER.isDown()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    // ── 服务端：自动恢复 + 落地归零（悬停由 MixinHoverLock 处理）──

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!BoostRegistry.hasActive(player)) return;

        // 自动恢复
        BoostData.tickRegen(player);

        // 落地归零
        boolean flying = player.isFallFlying();
        boolean onGround = player.onGround();
        if (wasFlying && !flying && onGround) {
            BoostData.onLand(player);
        }
        wasFlying = flying;
    }
}
