package net.minecraft.client.yiz.xian.api;

import com.mojang.serialization.Codec;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.client.yiz.xian.network.SyncAccessoryPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 饰品槽容器 — 可变大小的 {@link SimpleContainer}，单例化（按玩家 UUID + 逻辑侧缓存）。
 *
 * <h3>单一权威源（SSOT）架构</h3>
 * <ul>
 *   <li><b>服务端实例（{@code _s}）</b>是唯一可写权威源：装入/取出饰品、能力判定
 *       （飞行维持、悬停、免伤、突进恢复）全部在服务端实例上做。</li>
 *   <li><b>客户端实例（{@code _c}）</b>是只读镜像：<b>唯一</b>数据入口是
 *       {@link #applyServerSnapshot(String)}（经 {@link SyncAccessoryPayload} 从服务端推送），
 *       以及原版 Menu slot 同步（源头同是 {@code _s}，不冲突）。客户端<b>从不</b>读附件。</li>
 *   <li>附件（{@link PlayerDataAPI}）仅作 {@code _s} 的持久化载体：登录时读一次、变更时写。</li>
 * </ul>
 *
 * <h3>可变数量</h3>
 * <p>默认 9 槽。可通过 {@link #setSlotCount(int)} 动态增减。
 * 减少时尾部槽位的物品会掉落给玩家；增加时新槽位为空。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>登录/重生：服务端 discard+get → {@link #loadFromPersist()} 从附件加载 → 发 {@link SyncAccessoryPayload} 初始化客户端</li>
 *   <li>运行时：物品放入/取出 → {@link #setChanged()} → 服务端写附件 + 发包推客户端</li>
 *   <li>退出/断开：{@link #discard(Player)} 清理单例缓存（两端各自清理）</li>
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
        // 仅服务端从附件加载初始内容；客户端构造为空，等 applyServerSnapshot 填充
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

    /** 只读访问：容器尚未创建时返回 null（避免过早创建空容器并缓存）。 */
    public static AccessoryContainer getIfExists(Player player) {
        return INSTANCES.get(cacheKey(player));
    }

    /** 玩家退出/切换世界/重生时清理单例，避免内存泄漏与脏数据。 */
    public static void discard(Player player) {
        INSTANCES.remove(cacheKey(player));
    }

    // ─── 统一能力查询（取代各处重复的"遍历找心之翅"） ──────

    /**
     * 玩家饰品槽是否装备了心之翅或原版鞘翅。
     * <p>用 {@link #getIfExists}（不创建空实例），容器不存在时返回 false。
     * 供飞行/突进/免伤/HUD 等所有能力判定统一调用。</p>
     */
    public static boolean hasHeartWings(Player player) {
        return findElytra(player) != ItemStack.EMPTY;
    }

    /**
     * 找到饰品槽里的心之翅或原版鞘翅 ItemStack（找不到返回 {@link ItemStack#EMPTY}）。
     * <p>用 {@link #getIfExists}，不创建空实例。供渲染层返回真实物品栈用。</p>
     */
    public static ItemStack findElytra(Player player) {
        AccessoryContainer c = getIfExists(player);
        if (c == null) return ItemStack.EMPTY;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(Items.ELYTRA) || s.getItem() instanceof HeartWingsItem) return s;
        }
        return ItemStack.EMPTY;
    }

    /** 通用查询：饰品槽是否存在满足谓词的物品。 */
    public static boolean hasItem(Player player, Predicate<ItemStack> test) {
        AccessoryContainer c = getIfExists(player);
        if (c == null) return false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (test.test(c.getItem(i))) return true;
        }
        return false;
    }

    // ─── 客户端：唯一数据入口 ───────────────────────────────

    /**
     * 客户端：用服务端推送的 SNBT 快照整体刷新本容器（只读镜像）。
     * <p>这是客户端 {@code _c} 的<b>唯一</b>数据来源（配合原版 Menu slot 同步）。
     * loading 标志屏蔽期间的回写，绝不触发 saveToPersist/发包。</p>
     */
    public void applyServerSnapshot(String snbt) {
        if (!clientSide) return;   // 仅客户端
        loading = true;
        try {
            if (snbt == null || snbt.isEmpty()) return;
            CompoundTag root = TagParser.parseTag(snbt);
            int count = root.getInt("Count");
            if (count != getContainerSize()) {
                resizeInternal(count);
            }
            ListTag list = root.getList("Slots", Tag.TAG_COMPOUND);
            int n = Math.min(getContainerSize(), list.size());
            for (int i = 0; i < n; i++) {
                CompoundTag ct = list.getCompound(i);
                if (!ct.isEmpty()) {
                    ItemStack parsed = ItemStack.parse(player.registryAccess(), ct).orElse(ItemStack.EMPTY);
                    setItem(i, parsed);
                } else {
                    setItem(i, ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            YizxianMod.LOGGER.warn("Failed to apply accessory snapshot for {}",
                player.getName().getString(), e);
        } finally {
            loading = false;
        }
    }

    // ─── 服务端：持久化加载/回写 ───────────────────────────

    /** 从 PlayerDataAPI 加载槽位内容。仅服务端构造时调用。 */
    private void loadFromPersist() {
        loading = true;
        try {
            int count = getPersistedSlotCount();
            String raw = PlayerDataAPI.get(player, DATA_KEY);
            if (count != getContainerSize()) {
                resizeInternal(count);
            }
            if (raw != null && !raw.isEmpty()) {
                CompoundTag root = TagParser.parseTag(raw);
                ListTag list = root.getList("Slots", Tag.TAG_COMPOUND);
                int n = Math.min(getContainerSize(), list.size());
                for (int i = 0; i < n; i++) {
                    CompoundTag ct = list.getCompound(i);
                    if (!ct.isEmpty()) {
                        ItemStack parsed = ItemStack.parse(player.registryAccess(), ct).orElse(ItemStack.EMPTY);
                        setItem(i, parsed);
                    } else {
                        setItem(i, ItemStack.EMPTY);
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

    /**
     * 服务端：从附件重新加载内容（登录/重生时调用）。
     * <p><b>关键</b>：复用既有实例（{@code get}），不 discard 换新实例 ——
     * 玩家的 InventoryMenu slot 已绑定到既有实例，discard+换新会导致 Menu slot 指向旧实例、
     * INSTANCES 指向新实例的"双实例"脱节（表现为取出后还能飞 / 闪烁）。</p>
     */
    public void reloadFromPersist() {
        if (clientSide) return;
        loadFromPersist();
    }

    /** 序列化当前槽位为 SNBT（服务端权威快照）。供持久化与推送客户端共用。 */
    public String getSnapshotSnbt() {
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
        return root.toString();
    }

    /** 将当前槽位内容写回 PlayerDataAPI（持久化）。仅服务端。 */
    private void saveToPersist() {
        if (loading || clientSide) return;
        try {
            PlayerDataAPI.set(player, DATA_KEY, getSnapshotSnbt());
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
        } finally {
            loading = false;
        }
        // 触发持久化 accessory_items + 推客户端（loading 已恢复 false）
        setChanged();
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
        // SimpleContainer 的 items 字段是 private，通过反射替换。
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
            throw new RuntimeException("AccessoryContainer resize failed", e);
        }
    }

    // ─── SimpleContainer 覆写：SSOT 写入 + 推客户端 ─────────

    /**
     * 饰品内容变更的统一入口（slot setItem / clear / add 触发）。
     * <p>服务端：持久化到附件 + 发 {@link SyncAccessoryPayload} 推客户端。
     * 客户端：early-return（只读镜像，绝不持久化或发包）。</p>
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (clientSide || loading) return;
        saveToPersist();
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SyncAccessoryPayload(getSnapshotSnbt()));
        }
    }

    // ─── PlayerDataAPI 注册 ──────────────────────────────────

    /** 在模组构造时调用一次，注册数据键。 */
    public static void registerDataKeys() {
        PlayerDataAPI.register(COUNT_KEY, Codec.INT, DEFAULT_SLOT_COUNT);
        PlayerDataAPI.register(DATA_KEY, Codec.STRING, "");
    }
}
