package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.yiz.xian.item.equipment.ExplorerVambraceItem;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 探索者护臂层数同步包 (S2C) — 6 装备槽各一件护臂的独立层数，客户端 HUD 每件各显示一行。
 */
public record S2CExplorerStacksPayload(int s0, int s1, int s2, int s3, int s4, int s5)
        implements CustomPacketPayload {

    public static final Type<S2CExplorerStacksPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "explorer_stacks"));

    public static final StreamCodec<ByteBuf, S2CExplorerStacksPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s0,
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s1,
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s2,
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s3,
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s4,
            ByteBufCodecs.VAR_INT, S2CExplorerStacksPayload::s5,
            S2CExplorerStacksPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendTo(ServerPlayer player, int[] stacks) {
        PacketDistributor.sendToPlayer(player,
            new S2CExplorerStacksPayload(stacks[0], stacks[1], stacks[2],
                stacks[3], stacks[4], stacks[5]));
    }

    public static void handle(S2CExplorerStacksPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ExplorerVambraceItem.cacheClientStacks(
            new int[]{payload.s0, payload.s1, payload.s2, payload.s3, payload.s4, payload.s5}));
    }
}
