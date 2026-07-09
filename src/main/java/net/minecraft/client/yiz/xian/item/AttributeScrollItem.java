package net.minecraft.client.yiz.xian.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性卷轴 — NBT 驱动的属性加减物品。
 *
 * <p>右键点击背包/容器中的目标物品，根据 NBT 中的
 * {@code yizmodqzk:attr_id} 和 {@code yizmodqzk:attr_delta}
 * 为目标物品增加/减少对应自定义属性值。</p>
 *
 * <p>每个自定义属性有两种卷轴：+1（delta=1）和 -1（delta=-1）。
 * 在创造标签页中按属性分组展示。</p>
 */
public class AttributeScrollItem extends Item {

    static final String ATTR_ID_KEY = "yizmodqzk:attr_id";
    static final String ATTR_DELTA_KEY = "yizmodqzk:attr_delta";

    /** 所有属性卷轴的注册信息（属性 ID → 中文名） */
    /** 仅包含已有消费代码的自定义属性。无消费代码的不在此列。 */
    public static final Map<String, String> ATTRIBUTES = new LinkedHashMap<>();
    static {
        // NeoForge — 有消费代码（19 个，不含 on_attack/on_tick）
        ATTRIBUTES.put("crit_rate", "暴击率");
        ATTRIBUTES.put("crit_damage", "暴伤");
        ATTRIBUTES.put("life_steal", "吸血");
        ATTRIBUTES.put("splash_radius", "溅射半径");
        ATTRIBUTES.put("splash_damage", "溅射伤害");
        ATTRIBUTES.put("splash_falloff", "溅射衰减");
        ATTRIBUTES.put("huixin", "会心");
        ATTRIBUTES.put("kegong", "渴攻");
        ATTRIBUTES.put("generic_damage", "全伤害");
        ATTRIBUTES.put("damage_block", "格挡");
        ATTRIBUTES.put("on_hurt", "受击");
        ATTRIBUTES.put("counter_rate", "反击率");
        ATTRIBUTES.put("counter_value", "反击值");
        ATTRIBUTES.put("counter_count", "反击数");
        ATTRIBUTES.put("undying", "复活");
        ATTRIBUTES.put("projectile_reflection", "投射物反弹");
        ATTRIBUTES.put("no_collision", "穿过实体");
        ATTRIBUTES.put("knockback_immunity", "击退免疫");
        ATTRIBUTES.put("projectile_immunity", "投射物免疫");

        // 饰品 — 跳跃/移动（8 个，全部有消费）
        ATTRIBUTES.put("jump_count", "跳跃次数");
        ATTRIBUTES.put("jump_height", "跳跃高度");
        ATTRIBUTES.put("fall_safe", "安全距离");
        ATTRIBUTES.put("fall_reduce", "摔伤减免");
        ATTRIBUTES.put("move_speed", "移动速度");
        ATTRIBUTES.put("max_run_speed", "最大奔跑速度");
        ATTRIBUTES.put("jump_strength", "跳跃力度");
        ATTRIBUTES.put("air_speed", "空中移速");

        // 饰品 — 防御（4 个，有消费）
        ATTRIBUTES.put("armor", "防御力");
        ATTRIBUTES.put("damage_reduction", "减伤率");
        ATTRIBUTES.put("dodge_chance", "闪避率");
        ATTRIBUTES.put("invincibility_mult", "无敌帧");

        // 饰品 — 回复（2 个，有消费）
        ATTRIBUTES.put("life_regen_rate", "生命再生");
        ATTRIBUTES.put("life_regen_pct", "生命再生%");
    }

    public AttributeScrollItem(Properties properties) {
        super(properties);
    }

    /** 创建一个指定属性的 +1 卷轴 ItemStack */
    public static ItemStack createPlus(String attrId) {
        return create(attrId, 1);
    }

    /** 创建一个指定属性的 -1 卷轴 ItemStack */
    public static ItemStack createMinus(String attrId) {
        return create(attrId, -1);
    }

    private static ItemStack create(String attrId, int delta) {
        ItemStack stack = new ItemStack(
            net.minecraft.client.yiz.xian.YizxianMod.ATTRIBUTE_SCROLL_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(ATTR_ID_KEY, attrId);
        tag.putInt(ATTR_DELTA_KEY, delta);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // 仿附魔书：物品名固定"属性卷轴"，效果名和值显示在下方（via LORE）
        String name = ATTRIBUTES.getOrDefault(attrId, attrId);
        String prefix = delta > 0 ? "§9+" : "§c";
        String suffix = delta > 0 ? "" : "";
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
            List.of(Component.literal(prefix + Math.abs(delta) + " " + name))));

        return stack;
    }

    /** 读取卷轴中的属性 ID */
    public static String getAttrId(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag tag = cd.copyTag();
        return tag.contains(ATTR_ID_KEY) ? tag.getString(ATTR_ID_KEY) : null;
    }

    /** 读取卷轴中的 delta 值 */
    public static int getDelta(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        CompoundTag tag = cd.copyTag();
        return tag.getInt(ATTR_DELTA_KEY);
    }

    /** 判断是否为属性卷轴 */
    public static boolean isScroll(ItemStack stack) {
        return stack.getItem() instanceof AttributeScrollItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                 List<Component> tooltip, TooltipFlag flag) {
        String attrId = getAttrId(stack);
        int delta = getDelta(stack);
        if (attrId != null && delta != 0) {
            tooltip.add(Component.literal(
                "§7右键目标物品以" + (delta > 0 ? "§a增加" : "§c减少") + "§7属性"));
        }
    }
}