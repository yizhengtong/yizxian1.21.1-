package net.minecraft.client.yiz.xian.api;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 突进来源注册表。
 *
 * <p>多来源时按注册顺序取第一个 {@link BoostProvider#isActive} 的。
 * 突进数据是玩家全局共享池（见 {@link BoostData}），不按来源隔离。</p>
 */
public final class BoostRegistry {

    private static final List<BoostProvider> PROVIDERS = new ArrayList<>();

    private BoostRegistry() {}

    public static void register(BoostProvider provider) {
        PROVIDERS.add(provider);
    }

    /** 返回第一个 active 的 provider；无则 null。 */
    public static BoostProvider getActive(Player player) {
        for (BoostProvider p : PROVIDERS) {
            if (p.isActive(player)) return p;
        }
        return null;
    }

    /** 玩家当前是否有任意突进来源。 */
    public static boolean hasActive(Player player) {
        return getActive(player) != null;
    }
}
