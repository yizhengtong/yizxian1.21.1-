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
        double amp = level * 15.0;
        ItemAttributeHandler.setDamageAmplification(stack, amp);
    }

    @Override
    public List<String> getTalentDetailLines(LivingEntity entity) {
        return List.of("§6利刃：增加武器 §e" + (getLevel() * 15) + "% §6伤害");
    }

    private int readStoredLevel(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return getLevel();
        CompoundTag tag = cd.copyTag();
        if (!tag.contains("yizmodqzk:effects", Tag.TAG_LIST)) return getLevel();
        ListTag list = tag.getList("yizmodqzk:effects", Tag.TAG_COMPOUND);
        String myId = getId().toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (myId.equals(t.getString("id"))) {
                return Math.max(1, t.getInt("level"));
            }
        }
        return getLevel();
    }
}
