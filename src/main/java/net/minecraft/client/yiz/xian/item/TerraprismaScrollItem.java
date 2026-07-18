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

    public record TerraprismaLevel(
        double hurtDmg,
        DamageKind kind,
        double trueDmg, double modHp, double modHpPctOfMax,
        int antiHealSec, int antiHealCapSec
    ) {
        public double totalDamage() {
            return switch (kind) {
                case PHYSICAL, MAGIC -> hurtDmg;
                case TRUE_DAMAGE -> trueDmg;
                case HYBRID -> trueDmg + modHp;
            };
        }
    }

    public enum DamageKind { PHYSICAL, MAGIC, TRUE_DAMAGE, HYBRID }

    public TerraprismaScrollItem(int level) {
        super(new Properties(), WEAPON_ID, PROFILE, level);
        this.spec = buildSpec(getLevelData());
    }

    private static TerraprismaLevel buildSpec(WeaponLevelData data) {
        if (data == null) return TABLE[0];
        return new TerraprismaLevel(
            data.getExtra("hurtDmg"),
            DamageKind.values()[(int) data.getExtra("dmgKind")],
            data.getExtra("trueDmg"),
            data.getExtra("modHp"),
            data.getExtra("modHpPct"),
            data.getExtraInt("antiHealSec"),
            data.getExtraInt("antiHealCapSec")
        );
    }

    public static WeaponProfile buildDefault() {
        return WeaponProfile.builder(WEAPON_ID)
            .level(1).extra("hurtDmg", 2).extra("dmgKind", 0)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            .level(2).extra("hurtDmg", 3).extra("dmgKind", 0)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            .level(3).extra("hurtDmg", 4).extra("dmgKind", 1)
                     .extra("trueDmg", 0).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 0).extra("antiHealCapSec", 0).next()
            .level(4).extra("hurtDmg", 0).extra("dmgKind", 2)
                     .extra("trueDmg", 5).extra("modHp", 0).extra("modHpPct", 0)
                     .extra("antiHealSec", 5).extra("antiHealCapSec", 15).next()
            .level(5).extra("hurtDmg", 0).extra("dmgKind", 3)
                     .extra("trueDmg", 2.4).extra("modHp", 3.6).extra("modHpPct", 0.006)
                     .extra("antiHealSec", 5).extra("antiHealCapSec", 45)
            .build();
    }

    private static final TerraprismaLevel[] TABLE = {
        new TerraprismaLevel(2,   DamageKind.PHYSICAL,   0,   0,   0,      0,  0),
        new TerraprismaLevel(3,   DamageKind.PHYSICAL,   0,   0,   0,      0,  0),
        new TerraprismaLevel(4,   DamageKind.MAGIC,      0,   0,   0,      0,  0),
        new TerraprismaLevel(0,   DamageKind.TRUE_DAMAGE, 5,   0,   0,      5, 15),
        new TerraprismaLevel(0,   DamageKind.HYBRID,      2.4, 3.6, 0.006, 5, 45),
    };

    public static TerraprismaLevel specOf(int level) {
        if (level < 1 || level > TABLE.length) return TABLE[0];
        return TABLE[level - 1];
    }

    public TerraprismaLevel getSpec() { return spec; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean shift = player.isShiftKeyDown();
        int count = getSwordCount(held);
        int max = getEffectiveMaxSwords(player);

        if (!level.isClientSide) {
            if (shift) {
                if (count > 0) setSwordCount(held, count - 1);
            } else {
                if (count < max) setSwordCount(held, count + 1);
            }
        }
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        int count = getSwordCount(stack);
        tooltip.add(Component.literal("§7唤剑: §f" + count));
        String dmgLabel = switch (spec.kind) {
            case PHYSICAL -> "§c" + (int)spec.hurtDmg + " §7(物理)";
            case MAGIC     -> "§d" + (int)spec.hurtDmg + " §7(魔法)";
            case TRUE_DAMAGE -> "§4" + (int)spec.trueDmg + " §7(真实伤害)";
            case HYBRID -> "§4" + String.format("%.1f", spec.trueDmg) + " §7真伤 §c+ " + String.format("%.1f", spec.modHp) + " §7固伤";
        };
        tooltip.add(Component.literal("§7单剑伤害: " + dmgLabel));
        if (spec.antiHealSec() > 0) {
            tooltip.add(Component.literal("§7禁疗: §c" + spec.antiHealSec() + "s §7(上限 §c" + spec.antiHealCapSec() + "s§7)"));
        }
    }

    @Override
    public int getSummonCount(ItemStack weapon) { return getSwordCount(weapon); }

    @Override
    public int getMaxSummonCount(ItemStack weapon) { return 0; }

    @Override
    public void increaseCount(ItemStack weapon) {
        int cur = getSwordCount(weapon);
        if (cur < 99) setSwordCount(weapon, cur + 1);
    }

    @Override
    public void decreaseCount(ItemStack weapon) {
        int cur = getSwordCount(weapon);
        if (cur > 0) setSwordCount(weapon, cur - 1);
    }

    public static int getEffectiveMaxSwords(Player player) {
        var inst = player.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.MAX_MINIONS);
        if (inst != null) { double val = inst.getValue(); return Math.max(1, (int) val); }
        return 1;
    }

    @Override
    public boolean onSummonAttack(Player attacker, LivingEntity target, ItemStack weapon) {
        dispatchSummonAttack(attacker, target, weapon);
        var dir = target.position().subtract(attacker.position()).normalize();
        attacker.setDeltaMovement(dir.scale(1.5));
        attacker.hurtMarked = true;
        return true;
    }

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
