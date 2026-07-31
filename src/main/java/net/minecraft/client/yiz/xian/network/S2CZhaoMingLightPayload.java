package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 紫昭明光 S2C 同步包 — 服务端每 4 tick 发送所有存活紫昭明光的 (id, state, 位置)。
 * 客户端全量对齐：收到的更新，未收到的移除（无增量协议）。
 */
public record S2CZhaoMingLightPayload(List<FxEntry> entries) implements CustomPacketPayload {

    public record FxEntry(int id, java.util.UUID owner, int state, double x, double y, double z) {}

    public static final Type<S2CZhaoMingLightPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "s2c_zhaoming_light"));

    public static final StreamCodec<FriendlyByteBuf, S2CZhaoMingLightPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public S2CZhaoMingLightPayload decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<FxEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new FxEntry(buf.readVarInt(), buf.readUUID(), buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }
            return new S2CZhaoMingLightPayload(list);
        }
        @Override public void encode(FriendlyByteBuf buf, S2CZhaoMingLightPayload p) {
            buf.writeVarInt(p.entries.size());
            for (FxEntry e : p.entries) {
                buf.writeVarInt(e.id());
                buf.writeUUID(e.owner());
                buf.writeVarInt(e.state());
                buf.writeDouble(e.x());
                buf.writeDouble(e.y());
                buf.writeDouble(e.z());
            }
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 服务端权威校准客户端本地模拟（状态/位置/移除贴合实际轨迹）
            net.minecraft.client.yiz.xian.render.ZhaoMingLightClientManager.getInstance()
                    .syncFromServer(entries);
        });
    }
}
