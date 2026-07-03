package net.minecraft.client.yiz.xian.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 心之翅 — 可放入饰品槽的鞘翅变体。
 *
 * <p>放入饰品槽后，通过模组的鞘翅 Mixin（{@code MixinLocalPlayerElytra} 等）
 * 自动获得滑翔能力，与普通鞘翅行为一致。</p>
 */
public class HeartWingsItem extends Item {

    public HeartWingsItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC).durability(432));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return true;
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, net.minecraft.world.entity.LivingEntity entity, int flightTicks) {
        return true;
    }
}
