package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 连招动画索引同步包 (S2C)。
 *
 * <p>服务端 {@link ComboStateMachine#onAttack} 算出新动画索引后，仅发给攻击者本人，
 * 客户端缓存供第一人称渲染每帧读取（{@code TerraBladeFirstPersonMixin}）。</p>
 *
 * <p>取代旧的「combo_step/combo_tick 走 PlayerDataAPI 每 tick 全量同步」方案：
 * 攻击是事件，按事件下发索引，无需每 tick 把整个玩家数据 root 同步到客户端。</p>
 */
public record S2CComboAnimPayload(int animIdx) implements CustomPacketPayload {

    public static final Type<S2CComboAnimPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "combo_anim"));

    public static final StreamCodec<ByteBuf, S2CComboAnimPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, S2CComboAnimPayload::animIdx,
            S2CComboAnimPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 服务端 → 指定玩家：推送最新动画索引。 */
    public static void sendTo(net.minecraft.server.level.ServerPlayer player, int animIdx) {
        PacketDistributor.sendToPlayer(player, new S2CComboAnimPayload(animIdx));
    }

    /** 客户端接收：写入 ComboStateMachine 的客户端缓存。 */
    public static void handle(S2CComboAnimPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ComboStateMachine.setClientAnimIndex(payload.animIdx));
    }
}
