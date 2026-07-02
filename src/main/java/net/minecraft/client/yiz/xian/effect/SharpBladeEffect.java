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
    /** 每级伤害增幅（小数倍率，0.15 = +15%）。前置库 modifyHurtAmount 以 amount *= (1 + amp) 消费。 */
    private static final double AMP_PER_LEVEL = 0.15;

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
        // 效果由 recalculate() 驱动，execute 在此保留但不在每 tick 调度
    }

    /**
     * 重算该物品上利刃效果的伤害加成。
     * 每次等级变动后调用，取代每 tick 调度。
     *
     * <p>写入 {@code %damage_amplification} NBT（非 ATTACK_DAMAGE 物品修饰符）。
     * 前置库 {@code LivingEntityMixin.modifyHurtAmount} 在 hurt() 入口以
     * {@code amount *= (1 + amp)} 消费该值，立即生效，且与原版属性系统不冲突。
     * 每 level 提供 {@link #AMP_PER_LEVEL}（+15%）伤害增幅。</p>
     */
    public static void recalculate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        int level = readStoredLevel(stack);
        ItemAttributeHandler.setDamageAmplification(stack, level * AMP_PER_LEVEL);
    }

    @Override
    public List<String> getTalentDetailLines(LivingEntity entity) {
        return List.of("§6利刃: §e每级 +15% §6基础伤害");
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
}
