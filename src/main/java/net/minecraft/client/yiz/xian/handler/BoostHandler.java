package net.minecraft.client.yiz.xian.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.xian.api.BoostData;
import net.minecraft.client.yiz.xian.api.BoostRegistry;
import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.client.yiz.xian.network.C2SBoostPayload;
import net.minecraft.client.yiz.xian.network.C2SExtraJumpBoostPayload;
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
 * 与具体物品解耦。心之翅突进数据见 {@link BoostData}。</p>
 *
 * <h3>TAB 调度（用户决策 2026-07-05：多段跳突进优先度低于心之翅）</h3>
 * <p>鞘翅飞行中按 TAB，按优先级判定走哪条路（客户端乐观预测 + 发对应包，服务端权威消耗）：</p>
 * <ol>
 *   <li><b>心之翅突进</b>（优先）：装备心之翅 且 不在 30 tick 冷却 且 有层数 →
 *       {@code look×1.8} + {@link C2SBoostPayload}</li>
 *   <li><b>多段跳突进</b>（备胎）：心之翅不可用，但有附加跳次数 且 不在 16 tick 冷却 →
 *       {@code look×1} + {@link C2SExtraJumpBoostPayload}，消耗 1 次附加跳，
 *       {@code usedThisFall}+1（照常计入摔伤减免）</li>
 * </ol>
 * <p>两套冷却<b>独立</b>：心之翅 1.5s、多段跳 0.8s。心之翅进入冷却后可立刻用多段跳顶上。</p>
 *
 * <h3>客户端/服务端分工</h3>
 * <ul>
 *   <li>客户端：按键检测 → 乐观预测速度（即时手感）→ 发对应 C2S 包</li>
 *   <li>服务端：收包 → 校验冷却/充能 → 消耗 + 重置 → PlayerDataAPI 自动同步纠正客户端</li>
 *   <li>服务端 tick：心之翅自动恢复（{@link BoostData#tickRegen}）+ 多段跳突进冷却递减
 *       （{@link ExtraJumpData#tickCooldown}，由 {@code ExtraJumpHandler} 调用）</li>
 * </ul>
 */
public final class BoostHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * 心之翅突进速度系数（沿视线 {@code add(look.scale(s))}，瞬时增量）。
     * <p>用户决策 2026-07-06：2.0 —— 主力突进，给足"推进感"。手感偏弱/偏强可在此微调。</p>
     */
    private static final double HEART_WINGS_BOOST_SCALE = 2.0;
    /**
     * 多段跳突进速度系数（弱于心之翅，体现"备胎"定位）。
     * <p>用户决策 2026-07-06：1.2 —— 明显弱于心之翅 2.0，但仍能给有效推进。</p>
     */
    private static final double EXTRA_JUMP_BOOST_SCALE = 1.2;

    private BoostHandler() {}

    // ── 客户端：按键处理 ──

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        // 突进（含多段跳突进）只在鞘翅飞行时可用
        if (!mc.player.isFallFlying()) return;

        // TAB：按优先级调度心之翅 / 多段跳突进（consumeClick 只触发一次，防多次消耗）
        if (HeartWingsKeyMappings.BOOST.consumeClick()) {
            tryBoost(mc.player);
        }

        // G：悬停（心之翅专属，在 MixinHoverLock.travel HEAD 中焊死，此处仅保证客户端即时反馈）
        if (BoostRegistry.hasActive(mc.player) && HeartWingsKeyMappings.HOVER.isDown()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * TAB 突进调度：心之翅优先，否则多段跳。客户端乐观预测速度 + 发对应包。
     * 两路都失败（都没冷却到位/没资源）则什么都不做。
     */
    private static void tryBoost(Player player) {
        // 1) 心之翅突进（优先）：有 active provider + 冷却已过 + 有层数
        if (BoostRegistry.hasActive(player)
                && BoostData.getCooldown(player) <= 0
                && BoostData.getBoosts(player) > 0) {
            applyBoost(player, HEART_WINGS_BOOST_SCALE);
            PacketDistributor.sendToServer(new C2SBoostPayload());
            return;
        }
        // 2) 多段跳突进（备胎）：有附加跳次数 且 冷却已过
        if (ExtraJumpData.canBoost(player)) {
            applyBoost(player, EXTRA_JUMP_BOOST_SCALE);
            PacketDistributor.sendToServer(new C2SExtraJumpBoostPayload());
        }
    }

    /** 乐观预测：沿视线叠加 scale 倍单位向量速度，标记 hurt 让服务端接收。 */
    private static void applyBoost(Player player, double scale) {
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(player.getDeltaMovement().add(look.scale(scale)));
        player.hurtMarked = true;
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
