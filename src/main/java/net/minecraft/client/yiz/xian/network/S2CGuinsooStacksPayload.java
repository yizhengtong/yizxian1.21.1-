package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 鬼索叠层同步包 (S2C)。
 *
 * <p>服务端攻击叠层/衰减/切手后仅发给玩家本人 6 槽层数，
 * 客户端缓存供 HUD 渲染读取。取代旧 PlayerDataAPI 全量同步方案。</p>
 */
public record S2CGuinsooStacksPayload(int s0, int s1, int s2, int s3, int s4, int s5)
        implements CustomPacketPayload {

    public static final Type<S2CGuinsooStacksPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "guinsoo_stacks"));

    public static final StreamCodec<ByteBuf, S2CGuinsooStacksPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s0,
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s1,
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s2,
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s3,
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s4,
            ByteBufCodecs.VAR_INT, S2CGuinsooStacksPayload::s5,
            S2CGuinsooStacksPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendTo(ServerPlayer player, int[] stacks) {
        PacketDistributor.sendToPlayer(player,
            new S2CGuinsooStacksPayload(stacks[0], stacks[1], stacks[2],
                stacks[3], stacks[4], stacks[5]));
    }

    public static void handle(S2CGuinsooStacksPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GuinsooRagebladeItem.cacheClientStacks(
            new int[]{payload.s0, payload.s1, payload.s2, payload.s3, payload.s4, payload.s5}));
    }
}
