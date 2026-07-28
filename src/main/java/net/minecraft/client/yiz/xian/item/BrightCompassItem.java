package net.minecraft.client.yiz.xian.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.client.yiz.xian.menu.LightCompassMenu;
import net.minecraft.client.yiz.xian.render.LightCompassHighlightRenderer;

/**
 * 光明指南针。
 *
 * Shift + 右键 → 打开「光明指南针」容器 GUI（搜索物品、管理工作槽）。
 * 右键         → 一次性扫描周围 128 格，高亮工作槽中物品对应的方块/实体，持续发光 60 秒。
 */
public class BrightCompassItem extends Item {

    public BrightCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // ── Shift + 右键：打开配置面板 ──
            if (level.isClientSide) {
                return InteractionResultHolder.success(stack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new LightCompassMenu(id, inv),
                    net.minecraft.network.chat.Component.translatable("container.yizxianmod.light_compass")
                ));
            }
            return InteractionResultHolder.consume(stack);
        }

        // ── 右键（无 Shift）：触发一次性扫描 ──
        if (level.isClientSide) {
            LightCompassHighlightRenderer.triggerScan();
        }
        return InteractionResultHolder.success(stack);
    }
}
