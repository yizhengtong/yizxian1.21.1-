package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.core.ItemStackSizeOverride;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * 堆叠核心的铁砧强化处理。
 *
 * <p>交互：铁砧<b>左槽</b>放目标物品、<b>右槽</b>放堆叠核心 → 取出后该物品 ID 的最大堆叠数 ×2
 * （封顶 99）。同一物品 ID 最多强化 {@link ItemStackSizeOverride#MAX_ENHANCE} 次。</p>
 *
 * <h3>两个事件</h3>
 * <ul>
 *   <li>{@link #onAnvilUpdate} — 在 {@code AnvilMenu#createResult()} 触发，计算输出槽<b>预览</b>。
 *       右槽是堆叠核心时，输出 = 左槽副本（身份/NBT 不变），消耗 1 个堆叠核心、不耗经验。
 *       <b>不在此改堆叠表</b>（玩家可能取消）。</li>
 *   <li>{@link #onAnvilRepair} — 玩家取出物品时触发，此时才真正写入堆叠表 + 累加强化次数。</li>
 * </ul>
 *
 * <p>强化规则（任意堆叠数 ×2 递增）：
 * 取该物品 ID 当前有效堆叠数（{@link ItemStackSizeOverride#getOverride}，未覆盖则用原版默认）×2，
 * 封顶 99。次数达 {@value ItemStackSizeOverride#MAX_ENHANCE} 或当前已 =99 则拒绝。</p>
 */
public final class StackCoreAnvilHandler {

    private StackCoreAnvilHandler() {}

    /**
     * 判断右槽是否为堆叠核心。
     */
    private static boolean isStackCore(ItemStack right) {
        return !right.isEmpty() && right.getItem() == YizxianMod.STACK_CORE.get();
    }

    /**
     * 取某物品 ID 的当前有效最大堆叠数（覆盖表优先，否则原版默认）。
     */
    private static int currentMax(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        int override = ItemStackSizeOverride.getOverride(id);
        return override > 0 ? override : item.getDefaultMaxStackSize();
    }

    /**
     * 计算强化后的新堆叠数：当前 ×2，封顶 99。
     */
    private static int enhancedMax(int current) {
        return Math.min(ItemStackSizeOverride.MAX, current * 2);
    }

    // ══════════════════════════════════════════════════════════
    //  预览：计算输出槽
    // ══════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!isStackCore(right) || left.isEmpty()) return;

        Item target = left.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(target);

        // 次数已达上限 → 不设输出（输出空，玩家看到无法强化）
        if (!ItemStackSizeOverride.canEnhance(id)) return;
        // 当前已到顶（99）→ 再强化无变化，拒绝
        if (currentMax(target) >= ItemStackSizeOverride.MAX) return;

        // 输出 = 左槽副本（保留 NBT/组件，身份不变）；强化效果体现在取出后该 ID 能堆更高
        ItemStack output = left.copy();
        event.setOutput(output);
        event.setMaterialCost(1); // 消耗 1 个堆叠核心
        // 注意：铁砧 mayPickup 要求 cost > 0 才允许取出（cost=0 视为无效操作），
        // 故必须消耗至少 1 级经验，无法做到真正"零消耗"。
        event.setCost(1);
    }

    // ══════════════════════════════════════════════════════════
    //  生效：玩家取出物品时真正写入
    // ══════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!isStackCore(right) || left.isEmpty()) return;

        Item target = left.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(target);
        // 双重保险：取出时再校验一次（预览可能因状态变化已失效）
        if (!ItemStackSizeOverride.canEnhance(id)) return;
        int current = currentMax(target);
        if (current >= ItemStackSizeOverride.MAX) return;

        int newSize = enhancedMax(current);
        ItemStackSizeOverride.set(id, newSize);
        ItemStackSizeOverride.incrementEnhanceCount(id);
    }
}
