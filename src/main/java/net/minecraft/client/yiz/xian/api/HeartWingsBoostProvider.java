package net.minecraft.client.yiz.xian.api;

import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.world.entity.player.Player;

/**
 * 心之翅突进来源：饰品槽装备心之翅时 active。
 *
 * <p>当前唯一的 {@link BoostProvider}。将来添加新突进来源时，
 * 实现该接口并到客户端初始化处 {@link BoostRegistry#register} 即可。</p>
 */
public final class HeartWingsBoostProvider implements BoostProvider {

    public static final HeartWingsBoostProvider INSTANCE = new HeartWingsBoostProvider();

    private HeartWingsBoostProvider() {}

    @Override
    public boolean isActive(Player player) {
        AccessoryContainer c = AccessoryContainer.get(player);
        for (int i = 0; i < c.getSlotCount(); i++) {
            if (c.getItem(i).getItem() instanceof HeartWingsItem) return true;
        }
        return false;
    }
}
