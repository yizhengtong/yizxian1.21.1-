package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 村正 — 近战武器，5 级品质（平凡→传说），无耐久，左侧展示。
 *
 * <p>攻击力：Lv1=5, Lv2=6.5, Lv3=8, Lv4=10, Lv5=13。</p>
 */
public class MuramasaItem extends MeleeWeaponItem implements ILeftHandRender {

    private static final double[] DAMAGE = {5.0, 6.5, 8.0, 10.0, 13.0};
    private static final String[] NAME_PREFIX = {"平凡", "优秀", "精良", "史诗", "传说"};

    private final int level;

    public MuramasaItem(int level) {
        super(new Properties().stacksTo(1));
        this.level = level;
    }

    public int getLevel() { return level; }

    @Override
    public double getAttackDamage(ItemStack stack) {
        return DAMAGE[level - 1];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(
            "§7品质: §f" + NAME_PREFIX[level - 1] + "  §7攻击: §c" + DAMAGE[level - 1]));
    }
}
