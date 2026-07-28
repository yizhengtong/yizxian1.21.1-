package net.minecraft.client.yiz.xian.render;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/**
 * 光明指南针工作槽的「高亮目标」解析。
 *
 * 放进工作槽的物品会被解析成三类高亮之一：
 *   BLOCK      — 方块物品（BlockItem）→ 高亮世界中该方块的轮廓
 *   ENTITY     — 刷怪蛋（SpawnEggItem）→ 高亮世界中该 EntityType 的实体 AABB
 *   ITEM_DROP  — 其余物品 → 高亮世界中包装了该物品的掉落物（ItemEntity）
 *
 * 颜色按工作槽 index 固定：0=黄、1=白、2=橙。
 */
public final class CompassHighlightTarget {

    public enum Kind { BLOCK, ENTITY, ITEM_DROP }

    private final Kind kind;
    /** BLOCK：对应方块；ENTITY：null（用 entityType）；ITEM_DROP：对应物品 */
    private final Block block;
    private final EntityType<?> entityType;
    private final Item item;
    private final float r, g, b; // 高亮颜色

    private CompassHighlightTarget(Kind kind, Block block, EntityType<?> type, Item item, float r, float g, float b) {
        this.kind = kind; this.block = block; this.entityType = type; this.item = item;
        this.r = r; this.g = g; this.b = b;
    }

    public Kind kind() { return kind; }
    public Block block() { return block; }
    public EntityType<?> entityType() { return entityType; }
    public Item item() { return item; }
    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }

    /** 从工作槽 ItemStack 解析出高亮目标；空槽返回 null。slotIndex 决定颜色。 */
    @Nullable
    public static CompassHighlightTarget from(ItemStack stack, int slotIndex) {
        if (stack == null || stack.isEmpty()) return null;
        Item it = stack.getItem();
        float[] rgb = colorFor(slotIndex);

        if (it instanceof BlockItem bi) {
            return new CompassHighlightTarget(Kind.BLOCK, bi.getBlock(), null, it, rgb[0], rgb[1], rgb[2]);
        }
        if (it instanceof SpawnEggItem egg) {
            EntityType<?> type = egg.getType(stack);
            return new CompassHighlightTarget(Kind.ENTITY, null, type, it, rgb[0], rgb[1], rgb[2]);
        }
        // 其余：掉落物匹配
        return new CompassHighlightTarget(Kind.ITEM_DROP, null, null, it, rgb[0], rgb[1], rgb[2]);
    }

    /** 工作槽 0/1/2 → 黄/白/橙。 */
    private static float[] colorFor(int slotIndex) {
        return switch (slotIndex) {
            case 0  -> new float[]{1.0f, 1.0f, 0.2f};  // 黄
            case 1  -> new float[]{1.0f, 1.0f, 1.0f};  // 白
            case 2  -> new float[]{1.0f, 0.6f, 0.1f};  // 橙
            default -> new float[]{0.8f, 0.8f, 0.8f};
        };
    }
}
