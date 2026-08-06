package net.minecraft.client.yiz.xian.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.xian.menu.EntityAttributeEditMenu;

/**
 * 实体属性编辑工具 —— 手持右键任意 {@link LivingEntity} 打开原版容器界面，
 * 编辑该实体的 yizmodqzk 自定义属性。
 *
 * <p>右键流程（服务端权威）：目标实体 UUID 经 {@code IContainerFactory} 额外数据传给客户端
 * Screen 展示当前值；实际写入走 C2S 包，服务端按「本模组实体（YizxianMob）受保护写入 /
 * 其他实体普通写入」策略应用。</p>
 */
public class EntityAttributeEditorItem extends Item {

    public EntityAttributeEditorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide() && player instanceof ServerPlayer sp) {
            sp.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("实体属性编辑");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                    return new EntityAttributeEditMenu(containerId, inv, target.getId());
                }
            }, buf -> buf.writeInt(target.getId()));
        }
        return InteractionResult.SUCCESS;
    }
}
