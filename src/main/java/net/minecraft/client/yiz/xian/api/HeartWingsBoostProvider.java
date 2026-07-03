package net.minecraft.client.yiz.xian.api;

import net.minecraft.world.entity.player.Player;

/**
 * 心之翅突进来源：饰品槽装备心之翅时 active。
 *
 * <p>当前唯一的 {@link BoostProvider}。将来添加新突进来源时，
 * 实现该接口并到客户端初始化处 {@link BoostRegistry#register} 即可。</p>
 *
 * <p>能力判定复用 {@link AccessoryContainer#hasHeartWings}（统一入口，
 * 内部 getIfExists 不创建空实例，避免客户端缓存到空 _c）。</p>
 */
public final class HeartWingsBoostProvider implements BoostProvider {

    /** 心之翅专属：最多堆叠 2 次突进。 */
    private static final int HEART_WINGS_MAX = 2;
    /** 心之翅专属：每 4 秒（80 tick）恢复一层。 */
    private static final int HEART_WINGS_INTERVAL = 80;

    public static final HeartWingsBoostProvider INSTANCE = new HeartWingsBoostProvider();

    private HeartWingsBoostProvider() {}

    @Override
    public boolean isActive(Player player) {
        return AccessoryContainer.hasHeartWings(player);
    }

    @Override
    public int getMaxBoosts(Player player) { return HEART_WINGS_MAX; }

    @Override
    public int getRegenInterval(Player player) { return HEART_WINGS_INTERVAL; }
}
