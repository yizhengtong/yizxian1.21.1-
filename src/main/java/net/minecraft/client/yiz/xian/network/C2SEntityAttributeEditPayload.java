package net.minecraft.client.yiz.xian.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 实体属性编辑工具「应用」请求 —— 修改目标实体的 yizmodqzk 自定义属性值。
 *
 * <p>目标用实体 id（{@code Level.getEntity(int)} 两端公开可用，同维度内一致）。</p>
 *
 * <p>写入策略（与保护范围一致）：
 * <ul>
 *   <li>目标是<b>本模组实体</b>（{@link YizxianMob}）→ {@link EntityAttributeGate#set}（{@code prot_} 前缀 + 调用栈鉴权 + AttributeInstanceMixin 防外部移除）</li>
 *   <li>目标是<b>其他实体</b> → {@link ItemAttributeHandler#setEntityAttribute}（普通 {@code entity_} 前缀，<b>不套保护</b>）</li>
 * </ul>
 */
public record C2SEntityAttributeEditPayload(int targetId, String attrId, double value)
        implements CustomPacketPayload {

    public static final Type<C2SEntityAttributeEditPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizxianmod", "entity_attr_edit"));

    public static final StreamCodec<ByteBuf, C2SEntityAttributeEditPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,     C2SEntityAttributeEditPayload::targetId,
            ByteBufCodecs.STRING_UTF8, C2SEntityAttributeEditPayload::attrId,
            ByteBufCodecs.DOUBLE,      C2SEntityAttributeEditPayload::value,
            C2SEntityAttributeEditPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送：把选中属性的新值提交给服务端。 */
    public static void send(int targetId, String attrId, double value) {
        PacketDistributor.sendToServer(new C2SEntityAttributeEditPayload(targetId, attrId, value));
    }

    /** 服务端接收：校验后按目标类型选择受保护 / 普通写入。 */
    public static void handle(C2SEntityAttributeEditPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            net.minecraft.client.yiz.xian.YizxianMod.LOGGER.info(
                "[AttrEdit] C2S received: targetId={} attr={} value={}", payload.targetId, payload.attrId, payload.value);

            // 1. 目标实体存在性 + 距离校验
            if (!(player.level().getEntity(payload.targetId) instanceof LivingEntity target)) {
                net.minecraft.client.yiz.xian.YizxianMod.LOGGER.warn("[AttrEdit] target not found: id={}", payload.targetId);
                return;
            }
            if (player.distanceToSqr(target) > 64.0 * 64.0) return;

            // 2. 属性存在性（仅 yizmodqzk 自定义属性）
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder =
                BuiltInRegistries.ATTRIBUTE.getHolder(
                    ResourceLocation.fromNamespaceAndPath("yizmodqzk", payload.attrId)).orElse(null);
            if (holder == null) return;

            // 3. 值域防御性 clamp（yizmodqzk 属性均为 RangedAttribute，取自身值域）
            double v = payload.value;
            if (holder.value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged) {
                v = Math.max(ranged.getMinValue(), Math.min(ranged.getMaxValue(), v));
            }

            // 4. 写入：本模组实体受保护，其他实体普通写入不套保护
            if (target instanceof YizxianMob) {
                // 旧实例可能未挂载新注册的属性（createAttributes 变更只影响新生成实体）→ 注入兜底
                if (target.getAttribute(holder) == null) {
                    ensureAttribute(target, holder);
                }
                EntityAttributeGate.set(target, holder, payload.attrId, v);
                net.minecraft.client.yiz.xian.YizxianMod.LOGGER.info(
                    "[AttrEdit] protected write to {} ({}={})", target.getName().getString(), payload.attrId, v);
            } else {
                // 非本模组实体：反射按需注入属性（当次存活期间生效，不套保护），再普通写入
                if (target.getAttribute(holder) == null) {
                    ensureAttribute(target, holder);
                }
                var inst = target.getAttribute(holder);
                if (inst == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "该实体注入属性「" + payload.attrId + "」失败，无法编辑"));
                    net.minecraft.client.yiz.xian.YizxianMod.LOGGER.warn(
                        "[AttrEdit] inject attr {} failed on {}", payload.attrId, target.getName().getString());
                    return;
                }
                ItemAttributeHandler.setEntityAttribute(
                    target, holder, payload.attrId, v, AttributeModifier.Operation.ADD_VALUE);
                net.minecraft.client.yiz.xian.YizxianMod.LOGGER.info(
                    "[AttrEdit] plain write to {} ({}={})", target.getName().getString(), payload.attrId, v);
            }
        });
    }

    /**
     * 反射往目标实体的 {@link AttributeMap} 内部 map 塞一个 yizmodqzk 属性实例（按需注入）。
     *
     * <p>非本模组实体通常未把 yizmodqzk 属性挂进 AttributeSupplier，正常 {@code getAttribute} 返回 null
     * 无法写入。运行时没有标准 API 给已创建实体新增 attribute，故用反射往 {@code attributes} map 注入。
     * 注入的实例是内存态：当次存活期间生效、不持久化、客户端不同步（服务端伤害计算生效）。</p>
     */
    private static void ensureAttribute(LivingEntity target, Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.ai.attributes.AttributeMap.class.getDeclaredField("attributes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Holder<net.minecraft.world.entity.ai.attributes.Attribute>,
                net.minecraft.world.entity.ai.attributes.AttributeInstance> attrs =
                (java.util.Map<Holder<net.minecraft.world.entity.ai.attributes.Attribute>,
                    net.minecraft.world.entity.ai.attributes.AttributeInstance>) f.get(target.getAttributes());
            if (attrs.containsKey(holder)) return;
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                new net.minecraft.world.entity.ai.attributes.AttributeInstance(holder, i -> {});
            attrs.put(holder, inst);
        } catch (Exception e) {
            net.minecraft.client.yiz.xian.YizxianMod.LOGGER.warn("[AttrEdit] 注入属性失败: {}", holder, e);
        }
    }
}
