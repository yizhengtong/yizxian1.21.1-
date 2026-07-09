package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 多段跳突进请求包 (C2S) —— 鞘翅飞行时 TAB 触发。
 *
 * <h3>触发条件（客户端 BoostHandler.onClientTick 判定，优先度低于心之翅）</h3>
 * <ol>
 *   <li>{@code isFallFlying()}（鞘翅滑翔中）</li>
 *   <li>心之翅突进<b>不可用</b>（未装备 / 无层数 / 冷却中）—— 否则走 {@link C2SBoostPayload}</li>
 *   <li>有多段跳次数 且 多段跳突进冷却已过</li>
 * </ol>
 *
 * <h3>服务端处理</h3>
 * <p>{@link ExtraJumpData#tryConsumeForBoost}：按优先级消耗 1 次附加跳 + {@code usedThisFall}+1
 * （照常计入摔伤减免）+ 进入 {@link ExtraJumpData#BOOST_COOLDOWN_TICKS} 独立冷却。
 * <b>不</b>归零 fallDistance，与正常附加跳一致。</p>
 *
 * <p>无数据负载，仅作为"请求一次多段跳突进"的信号。客户端已乐观预测 {@code look×1} 速度，
 * 服务端只做权威消耗/冷却，通过 SyncPlayerDataPayload 纠正客户端。</p>
 */
public record C2SExtraJumpBoostPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SExtraJumpBoostPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "extra_jump_boost"));

    public static final StreamCodec<FriendlyByteBuf, C2SExtraJumpBoostPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SExtraJumpBoostPayload decode(FriendlyByteBuf buf) {
            return new C2SExtraJumpBoostPayload();
        }

        @Override
        public void encode(FriendlyByteBuf buf, C2SExtraJumpBoostPayload payload) {
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：权威消耗一次多段跳突进。客户端不在此处施加速度（已乐观预测）。 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ExtraJumpData.tryConsumeForBoost(serverPlayer);
            }
        });
    }
}
