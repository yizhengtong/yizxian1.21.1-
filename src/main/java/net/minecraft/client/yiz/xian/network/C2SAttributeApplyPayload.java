package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.item.AttributeScrollItem;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端请求对背包中目标物品应用属性卷轴。
 */
public record C2SAttributeApplyPayload(String attrId, int delta, int slotIndex)
        implements CustomPacketPayload {

    public static final Type<C2SAttributeApplyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "attr_apply"));

    public static final StreamCodec<ByteBuf, C2SAttributeApplyPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SAttributeApplyPayload::attrId,
            ByteBufCodecs.VAR_INT,     C2SAttributeApplyPayload::delta,
            ByteBufCodecs.VAR_INT,     C2SAttributeApplyPayload::slotIndex,
            C2SAttributeApplyPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送 */
    public static void send(String attrId, int delta, int slotIndex) {
        PacketDistributor.sendToServer(new C2SAttributeApplyPayload(attrId, delta, slotIndex));
    }

    /** 服务端接收 */
    public static void handle(C2SAttributeApplyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // 1. 获取目标槽位中的物品
            AbstractContainerMenu menu = player.containerMenu;
            if (payload.slotIndex < 0 || payload.slotIndex >= menu.slots.size()) return;
            Slot slot = menu.getSlot(payload.slotIndex);
            if (!slot.hasItem()) return;
            ItemStack target = slot.getItem();

            // 2. 应用属性 delta
            double current = readAttrValue(target, payload.attrId);
            double newVal = Math.max(0, current + payload.delta);

            // 限制：反击率最大 100，反击数最小 1
            if ("counter_rate".equals(payload.attrId)) {
                newVal = Math.min(100, Math.max(0, newVal));
            }
            if ("counter_count".equals(payload.attrId)) {
                newVal = Math.max(1, newVal);
            }

            writeAttrValue(target, payload.attrId, newVal);
            menu.broadcastChanges();
        });
    }

    /** 已知 NeoForge 属性 ID 集合（不在其中的按 EffectTag 处理） */
    private static final java.util.Set<String> NEOFORGE_ATTRS = java.util.Set.of(
        "crit_rate", "crit_damage", "life_steal",
        "splash_radius", "splash_damage", "splash_falloff",
        "huixin", "kegong",
        "generic_damage", "damage_block",
        "on_hurt",
        "counter_rate", "counter_value", "counter_count",
        "undying",
        "projectile_reflection", "no_collision", "knockback_immunity", "projectile_immunity"
    );

    /** 从物品读取当前属性值 */
    private static double readAttrValue(ItemStack stack, String attrId) {
        if (NEOFORGE_ATTRS.contains(attrId)) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(
                ResourceLocation.fromNamespaceAndPath("yizmodqzk", attrId));
            if (holder.isEmpty()) return 0;
            return ItemAttributeHandler.sumVanillaModifierPublic(stack, holder.get());
        }
        // EffectTag 属性
        try {
            var tag = net.minecraft.client.yiz.xian.api.terraria.EffectTag.valueOf(attrId.toUpperCase());
            var attrs = net.minecraft.client.yiz.xian.api.terraria.JumpAttributes.getWithDefaults(stack);
            return attrs.getOrDefault(tag, 0f);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** 将属性值写入物品 */
    private static void writeAttrValue(ItemStack stack, String attrId, double value) {
        if (NEOFORGE_ATTRS.contains(attrId)) {
            double delta = value - readAttrValue(stack, attrId);
            switch (attrId) {
                case "crit_rate"            -> ItemAttributeHandler.addCritRate(stack, delta);
                case "crit_damage"          -> ItemAttributeHandler.addCritDamage(stack, delta);
                case "life_steal"           -> ItemAttributeHandler.addLifeSteal(stack, delta);
                case "splash_radius"        -> ItemAttributeHandler.addSplashRadius(stack, delta);
                case "splash_damage"        -> ItemAttributeHandler.addSplashDamage(stack, delta);
                case "splash_falloff"       -> ItemAttributeHandler.addSplashFalloff(stack, delta);
                case "generic_damage"       -> ItemAttributeHandler.addGenericDamage(stack, delta);
                case "damage_block"         -> ItemAttributeHandler.addDamageBlock(stack, delta);
                case "on_hurt"              -> ItemAttributeHandler.addOnHurt(stack, delta);
                case "counter_rate"         -> ItemAttributeHandler.addCounterRate(stack, delta);
                case "counter_value"        -> ItemAttributeHandler.addCounterValue(stack, delta);
                case "counter_count"        -> ItemAttributeHandler.addCounterCount(stack, delta);
                case "undying"              -> ItemAttributeHandler.addUndying(stack, delta);
                case "projectile_reflection"-> ItemAttributeHandler.addProjectileReflection(stack, delta);
                case "no_collision"         -> ItemAttributeHandler.addNoCollision(stack, delta);
                case "knockback_immunity"   -> ItemAttributeHandler.addKnockbackImmunity(stack, delta);
                case "projectile_immunity"  -> ItemAttributeHandler.addProjectileImmunity(stack, delta);
                case "huixin"               -> ItemAttributeHandler.addHuixin(stack, delta);
                case "kegong"               -> ItemAttributeHandler.addKegong(stack, delta);
            }
        } else {
            // EffectTag 属性 → 直接用 setOne（与 /yizxian attr set 命令一致）
            try {
                var tag = net.minecraft.client.yiz.xian.api.terraria.EffectTag.valueOf(attrId.toUpperCase());
                net.minecraft.client.yiz.xian.api.terraria.JumpAttributes.setOne(stack, tag, (float) value);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}