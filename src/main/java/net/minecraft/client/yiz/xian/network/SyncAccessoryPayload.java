package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 饰品容器同步包 (S2C)。
 *
 * <p>服务端 → 客户端：把服务端权威容器（{@code _s}）的全量 SNBT 快照推给客户端，
 * 客户端用它整体替换自己的只读镜像容器（{@code _c}）。</p>
 *
 * <p>这是客户端 {@code _c} 的<b>唯一数据入口</b>（配合原版 Menu slot 同步），
 * 取代了旧的 {@code refreshFromSync}（从附件读）。源头始终是服务端 {@code _s}，
 * 不存在多副本互相覆盖的竞态。</p>
 *
 * <p>发送时机：① 玩家登录/重生初始化；② 每次 {@link AccessoryContainer#setChanged()}
 * （即饰品内容变更）。参照 yiz1.21.1 的 {@code SyncRealmPayload} 写法。</p>
 */
public record SyncAccessoryPayload(
    String containerSnbt
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncAccessoryPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "sync_accessory"));

    public static final StreamCodec<FriendlyByteBuf, SyncAccessoryPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncAccessoryPayload decode(FriendlyByteBuf buf) {
            return new SyncAccessoryPayload(buf.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncAccessoryPayload payload) {
            buf.writeUtf(payload.containerSnbt);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：用服务端快照整体刷新本地只读镜像容器。 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            // get 会按需创建客户端 _c 单例；applyServerSnapshot 是它唯一的数据来源
            AccessoryContainer.get(player).applyServerSnapshot(containerSnbt);
        });
    }
}
