package net.minecraft.client.yiz.xian.api.terraria;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * 跳跃属性 Map（{@code Map<EffectTag, Float>}）的 Codec / StreamCodec 工具。
 * <p>供 {@link net.minecraft.core.component.DataComponentType} 注册用：
 * 磁盘持久化用 {@link #CODEC}（{@code Codec.unboundedMap(EffectTag.CODEC, Codec.FLOAT)}），
 * 网络同步用 {@link #STREAM_CODEC}（基于 {@link FriendlyByteBuf#writeMap}）。</p>
 */
public final class JumpAttrCodec {

    /** 磁盘 Codec：{@code Map<EffectTag, Float>} → JSON。 */
    public static final Codec<Map<EffectTag, Float>> CODEC =
        Codec.unboundedMap(EffectTag.CODEC, Codec.FLOAT);

    /** 网络 StreamCodec：写 Map<EffectTag, Float> 到 FriendlyByteBuf。 */
    public static final StreamCodec<FriendlyByteBuf, Map<EffectTag, Float>> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public Map<EffectTag, Float> decode(FriendlyByteBuf buf) {
                int size = buf.readVarInt();
                Map<EffectTag, Float> map = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    EffectTag tag = buf.readEnum(EffectTag.class);
                    float value = buf.readFloat();
                    map.put(tag, value);
                }
                return map;
            }

            @Override
            public void encode(FriendlyByteBuf buf, Map<EffectTag, Float> value) {
                buf.writeVarInt(value.size());
                for (var e : value.entrySet()) {
                    buf.writeEnum(e.getKey());
                    buf.writeFloat(e.getValue());
                }
            }
        };

    private JumpAttrCodec() {}
}
