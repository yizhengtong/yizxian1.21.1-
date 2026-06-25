package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 村正 — 近战剑，5 级品质（平凡→传说），无耐久。
 */
public class MuramasaItem extends MeleeWeaponItem implements ILeftHandRender {

    private static final double[] DAMAGE = {5.0, 6.5, 8.0, 10.0, 13.0};
    private static final double[] SPEED  = {1.6, 1.6, 1.6,  1.6,  1.6};
    private static final Rarity[] RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.EPIC};
    private static final String[] NAME_PREFIX = {"平凡", "优秀", "精良", "史诗", "传说"};

    private final int level;

    public MuramasaItem(int level) {
        super(new Properties().stacksTo(1).rarity(RARITY[level - 1]),
              DAMAGE[level - 1], SPEED[level - 1]);
        this.level = level;
    }

    public int getLevel() { return level; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(
            "§7品质: §f" + NAME_PREFIX[level - 1]
            + "  §7攻击: §c" + DAMAGE[level - 1]
            + "  §7攻速: §b" + SPEED[level - 1]));
    }
}
