package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 附加跳请求包 (C2S)。
 *
 * <p>客户端空中按下跳跃键（按下边沿）且有可用跳槽 → 乐观预测给向上速度（手感即时）→ 发本包。
 * 服务端权威校验并 {@link ExtraJumpData#tryConsume} 消耗一次（available-1 + usedThisFall+1），
 * 通过 PlayerDataAPI 自动 S2C 同步纠正客户端数据。</p>
 *
 * <p>无数据负载，仅作为"请求一次附加跳"的信号（仿 {@code C2SBoostPayload}）。</p>
 */
public record C2SExtraJumpPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SExtraJumpPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "extra_jump"));

    public static final StreamCodec<FriendlyByteBuf, C2SExtraJumpPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SExtraJumpPayload decode(FriendlyByteBuf buf) {
            return new C2SExtraJumpPayload();
        }

        @Override
        public void encode(FriendlyByteBuf buf, C2SExtraJumpPayload payload) {
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：权威消耗一次附加跳。
     * <p>{@link ExtraJumpData#tryConsume} 内部同时累加 {@code usedThisFall}
     * （驱动落地坠落减免，见 {@code MixinExtraJumpFallDamage}）。
     * <b>不</b>归零 fallDistance —— 本下落从最高点累计，落地按 usedThisFall 减免。</p>
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ExtraJumpData.tryConsume(serverPlayer);
            }
        });
    }
}
