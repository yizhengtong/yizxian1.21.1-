package net.minecraft.client.yiz.xian.effect;

import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.ItemPerception;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Set;

public class SharpBladeEffect extends AbstractEffect {

    private static final String MODID = "yizxianmod";
    private static final String EFF_ID = "sharp_blade";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MODID, EFF_ID);
    private static final String BASE_DMG_KEY = "yizmodqzk:sharp_blade_base";

    public SharpBladeEffect(int level) {
        super(
            ID,
            "effect." + MODID + "." + EFF_ID,
            "利刃",
            ParentType.ECHO,
            Math.max(1, Math.min(level, 5)),
            Set.of(new ItemPerception(
                ItemPerception.ItemSlot.MAIN_HAND,
                ItemPerception.ItemSlot.OFF_HAND
            )),
            ctx -> true,
            Rarity.RARE
        );
    }

    @Override
    public void execute(EffectContext context) {
        ItemStack stack = context.itemStack();
        if (stack == null || stack.isEmpty()) return;

        int level = readStoredLevel(stack);
        if (level <= 0) return;

        double baseDmg = getBaseDamage(stack);
        if (baseDmg <= 0) return;

        double newDmg = baseDmg * (1.0 + level * 0.15);
        ItemAttributeHandler.setAttackDamage(stack, newDmg);
    }

    @Override
    public List<String> getTalentDetailLines(LivingEntity entity) {
        return List.of("§6利刃：增加武器 §e" + (getLevel() * 15) + "% §6基础伤害");
    }

    /** 读取物品上此效果的当前等级 */
    public static int readStoredLevel(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        CompoundTag tag = cd.copyTag();
        if (!tag.contains("yizmodqzk:effects", Tag.TAG_LIST)) return 0;
        ListTag list = tag.getList("yizmodqzk:effects", Tag.TAG_COMPOUND);
        String myId = ID.toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (myId.equals(t.getString("id"))) {
                return Math.max(1, t.getInt("level"));
            }
        }
        return 0;
    }

    /** 获取武器基准伤害（首次读取时记录当前面板值） */
    private static double getBaseDamage(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            if (tag.contains(BASE_DMG_KEY)) {
                return tag.getDouble(BASE_DMG_KEY);
            }
        }
        // 第一次访问：记录当前面板伤害为基准
        double current = ItemAttributeHandler.getAttackDamage(stack);
        if (current <= 0) return 0;
        // 写入基准值
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.putDouble(BASE_DMG_KEY, current);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return current;
    }
}
