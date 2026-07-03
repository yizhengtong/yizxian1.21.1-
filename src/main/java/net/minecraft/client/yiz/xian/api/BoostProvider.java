package net.minecraft.client.yiz.xian.api;

import net.minecraft.world.entity.player.Player;

/**
 * 突进（boost）能力来源。
 *
 * <p>心之翅是当前唯一实现，将来其它装备/物品可实现此接口提供突进，
 * 与具体物品解耦。突进数据本身由 {@link BoostData} 统一管理（玩家全局池）。</p>
 */
public interface BoostProvider {

    /** 该玩家当前是否拥有此突进来源（例如饰品槽装备了心之翅）。 */
    boolean isActive(Player player);

    /** 此来源提供的推进上限，默认 {@link BoostData#DEFAULT_MAX}。 */
    default int getMaxBoosts(Player player) { return BoostData.DEFAULT_MAX; }

    /** 此来源的恢复间隔（tick），默认 {@link BoostData#DEFAULT_REGEN}。 */
    default int getRegenInterval(Player player) { return BoostData.DEFAULT_REGEN; }
}
