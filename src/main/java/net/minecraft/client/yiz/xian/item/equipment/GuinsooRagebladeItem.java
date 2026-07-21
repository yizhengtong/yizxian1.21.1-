package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.api.IPassiveItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鬼索的狂暴之刃 — 每次攻击叠加冷却缩减，逐层衰减。
 *
 * <pre>
 *              普通版      光明版
 *  冷却缩减      4          8
 *  攻击强度    0.5 点      1 点
 *  每层 CDR    +1.5       +3  (COOLDOWN_REDUCTION 值域 0-100)
 *  衰减间隔     5 秒/层    5 秒/层 (不翻倍)
 *  层数上限     10          7
 * </pre>
 */
public class GuinsooRagebladeItem extends Item implements IEquipmentItem, IPassiveItem {

    private static final int DECAY_TICKS = 5 * 20;
    private static final Map<UUID, int[]> STACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> LAST_ATK = new ConcurrentHashMap<>();
    /** 记录上次 tick 时玩家主手+副手的物品引用，用于检测切换 */
    private static final Map<UUID, net.minecraft.world.item.Item[]> LAST_HELD = new ConcurrentHashMap<>();

    private final boolean bright;

    public GuinsooRagebladeItem(boolean bright) {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers(bright)));
        this.bright = bright;
    }

    private static ItemAttributeModifiers buildModifiers(boolean bright) {
        double m = bright ? 2.0 : 1.0;
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.COOLDOWN_REDUCTION,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:guinsoo_cdr"),
                    4.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.ATTACK_STRENGTH,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:guinsoo_as"),
                    0.5 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    // ── IEquipmentItem ──────────────────────────────

    @Override public String getUniqueEquipmentGroup() { return ""; }
    @Override public String getUniquePassiveGroup() { return ""; }

    @Override
    public void onUnequip(Player player, ItemStack stack, int slot) {
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            inst.removeModifier(ResourceLocation.parse(MOD_ID_BASE + "_" + slot));
        }
        // 清理叠层状态
        int[] s = STACKS.get(player.getUUID());
        if (s != null) s[slot] = 0;
    }

    // ── IPassiveItem: onAttack 叠层 ─────────────────

    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {
        int slot = findSlot(player);
        if (slot < 0) return;
        long now = player.level().getGameTime();
        ensureArrays(player.getUUID());
        int[] s = STACKS.get(player.getUUID());
        if (s[slot] < getMaxStacks())
            s[slot]++;
        LAST_ATK.get(player.getUUID())[slot] = now;
        syncModifier(player, slot);
    }

    // ── IPassiveItem: onWornTick 衰减 + 切手检测 ────

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        int slot = findSlot(player);
        if (slot < 0) return;

        // 检测主手/副手切换 → 归零
        UUID uuid = player.getUUID();
        var mh = player.getMainHandItem().getItem();
        var oh = player.getOffhandItem().getItem();
        var last = LAST_HELD.computeIfAbsent(uuid, k -> new net.minecraft.world.item.Item[]{mh, oh});
        if (last[0] != mh || last[1] != oh) {
            last[0] = mh; last[1] = oh;
            resetStacks(player, slot, uuid);
            return;
        }

        long now = player.level().getGameTime();
        ensureArrays(uuid);
        int[] s = STACKS.get(uuid);
        long[] la = LAST_ATK.get(uuid);

        long elapsed = now - la[slot];
        int decay = (int) (elapsed / DECAY_TICKS);
        if (decay > 0) {
            s[slot] = Math.max(0, s[slot] - decay);
            la[slot] = now - (elapsed % DECAY_TICKS);
            syncModifier(player, slot);
        }
    }

    private void resetStacks(Player player, int slot, UUID uuid) {
        int[] s = STACKS.get(uuid);
        if (s != null) s[slot] = 0;
        syncModifier(player, slot);
    }

    // ── 动态修饰符同步 ──────────────────────────────

    private static final ResourceLocation MOD_ID_BASE = ResourceLocation.parse("yizxianmod:guinsoo_stacks");

    private void syncModifier(Player player, int slot) {
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst == null) return;
        ResourceLocation modId = ResourceLocation.parse(MOD_ID_BASE + "_" + slot);
        inst.removeModifier(modId);
        int stacks = STACKS.getOrDefault(player.getUUID(), new int[6])[slot];
        if (stacks > 0) {
            inst.addTransientModifier(new AttributeModifier(
                modId, getPerStackCDR() * stacks, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ── 内部 ────────────────────────────────────────

    private void ensureArrays(UUID uuid) {
        STACKS.computeIfAbsent(uuid, k -> new int[6]);
        LAST_ATK.computeIfAbsent(uuid, k -> new long[6]);
    }

    private int findSlot(Player player) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return -1;
        for (int i = 0; i < 6; i++) {
            if (data.equipment().getItem(i).getItem() == this) return i;
        }
        return -1;
    }

    // ── 公开查询 ────────────────────────────────────

    public boolean isBright() { return bright; }
    /** 死亡时调用：清空该玩家所有叠层 */
    public static void onPlayerDeath(Player player) {
        UUID uuid = player.getUUID();
        int[] s = STACKS.get(uuid);
        if (s != null) java.util.Arrays.fill(s, 0);
        LAST_HELD.remove(uuid);
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            for (int i = 0; i < 6; i++)
                inst.removeModifier(ResourceLocation.parse(MOD_ID_BASE + "_" + i));
        }
    }

    public int getMaxStacks() { return bright ? 7 : 10; }
    public double getPerStackCDR() { return bright ? 3.0 : 1.5; }
    public static int getStacks(Player player, int slot) {
        int[] s = STACKS.get(player.getUUID());
        return s != null && slot < s.length ? s[slot] : 0;
    }
}
