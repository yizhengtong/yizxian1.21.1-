package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.weapon.WeaponLevelData;
import net.minecraft.client.yiz.weapon.WeaponProfile;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class TerraprismaScrollItem extends SummonWeaponItem {

    static final ResourceLocation WEAPON_ID =
        ResourceLocation.fromNamespaceAndPath(YizxianMod.MODID, "terraprisma_scroll");
    static final WeaponProfile PROFILE = buildDefault();

    private static final String COUNT_KEY = "yizxianmod:sword_count";

    private final TerraprismaLevel spec;

    // ═══ TerraprismaLevel — 从 Profile extra 重建的类型安全视图 ═══
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

    // ═══ 构造 ═══

    public TerraprismaScrollItem(int level) {
        super(new Properties(), WEAPON_ID, PROFILE, level);
        this.spec = buildSpec(getLevelData());
    }

    private static TerraprismaLevel buildSpec(WeaponLevelData data) {
        if (data == null) return TABLE[0];
        return new TerraprismaLevel(
            data.getExtraInt("maxSwords"),
            data.getExtra("hurtDmg"),
            DamageKind.values()[(int) data.getExtra("dmgKind")],
            data.getExtra("trueDmg"),
            data.getExtra("modHp"),
            data.getExtra("modHpPct"),
            data.getExtraInt("antiHealSec"),
            data.getExtraInt("antiHealCapSec")
        );
    }

    /** 代码默认 Profile：5 级品质，extra 中存储 Terraprisma 专属参数。 */
    public static WeaponProfile buildDefault() {
        return WeaponProfile.builder(WEAPON_ID)
            // Level 1: 平凡
            .level(1).extra("maxSwords", 2).extra("hurtDmg", 3).extra("dmgKind", 0)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            // Level 2: 优秀
            .level(2).extra("maxSwords", 4).extra("hurtDmg", 3).extra("dmgKind", 0)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            // Level 3: 精良
            .level(3).extra("maxSwords", 5).extra("hurtDmg", 4).extra("dmgKind", 1)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            // Level 4: 史诗
            .level(4).extra("maxSwords", 7).extra("hurtDmg", 0).extra("dmgKind", 2)
                     .extra("trueDmg", 5).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 5).extra("antiHealCapSec", 15).next()
            // Level 5: 传说
            .level(5).extra("maxSwords", 9).extra("hurtDmg", 0).extra("dmgKind", 3)
                     .extra("trueDmg", 2).extra("modHp", 3).extra("modHpPct", 0.0001)
                     .extra("antiHealSec", 5).extra("antiHealCapSec", 45)
            .build();
    }

    // ═══ 静态备用表（profile 为 null 时的回退） ═══
    private static final TerraprismaLevel[] TABLE = {
        new TerraprismaLevel(2, 3, DamageKind.PHYSICAL,   0, 0, 0,       0,  0),
        new TerraprismaLevel(4, 3, DamageKind.PHYSICAL,   0, 0, 0,       0,  0),
        new TerraprismaLevel(5, 4, DamageKind.MAGIC,      0, 0, 0,       0,  0),
        new TerraprismaLevel(7, 0, DamageKind.TRUE_DAMAGE,5, 0, 0,       5, 15),
        new TerraprismaLevel(9, 0, DamageKind.HYBRID,     2, 3, 0.0001, 5, 45),
    };

    public static TerraprismaLevel specOf(int level) {
        if (level < 1 || level > TABLE.length) return TABLE[0];
        return TABLE[level - 1];
    }

    // ═══ 访问器 ═══

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
        dispatchSummonAttack(attacker, target, weapon);
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
