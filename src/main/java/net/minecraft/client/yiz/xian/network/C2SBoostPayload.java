package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.api.BoostData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 突进请求包 (C2S)
 * 客户端 TAB 按下 → 服务端校验并消耗突进。
 * 无数据负载，仅作为"请求使用一次突进"的信号。
 */
public record C2SBoostPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SBoostPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "boost"));

    public static final StreamCodec<FriendlyByteBuf, C2SBoostPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SBoostPayload decode(FriendlyByteBuf buf) {
            return new C2SBoostPayload();
        }

        @Override
        public void encode(FriendlyByteBuf buf, C2SBoostPayload payload) {
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：校验冷却/充能 → 消耗 → 恢复计时重置 → PlayerDataAPI 自动同步回客户端。 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                BoostData.tryConsume(serverPlayer);
                // 不在此处施加速度：客户端已乐观预测速度（手感即时），
                // 服务端只负责权威数据校验/消耗/恢复，通过 SyncPlayerDataPayload 纠正客户端数据
            }
        });
    }
}
