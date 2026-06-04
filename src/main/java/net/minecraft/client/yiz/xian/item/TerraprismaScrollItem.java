package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class TerraprismaScrollItem extends Item implements IWeaponItem {

    private static final String COUNT_KEY = "yizxianmod:sword_count";
    private static final int MAX_SWORDS = 24;

    public TerraprismaScrollItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(held);

        boolean shift = player.isShiftKeyDown();
        int count = getSwordCount(held);

        if (shift) {
            if (count > 0) {
                setSwordCount(held, count - 1);
            }
        } else {
            if (count < MAX_SWORDS) {
                setSwordCount(held, count + 1);
            }
        }

        return InteractionResultHolder.success(held);
    }

    // ── 静态工具方法：客户端渲染也用 ──

    public static int getSwordCount(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt(COUNT_KEY);
    }

    public static void setSwordCount(ItemStack stack, int count) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        if (count <= 0) {
            tag.remove(COUNT_KEY);
        } else {
            tag.putInt(COUNT_KEY, count);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getMaxSwords() {
        return MAX_SWORDS;
    }
}