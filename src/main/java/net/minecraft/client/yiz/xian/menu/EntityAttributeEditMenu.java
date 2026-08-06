package net.minecraft.client.yiz.xian.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 实体属性编辑容器 Menu（原版容器风格）。
 *
 * <p>服务端打开时把目标实体 id 经 {@code IContainerFactory} 的额外数据传给客户端，
 * 客户端 Menu 只存实体 id（供 Screen 用 {@code Level.getEntity(id)} 读取目标实体当前属性值）；
 * 实际写入走 C2S 网络包，服务端按「本模组实体受保护 / 其他实体普通写入」策略应用。</p>
 *
 * <p>槽位：仅玩家背包 3×9 + 快捷栏 9（对齐 generic_54 箱子背景 176×222；上半部分容器区留作属性列表文本）。</p>
 */
public class EntityAttributeEditMenu extends AbstractContainerMenu {

    private final int targetEntityId;

    /** 客户端 / 服务端统一构造：只持目标实体 id。 */
    public EntityAttributeEditMenu(int containerId, Inventory playerInv, int targetEntityId) {
        super(YizxianMenus.ENTITY_ATTRIBUTE_EDIT_MENU.get(), containerId);
        this.targetEntityId = targetEntityId;

        // 玩家主背包 27 格 9×3，首格 (8,140)（generic_54 布局：容器区上半部分留作属性列表）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // 玩家快捷栏 9 格，首格 (8,198)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    /** 目标实体 id（客户端用于读取属性、提交编辑）。 */
    public int getTargetEntityId() {
        return targetEntityId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 纯编辑界面无容器槽，Shift 点击不做任何移动
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
