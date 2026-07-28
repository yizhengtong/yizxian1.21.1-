package net.minecraft.client.yiz.xian.menu;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 光明指南针容器 Menu。
 *
 * 布局（见 LightCompassScreen）：① 工作槽 3 个 / ② 搜索栏 / ③ 展示栏 9×5 / ④ 快捷栏 9 / ⑤ 滚动条。
 *
 * 关键设计：
 *  - 工作槽是「只进不出」的自定义槽：禁止任何常规取出/交换（左键取、shift-click、数字键 swap、拖拽）。
 *    取出的唯一方式是「左键点击已占用的工作槽」→ 由 Screen 调用 removeFromWorkSlot 实现。
 *  - 工作槽内容绑玩家持久化（PlayerDataAPI: yizxianmod:light_compass_work_slots），关 GUI / 重登 / 跨维度都在。
 *  - 展示栏是虚拟数据源（全部注册物品），不进 Slot 列表，由 Screen 自绘 + 自处理点击。
 */
public class LightCompassMenu extends AbstractContainerMenu {

    public static final int WORK_SLOT_COUNT = 3;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final String DATA_KEY = "yizxianmod:light_compass_work_slots";

    /** 工作槽容器（服务端权威，原版容器同步机制自动同步到客户端）。 */
    private final SimpleContainer workContainer = new SimpleContainer(WORK_SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            // 仅服务端写回持久化；客户端 workContainer 由原版同步获得，写它无意义且会覆盖同步
            if (owner != null && !owner.level().isClientSide) persistWorkSlots();
        }
    };
    private Player owner;

    /** 全部可获取物品列表（展示栏数据源），客户端与服务端共享同一份。 */
    private final List<ItemStack> displayItems = new ArrayList<>();

    public LightCompassMenu(int containerId, Inventory playerInventory) {
        super(YizxianMenus.LIGHT_COMPASS_MENU.get(), containerId);
        this.owner = playerInventory.player;
        buildDisplayItems();
        // 仅服务端从持久化读取填入；客户端工作槽由原版容器同步自动获得
        if (!owner.level().isClientSide) loadWorkSlots();

        // ① 工作槽 3 个：横排 y=22，x=8/26/44
        for (int i = 0; i < WORK_SLOT_COUNT; i++) {
            this.addSlot(new WorkSlot(workContainer, i, 8 + i * 18, 22));
        }
        // ④ 玩家快捷栏 9 个：y=143，x=8 起
        for (int col = 0; col < HOTBAR_SLOT_COUNT; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
        }
    }

    /** 构建展示栏数据源：全部注册物品（创造模式风格）。 */
    private void buildDisplayItems() {
        displayItems.clear();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item != Items.AIR) displayItems.add(new ItemStack(item));
        });
    }

    public List<ItemStack> getDisplayItems() {
        return displayItems;
    }

    /** 把指定展示栏物品送入第一个空工作槽；同一物品已存在则不重复添加。返回是否成功。 */
    public boolean sendToWorkSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // 去重：工作槽已有同物品（仅比物品类型）则不加
        for (int i = 0; i < WORK_SLOT_COUNT; i++) {
            ItemStack existing = workContainer.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItem(existing, stack)) {
                return false;
            }
        }
        for (int i = 0; i < WORK_SLOT_COUNT; i++) {
            if (workContainer.getItem(i).isEmpty()) {
                workContainer.setItem(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    /** 左键移除工作槽 index 的物品（唯一取出方式）。 */
    public void removeFromWorkSlot(int index) {
        if (index < 0 || index >= WORK_SLOT_COUNT) return;
        workContainer.setItem(index, ItemStack.EMPTY);
    }

    public boolean isWorkSlotOccupied(int index) {
        if (index < 0 || index >= WORK_SLOT_COUNT) return false;
        return !workContainer.getItem(index).isEmpty();
    }

    // ── 持久化（存物品注册表 ID 列表，-1 表示空位）──
    // 注意：ItemStack.CODEC 不能编码 EMPTY（count=0 / item=air 会校验失败，set 静默无效），
    // 所以改存每槽的 Item 注册表 ID（-1 表空），保留位置，且能正确序列化。

    /** 从 PlayerDataAPI 读取工作槽内容，按 index 填入容器（含空位，保留位置）。 */
    private void loadWorkSlots() {
        if (owner == null) return;
        try {
            List<Integer> saved = PlayerDataAPI.get(owner, DATA_KEY);
            for (int i = 0; i < WORK_SLOT_COUNT && i < saved.size(); i++) {
                int id = saved.get(i) == null ? -1 : saved.get(i);
                if (id < 0) {
                    workContainer.setItem(i, ItemStack.EMPTY);
                } else {
                    var item = BuiltInRegistries.ITEM.byId(id);
                    workContainer.setItem(i, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
                }
            }
        } catch (Exception ignored) {}
    }

    /** 把工作槽内容写回 PlayerDataAPI（存 Item 注册表 ID，空槽存 -1）。 */
    private void persistWorkSlots() {
        if (owner == null) return;
        try {
            List<Integer> ids = new ArrayList<>(WORK_SLOT_COUNT);
            for (int i = 0; i < WORK_SLOT_COUNT; i++) {
                ItemStack s = workContainer.getItem(i);
                ids.add(s.isEmpty() ? -1 : BuiltInRegistries.ITEM.getId(s.getItem()));
            }
            PlayerDataAPI.set(owner, DATA_KEY, ids);
        } catch (Exception ignored) {}
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 仅服务端写回持久化
        if (!player.level().isClientSide) persistWorkSlots();
    }

    // ── 锁死工作槽：拦截所有可能取出/交换工作槽的操作 ──

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // slotId < WORK_SLOT_COUNT 表示点的是工作槽（0/1/2）
        if (slotId >= 0 && slotId < WORK_SLOT_COUNT) {
            // 工作槽不响应任何原版点击交互（取出由 Screen 的左键逻辑走 removeFromWorkSlot）
            return;
        }
        // 其余（快捷栏 slotId>=3，或 slotId=-999 空白）正常处理
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 禁止任何 shift-click 搬运（工作槽锁死，快捷栏内部同名槽搬运无意义）
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 自定义工作槽：mayPickup 永远 false（原版不会从这里取物品到光标），
     * mayPlace 也 false（防止 shift-click 放入）。工作槽内容只通过 Menu 的
     * sendToWorkSlot / removeFromWorkSlot 改变。
     */
    public static class WorkSlot extends Slot {
        public WorkSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPickup(Player player) { return false; }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
        @Override
        public boolean isActive() { return true; }
    }
}
