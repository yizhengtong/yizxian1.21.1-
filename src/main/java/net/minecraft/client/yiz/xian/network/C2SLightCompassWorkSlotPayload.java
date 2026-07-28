package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.menu.LightCompassMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 光明指南针工作槽操作。
 *
 * 工作槽是「只进不出」的自定义槽，客户端无法通过原版 slot 点击修改它（mayPickup/mayPlace=false +
 * Menu.clicked 拦截）。展示栏点击放入 / 左键移除都走本包，由服务端在 player.containerMenu 上改 workContainer，
 * 再由原版容器同步回客户端 + 触发持久化（workContainer.setChanged → persistWorkSlots）。
 */
public record C2SLightCompassWorkSlotPayload(int action, ItemStack stack, int slotIndex)
        implements CustomPacketPayload {

    public static final int ACTION_ADD = 0;    // 放入：stack 进第一个空工作槽
    public static final int ACTION_REMOVE = 1; // 移除：清空指定 slotIndex 工作槽

    public static final Type<C2SLightCompassWorkSlotPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "light_compass_work"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SLightCompassWorkSlotPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,       C2SLightCompassWorkSlotPayload::action,
            ItemStack.OPTIONAL_STREAM_CODEC, C2SLightCompassWorkSlotPayload::stack,
            ByteBufCodecs.VAR_INT,       C2SLightCompassWorkSlotPayload::slotIndex,
            C2SLightCompassWorkSlotPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送：放入物品 */
    public static void sendAdd(ItemStack stack) {
        PacketDistributor.sendToServer(new C2SLightCompassWorkSlotPayload(ACTION_ADD, stack, -1));
    }

    /** 客户端发送：移除指定工作槽 */
    public static void sendRemove(int slotIndex) {
        PacketDistributor.sendToServer(new C2SLightCompassWorkSlotPayload(ACTION_REMOVE, ItemStack.EMPTY, slotIndex));
    }

    /** 服务端接收 */
    public static void handle(C2SLightCompassWorkSlotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof LightCompassMenu menu)) return;

            if (payload.action() == ACTION_ADD) {
                menu.sendToWorkSlot(payload.stack());
            } else if (payload.action() == ACTION_REMOVE) {
                menu.removeFromWorkSlot(payload.slotIndex());
            }
            menu.broadcastChanges();
        });
    }
}
