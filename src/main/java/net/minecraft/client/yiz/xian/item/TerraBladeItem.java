package net.minecraft.client.yiz.xian.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 泰拉刃 — 近战武器，5 级品质（平凡→传说），无耐久。
 *
 * <p>攻击力：Lv1=8.5, Lv2=11.5, Lv3=14, Lv4=18, Lv5=28。
 * 发光描边颜色由 {@code StagedItemHelper.glowColorForLevel} 控制。</p>
 */
import net.minecraft.client.yiz.xian.api.ILeftHandRender;

public class TerraBladeItem extends MeleeWeaponItem implements ILeftHandRender {

    private static final double[] DAMAGE = {8.5, 11.5, 14.0, 18.0, 28.0};
    private static final String[] NAME_PREFIX = {"平凡", "优秀", "精良", "史诗", "传说"};

    private final int level;

    public TerraBladeItem(int level) {
        super(new Properties().stacksTo(1)); // 无耐久
        this.level = level;
    }

    public int getLevel() { return level; }

    /** 武器攻击力，覆盖基类钩子 */
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
