package net.minecraft.client.yiz.xian.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class TerraprismaScrollItem extends SummonWeaponItem {

    private static final String COUNT_KEY = "yizxianmod:sword_count";
    private final int level;
    private final TerraprismaLevel spec;

    // ═══ 五级预设 ═══
    public record TerraprismaLevel(
        int maxSwords, double hurtDmg,
        DamageKind kind,
        double trueDmg, double modHp, double modHpPctOfMax,
        int antiHealSec, int antiHealCapSec
    ) {}

    public enum DamageKind {
        /** ①fake + ②原版 playerAttack */  PHYSICAL,
        /** ①fake + ②原版 indirectMagic */ MAGIC,
        /** ①fake + ③trueDamage */         TRUE_DAMAGE,
        /** ①fake + ③trueDamage + ④modifyHealth */ HYBRID
    }

    private static final TerraprismaLevel[] TABLE = {
        // idx=1 平凡
        new TerraprismaLevel(2, 3, DamageKind.PHYSICAL,   0,0,0,     0,  0),
        // idx=2 优秀
        new TerraprismaLevel(4, 3, DamageKind.PHYSICAL,   0,0,0,     0,  0),
        // idx=3 精良
        new TerraprismaLevel(5, 4, DamageKind.MAGIC,      0,0,0,     0,  0),
        // idx=4 史诗
        new TerraprismaLevel(7, 0, DamageKind.TRUE_DAMAGE,5,0,0,     5, 15),
        // idx=5 传说
        new TerraprismaLevel(9, 0, DamageKind.HYBRID,     2,3,0.0001,5, 45),
    };

    public static TerraprismaLevel specOf(int level) { return TABLE[level - 1]; }

    // ═══ 构造 ═══

    public TerraprismaScrollItem(int level) {
        super(new Item.Properties().stacksTo(1));
        this.level = level;
        this.spec = specOf(level);
    }

    public int getLevel()           { return level; }
    public TerraprismaLevel getSpec() { return spec; }

    public static int maxSwordsOf(int level) { return specOf(level).maxSwords; }
    public static int maxSwordsOf(ItemStack stack) {
        if (stack.getItem() instanceof TerraprismaScrollItem tsi) return tsi.spec.maxSwords;
        return 0;
    }

    // ═══ use() — 右键+1 / Shift右键-1 ═══

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean shift = player.isShiftKeyDown();
        int count = getSwordCount(held);
        int max = this.spec.maxSwords;

        if (!level.isClientSide) {
            if (shift) {
                if (count > 0) setSwordCount(held, count - 1);
            } else {
                if (count < max) setSwordCount(held, count + 1);
            }
        }
        return InteractionResultHolder.success(held);
    }

    // ═══ tooltip: 唤剑数/最大数 ═══

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int count = getSwordCount(stack);
        tooltip.add(Component.literal("§7唤剑: §f" + count + "§7/§f" + spec.maxSwords));
    }

    // ═══ SummonWeaponItem 钩子 ═══

    @Override
    public int getSummonCount(ItemStack weapon) {
        return getSwordCount(weapon);
    }

    @Override
    public int getMaxSummonCount(ItemStack weapon) {
        return spec.maxSwords;
    }

    @Override
    public void increaseCount(ItemStack weapon) {
        int cur = getSwordCount(weapon);
        if (cur < spec.maxSwords) setSwordCount(weapon, cur + 1);
    }

    @Override
    public void decreaseCount(ItemStack weapon) {
        int cur = getSwordCount(weapon);
        if (cur > 0) setSwordCount(weapon, cur - 1);
    }

    @Override
    public boolean onSummonAttack(Player attacker, LivingEntity target, ItemStack weapon) {
        // 子类自己的攻击逻辑 —— 会心一击突进 + 伤害
        var dir = target.position().subtract(attacker.position()).normalize();
        attacker.setDeltaMovement(dir.scale(1.5));
        attacker.hurtMarked = true;
        return true;
    }

    // ═══ NBT 读写 ═══

    public static int getSwordCount(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt(COUNT_KEY);
    }

    public static void setSwordCount(ItemStack stack, int count) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.putInt(COUNT_KEY, Math.max(0, count));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
