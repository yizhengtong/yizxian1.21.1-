package net.minecraft.client.yiz.xian.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.xian.api.BoostData;
import net.minecraft.client.yiz.xian.api.BoostRegistry;
import net.minecraft.client.yiz.xian.network.C2SBoostPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 通用突进系统 —— 按键由 {@link HeartWingsKeyMappings} 注册，玩家可在设置中自定义。
 *
 * <p>突进来源由 {@link BoostRegistry} 提供（心之翅等 {@code BoostProvider}），
 * 与具体物品解耦。突进数据见 {@link BoostData}。</p>
 *
 * <h3>客户端/服务端分工</h3>
 * <ul>
 *   <li>客户端：按键检测 → 乐观预测速度（即时手感）→ 发 C2SBoostPayload 到服务端</li>
 *   <li>服务端：收包 → 校验冷却/充能 → 消耗 + 重置恢复 → 给客户端同步正确数据</li>
 *   <li>服务端 tick：自动恢复</li>
 * </ul>
 */
public final class BoostHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    private BoostHandler() {}

    // ── 客户端：按键处理 ──

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isFallFlying()) return;
        if (!BoostRegistry.hasActive(mc.player)) return;

        // TAB：推进（consumeClick 只触发一次，防多次消耗）
        if (HeartWingsKeyMappings.BOOST.consumeClick()) {
            // 本地乐观检查：冷却中或无充能 → 跳过，不发包
            if (BoostData.getCooldown(mc.player) > 0) return;
            if (BoostData.getBoosts(mc.player) <= 0) return;
            // 乐观预测：立即在客户端施加速度（手感即时），同时发包到服务端做权威校验
            // 服务端校验不通过时客户端数据会被 SyncPlayerDataPayload 纠正
            Vec3 look = mc.player.getLookAngle();
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(look.scale(1.8)));
            mc.player.hurtMarked = true;
            PacketDistributor.sendToServer(new C2SBoostPayload());
        }

        // G：悬停（在 MixinHoverLock.travel HEAD 中焊死，此处仅保证客户端即时反馈）
        if (HeartWingsKeyMappings.HOVER.isDown()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    // ── 服务端：自动恢复（悬停由 MixinHoverLock 处理）──

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!BoostRegistry.hasActive(player)) return;

        // 自动恢复 + 冷却递减
        BoostData.tickRegen(player);
    }

}
