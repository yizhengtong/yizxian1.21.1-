package net.minecraft.client.yiz.xian.api.terraria;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * ItemStack 上的跳跃属性组件读写工具（2026-07-06 DataComponent 化）。
 *
 * <p>属性存在自定义组件 {@link YizxianMod#JUMP_ATTRIBUTES}（{@code Map<EffectTag, Float>}）。
 * 让任意物品（含原版靴子等）都能通过指令注入跳跃属性，穿上即生效。</p>
 *
 * <h3>默认模板</h3>
 * <p>物品<b>声明了任一</b>跳跃属性（组件非空）→ 未声明的 key 用默认值填充：
 * {@code JUMP_COUNT=1, JUMP_HEIGHT=4, FALL_SAFE=4, FALL_REDUCE=2}。
 * 组件不存在（null）→ 返回空 map（区分"无属性"和"声明了部分"）。</p>
 */
public final class JumpAttributes {

    private static final float DEFAULT_JUMP_COUNT  = 1f;
    private static final float DEFAULT_JUMP_HEIGHT = 4f;
    private static final float DEFAULT_FALL_SAFE   = 4f;
    private static final float DEFAULT_FALL_REDUCE = 2f;

    /** 属于"跳跃组"的属性——声明了其中任一个才触发跳跃默认填充。 */
    private static final Set<EffectTag> JUMP_GROUP = Set.of(
        EffectTag.JUMP_COUNT, EffectTag.JUMP_HEIGHT,
        EffectTag.FALL_SAFE, EffectTag.FALL_REDUCE
    );

    private JumpAttributes() {}

    /** 读物品的跳跃属性组件，无组件/空 stack → 空 map（不做默认填充）。 */
    public static Map<EffectTag, Float> get(ItemStack stack) {
        if (stack.isEmpty()) return new EnumMap<>(EffectTag.class);
        Map<EffectTag, Float> v = stack.get(YizxianMod.JUMP_ATTRIBUTES.get());
        return v != null ? new EnumMap<>(v) : new EnumMap<>(EffectTag.class);
    }

    /**
     * 读属性并按默认模板填充：仅当 raw 中<b>已有任一跳跃组属性</b>时，才对缺失的跳跃组 key
     * 用默认值补齐。纯移动属性（MOVE_SPEED/MAX_RUN_SPEED 等）不触发跳跃默认。
     * <p>raw 中<b>所有</b>属性（含非跳跃组）均透传到结果。</p>
     */
    public static Map<EffectTag, Float> getWithDefaults(ItemStack stack) {
        if (stack.isEmpty()) return new EnumMap<>(EffectTag.class);
        Map<EffectTag, Float> raw = stack.get(YizxianMod.JUMP_ATTRIBUTES.get());
        if (raw == null || raw.isEmpty()) return new EnumMap<>(EffectTag.class);
        EnumMap<EffectTag, Float> filled = new EnumMap<>(raw);   // 透传全部原始属性
        // 仅当 raw 含任一跳跃组属性时才补默认
        boolean hasJump = raw.keySet().stream().anyMatch(JUMP_GROUP::contains);
        if (hasJump) {
            filled.putIfAbsent(EffectTag.JUMP_COUNT, DEFAULT_JUMP_COUNT);
            filled.putIfAbsent(EffectTag.JUMP_HEIGHT, DEFAULT_JUMP_HEIGHT);
            filled.putIfAbsent(EffectTag.FALL_SAFE, DEFAULT_FALL_SAFE);
            filled.putIfAbsent(EffectTag.FALL_REDUCE, DEFAULT_FALL_REDUCE);
        }
        return filled;
    }

    /** 写入跳跃属性组件（覆盖）。空 map 等价于 {@link #remove}。 */
    public static void set(ItemStack stack, Map<EffectTag, Float> attrs) {
        if (stack.isEmpty()) return;
        if (attrs == null || attrs.isEmpty()) {
            remove(stack);
        } else {
            stack.set(YizxianMod.JUMP_ATTRIBUTES.get(), new EnumMap<>(attrs));
        }
    }

    /** 设单个属性（其他保留）。 */
    public static void setOne(ItemStack stack, EffectTag tag, float value) {
        Map<EffectTag, Float> cur = get(stack);
        cur.put(tag, value);
        set(stack, cur);
    }

    /** 清除跳跃属性组件。 */
    public static void remove(ItemStack stack) {
        if (stack.isEmpty()) return;
        stack.remove(YizxianMod.JUMP_ATTRIBUTES.get());
    }

    /** 物品是否带跳跃属性组件（无论声明了几个）。 */
    public static boolean hasAny(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Map<EffectTag, Float> v = stack.get(YizxianMod.JUMP_ATTRIBUTES.get());
        return v != null && !v.isEmpty();
    }
}
