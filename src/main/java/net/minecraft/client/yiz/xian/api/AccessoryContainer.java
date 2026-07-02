package net.minecraft.client.yiz.xian.api;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 饰品槽容器 — 可变大小的 {@link SimpleContainer}，单例化（按玩家 UUID 缓存）。
 *
 * <h3>持久化</h3>
 * <p>服务器端 {@link #setChanged()} 时将全部槽位内容序列化为 SNBT 字符串，
 * 通过 {@link PlayerDataAPI} 写入玩家数据（自动持久化 + copyOnDeath + 客户端同步）。
 * 客户端容器通过原版 Container 同步协议获取内容，不主动写回持久化。</p>
 *
 * <h3>可变数量</h3>
 * <p>默认 9 槽。可通过 {@link #setSlotCount(int)} 动态增减。
 * 减少时尾部槽位的物品会掉落给玩家；增加时新槽位为空。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>登录时：服务器构造 → {@link #loadFromPersist()} 加载初始内容</li>
 *   <li>运行时：物品放入/取出 → {@link #setChanged()} → 服务器端回写持久化</li>
 *   <li>退出时：{@link #discard(Player)} 清理单例缓存</li>
 * </ul>
 */
public class AccessoryContainer extends SimpleContainer {

    /** 默认槽位数量 */
    public static final int DEFAULT_SLOT_COUNT = 9;

    /** PlayerDataAPI 数据键 — 槽位数量。 */
    public static final String COUNT_KEY = "yizxianmod:accessory_count";
    /** PlayerDataAPI 数据键 — 槽位物品（SNBT）。 */
    public static final String DATA_KEY  = "yizxianmod:accessory_items";

    /**
     * 按玩家 UUID + 逻辑侧缓存的容器单例。
     * <p><b>关键</b>：不能用纯 UUID 做 key。集成服务器中 ServerPlayer 和 LocalPlayer
     * 共享同一 UUID，但运行在同一 JVM。若 key 只按 UUID，客户端会拿到服务端的容器实例，
     * 导致 {@code clientSide=false} 却从客户端线程写入 PlayerDataAPI，产生幽灵物品Bug。</p>
     */
    private static final Map<String, AccessoryContainer> INSTANCES = new ConcurrentHashMap<>();

    private final Player player;
    private final boolean clientSide;
    /** 装载/重设期间不触发 setChanged → 持久化回写，防止环形写入。 */
    private boolean loading;

    private AccessoryContainer(Player player) {
        super(DEFAULT_SLOT_COUNT);
        this.player = player;
        this.clientSide = player.level().isClientSide;
        if (!clientSide) {
            loadFromPersist();
        }
    }

    // ─── 单例管理 ────────────────────────────────────────────

    /** 生成区分客户端/服务端的缓存 key。避免集成服务器中两边拿到同一实例。 */
    private static String cacheKey(Player player) {
        return player.getUUID() + (player.level().isClientSide ? "_c" : "_s");
    }

    /** 获取玩家对应的容器单例。客户端与服务器各自维护。 */
    public static AccessoryContainer get(Player player) {
        return INSTANCES.computeIfAbsent(cacheKey(player), k -> new AccessoryContainer(player));
    }

    /** 玩家退出/切换世界时清理单例，避免内存泄漏。 */
    public static void discard(Player player) {
        INSTANCES.remove(cacheKey(player));
    }

    // ─── 持久化加载/回写 ─────────────────────────────────────

    /** 从 PlayerDataAPI 加载槽位内容。服务器端调用。 */
    private void loadFromPersist() {
        loading = true;
        try {
            // 1) 加载槽位数量
            int count = getPersistedSlotCount();
            if (count != getContainerSize()) {
                resizeInternal(count);
            }
            // 2) 加载物品
            String raw = PlayerDataAPI.get(player, DATA_KEY);
            if (raw != null && !raw.isEmpty()) {
                CompoundTag root = TagParser.parseTag(raw);
                int persistedCount = root.getInt("Count");
                ListTag list = root.getList("Slots", Tag.TAG_COMPOUND);
                int n = Math.min(getContainerSize(), list.size());
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    CompoundTag ct = list.getCompound(i);
                    if (!ct.isEmpty()) {
                        ItemStack.parse(player.registryAccess(), ct)
                            .ifPresentOrElse(s -> setItem(idx, s), () -> setItem(idx, ItemStack.EMPTY));
                    } else {
                        setItem(idx, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception e) {
            YizxianMod.LOGGER.warn("Failed to load accessory container data for {}",
                player.getName().getString(), e);
        } finally {
            loading = false;
        }
    }

    /** 将当前槽位内容写回 PlayerDataAPI。服务器端调用。 */
    private void saveToPersist() {
        if (loading || clientSide) return;
        try {
            CompoundTag root = new CompoundTag();
            root.putInt("Count", getContainerSize());
            ListTag list = new ListTag();
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack stack = getItem(i);
                // ItemStack.save() 空栈抛异常，空栈只存空 CompoundTag
                if (stack.isEmpty()) {
                    list.add(new CompoundTag());
                } else {
                    list.add(stack.save(player.registryAccess(), new CompoundTag()));
                }
            }
            root.put("Slots", list);
            PlayerDataAPI.set(player, DATA_KEY, root.toString());
        } catch (Exception e) {
            YizxianMod.LOGGER.warn("Failed to save accessory container data for {}",
                player.getName().getString(), e);
        }
    }

    /** 从 PlayerDataAPI 读取持久化的槽位数量。 */
    private int getPersistedSlotCount() {
        Integer v = PlayerDataAPI.get(player, COUNT_KEY);
        return (v != null && v > 0) ? v : DEFAULT_SLOT_COUNT;
    }

    /**
     * 客户端：PlayerDataAPI 同步到达后刷新本容器内容。
     * <p>用于处理容器外部的数据变更（命令 / 其他模组 / 未来自动装备逻辑）。
     * 与原版 slot 同步共存：二者最终收敛到服务器权威值。</p>
     */
    public void refreshFromSync() {
        if (!clientSide) return;
        loading = true;
        try {
            loadFromPersist();
        } finally {
            loading = false;
        }
    }

    /** 将当前槽位数量写回 PlayerDataAPI。 */
    private void saveSlotCount() {
        if (clientSide) return;
        PlayerDataAPI.set(player, COUNT_KEY, getContainerSize());
    }

    // ─── 可变大小 ────────────────────────────────────────────

    /**
     * 动态更改槽位数量。
     * <ul>
     *   <li>增大：新槽位为空</li>
     *   <li>缩小：尾部移除的槽位中有物品时，直接掉落给玩家</li>
     * </ul>
     */
    public void setSlotCount(int newCount) {
        if (newCount <= 0) newCount = 1;
        int oldCount = getContainerSize();
        if (newCount == oldCount) return;

        loading = true;
        try {
            // 缩小：掉落尾部多余的物品
            if (newCount < oldCount) {
                for (int i = newCount; i < oldCount; i++) {
                    ItemStack stack = getItem(i);
                    if (!stack.isEmpty()) {
                        if (!clientSide) {
                            player.getInventory().placeItemBackInInventory(stack);
                        }
                        setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            resizeInternal(newCount);
            saveSlotCount();
            saveToPersist();
        } finally {
            loading = false;
        }
    }

    /** 获取当前槽位数量。 */
    public int getSlotCount() {
        return getContainerSize();
    }

    /** 内部 resize：更换底层 ItemStack 数组。 */
    private void resizeInternal(int newSize) {
        List<ItemStack> old = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            old.add(getItem(i).copy());
        }
        // SimpleContainer 的 items 字段是 private，无法直接访问。
        // 通过 clearContent + 重建 via addItem 的方式代替。
        // 最简做法：直接用反射修改父类的 items 字段，或构造新实例替换结构。
        // 这里采用反射方式，SimpleContainer 内部就是 NonNullList<ItemStack>。
        try {
            java.lang.reflect.Field field = SimpleContainer.class.getDeclaredField("items");
            field.setAccessible(true);
            net.minecraft.core.NonNullList<ItemStack> newItems =
                net.minecraft.core.NonNullList.withSize(newSize, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(old.size(), newSize); i++) {
                newItems.set(i, old.get(i));
            }
            field.set(this, newItems);
        } catch (Exception e) {
            YizxianMod.LOGGER.error("Failed to resize AccessoryContainer via reflection", e);
            // fallback: 重建容器（交换单例缓存中的实例）
            throw new RuntimeException("AccessoryContainer resize failed", e);
        }
    }

    // ─── SimpleContainer 覆写 ────────────────────────────────

    @Override
    public void setChanged() {
        super.setChanged();
        saveToPersist();
    }

    // ─── PlayerDataAPI 注册 ──────────────────────────────────

    /** 在模组构造时调用一次，注册数据键。 */
    public static void registerDataKeys() {
        PlayerDataAPI.register(COUNT_KEY, Codec.INT, DEFAULT_SLOT_COUNT);
        PlayerDataAPI.register(DATA_KEY, Codec.STRING, "");
    }
}
