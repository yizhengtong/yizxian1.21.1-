package net.minecraft.client.yiz.xian.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 紫昭明光连发 handler — 客户端检测右键按住（手持昭明法杖），
 * 每 1 tick 发送一次 C2S 施法请求 + 本地预测，按得越久每 tick 发射次数越多。
 * 替代 1.21.1 不可靠的 startUsingItem/onUseTick 持续使用链路。
 */
public final class ZhaoMingCastHandler {

    /** 每次连发的最小间隔 tick（1 = 每 tick 都发射） */
    private static final int FIRE_INTERVAL = 1;

    /**
     * 连发档位表：{按住时长 tick, 每 tick 发射次数}，按阈值升序。
     * 超过末档后沿用末档次数。后续想再提速，追加一行即可，
     * 如 {120, 3} 表示按住超过 6 秒后每 tick 发射 3 次。
     */
    private static final int[][] BURST_TIERS = {
        {60, 2},   // 按住超过 3 秒（20tps × 3s = 60 tick）→ 每 tick 发射 2 次
    };

    private static int holdTicks;
    private static int nextFire;

    private ZhaoMingCastHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack main = mc.player.getMainHandItem();
        if (!(main.getItem() instanceof net.minecraft.client.yiz.xian.item.WupinItem)) {
            holdTicks = 0;
            nextFire = 0;
            return;
        }
        if (!mc.options.keyUse.isDown()) {   // 松开右键
            holdTicks = 0;
            nextFire = 0;
            return;
        }
        holdTicks++;
        // 每 tick 都发射，发射次数按按住时长升档
        if (holdTicks < nextFire) return;
        nextFire = holdTicks + FIRE_INTERVAL;

        int burst = getBurstCount(holdTicks);
        for (int i = 0; i < burst; i++) {
            fireOnce(mc);
        }
    }

    /** 按住时长 → 每 tick 发射次数，按 BURST_TIERS 阈值升档 */
    private static int getBurstCount(int holdTicks) {
        int count = 1;
        for (int[] tier : BURST_TIERS) {
            if (holdTicks > tier[0]) {
                count = tier[1];
            } else {
                break;
            }
        }
        return count;
    }

    /** 单次施法：C2S 施法请求（服务端权威施法 + 消耗）+ 客户端本地预测 + 闪烁 */
    private static void fireOnce(Minecraft mc) {
        PacketDistributor.sendToServer(new net.minecraft.client.yiz.xian.network.C2SZhaoMingCastPayload());

        // 客户端本地预测 + 闪烁
        var pos = ZhaoMingLightClientManager.getInstance().add(mc.player, mc.player.getLookAngle());
        if (pos != null && mc.level instanceof ClientLevel cl) {
            for (int i = 0; i < 5; i++) {
                cl.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT,
                    pos.x, pos.y + 0.3, pos.z,
                    (cl.random.nextDouble() - 0.5) * 0.08,
                    (cl.random.nextDouble() - 0.5) * 0.02,
                    (cl.random.nextDouble() - 0.5) * 0.08);
            }
        }
    }
}
