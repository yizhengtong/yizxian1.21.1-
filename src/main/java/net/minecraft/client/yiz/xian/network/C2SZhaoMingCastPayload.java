package net.minecraft.client.yiz.xian.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 紫昭明光施法请求 C2S — 客户端按住右键连发时每 N tick 发送一次，
 * 服务端权威施法（消耗法力/耐久 + 创建 FX）。
 */
public record C2SZhaoMingCastPayload() implements CustomPacketPayload {

    public static final Type<C2SZhaoMingCastPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("yizxianmod", "c2s_zhaoming_cast"));

    public static final StreamCodec<FriendlyByteBuf, C2SZhaoMingCastPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SZhaoMingCastPayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isServerbound() && context.player() instanceof ServerPlayer sp) {
                // 法力不足 → 拒绝施法
                if (!net.minecraft.client.yiz.tool.health.ManaTracker.consume(sp,
                        net.minecraft.client.yiz.xian.item.WupinItem.MANA_COST)) {
                    sp.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c法力不足"), true);
                    return;
                }
                // 耐久
                var stack = sp.getMainHandItem();
                if (stack.isDamageableItem()) {
                    stack.hurtAndBreak(
                            net.minecraft.client.yiz.xian.item.WupinItem.DURABILITY_COST, sp,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                }
                // 施法（服务端权威：伤害/命中/返还）
                net.minecraft.client.yiz.xian.fx.ZhaoMingLightManager.getInstance()
                        .add(sp, sp.getLookAngle());
            }
        });
    }
}
