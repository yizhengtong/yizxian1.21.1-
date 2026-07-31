package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.api.IPassiveItem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.hud.BuffHudEntry;
import net.minecraft.client.yiz.hud.BuffHudRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探索者护臂 — 击杀目标时叠层 armor/spell_defense/spell_power，逐层衰减。
 *
 * <pre>
 *  基础属性：法术强度+10、护甲+1、法术防御+1、全能吸血+20
 *  每层：法术强度+10、护甲+1、法术防御+1
 *  衰减：15 秒未击杀 -1 层
 *  层数上限：20（每槽独立）
 *  死亡：全部清零
 * </pre>
 *
 * <p>支持多件同装备：每个装备槽各一件、各计各的层数，击杀时每件 +1，
 * 上限独立（每槽最多 20 层）。层数纯内存 + S2C 事件同步供 HUD 每件显示一行。</p>
 */
public class ExplorerVambraceItem extends Item implements IEquipmentItem, IPassiveItem {

    private static final int DECAY_TICKS = 15 * 20;
    public static final int SLOT_COUNT = 6;
    /** 层数上限（每槽独立） */
    private static final int MAX_STACKS = 20;
    private static final double PER_STACK_SP = 10.0;
    private static final double PER_STACK_ARMOR = 1.0;
    private static final double PER_STACK_SD = 1.0;

    /** 叠层：纯内存，UUID → 6 槽层数 */
    private static final Map<UUID, int[]> STACKS = new ConcurrentHashMap<>();
    /** 最近击杀时间（服务端，衰减用，每槽独立） */
    private static final Map<UUID, long[]> LAST_KILL = new ConcurrentHashMap<>();
    /** 客户端缓存：由 S2C 包更新，供 HUD 读。 */
    private static volatile int[] CLIENT_CACHE = new int[SLOT_COUNT];

    private static final ResourceLocation MOD_SP = ResourceLocation.parse("yizxianmod:explorer_sp");
    private static final ResourceLocation MOD_ARM = ResourceLocation.parse("yizxianmod:explorer_arm");
    private static final ResourceLocation MOD_SD = ResourceLocation.parse("yizxianmod:explorer_sd");

    static {
        NeoForge.EVENT_BUS.register(ExplorerVambraceItem.class);
        // HUD：每件护臂各显示一行（层数）
        BuffHudRegistry.register(player -> {
            if (player == null) return List.of();
            int[] stacks = CLIENT_CACHE;
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            if (data == null) return List.of();
            List<BuffHudEntry.Display> out = new ArrayList<>();
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack eq = data.equipment().getItem(i);
                if (eq.getItem() instanceof ExplorerVambraceItem && stacks[i] > 0) {
                    out.add(new BuffHudEntry.Display(true, eq, stacks[i]));
                }
            }
            return out;
        });
    }

    public ExplorerVambraceItem() {
        super(new Properties().stacksTo(1)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildBaseModifiers()));
    }

    private static ItemAttributeModifiers buildBaseModifiers() {
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.SPELL_POWER,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:explorer_base_sp"),
                    10.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.ARMOR,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:explorer_base_arm"),
                    1.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.SPELL_DEFENSE,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:explorer_base_sd"),
                    1.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.LIFE_STEAL,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:explorer_base_ls"),
                    20.0, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·探索"));
        tooltip.add(Component.literal("§7击杀目标时叠加属性（每层 §f法术强度+10 §7、§f护甲+1 §7、§f法术防御+1§7），§b最多" + MAX_STACKS + "层"));
        tooltip.add(Component.literal("§7每件独立叠层；未击杀 15 秒衰减 1 层；死亡全部清零"));
    }

    // ── IEquipmentItem ──────────────────────────────

    @Override public String getUniqueEquipmentGroup() { return "explorer_vambrace"; }
    @Override public String getUniquePassiveGroup() { return "explorer_vambrace"; }

    @Override
    public void onUnequip(Player player, ItemStack stack, int slot) {
        // 清卸下槽的 modifier + 层数
        if (slot < 0 || slot >= SLOT_COUNT) return;
        int[] arr = STACKS.get(player.getUUID());
        if (arr != null) arr[slot] = 0;
        syncStackModifiers(player);
        pushStacks(player);
    }

    // ── IPassiveItem: onWornTick 衰减（每槽独立）──────

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        int slot = findSlot(player, stack);
        if (slot < 0) return;
        UUID uuid = player.getUUID();
        long[] la = LAST_KILL.get(uuid);
        if (la == null) return;
        long now = player.level().getGameTime();
        long elapsed = now - la[slot];
        int decay = (int) (elapsed / DECAY_TICKS);
        if (decay > 0) {
            int[] arr = STACKS.computeIfAbsent(uuid, k -> new int[SLOT_COUNT]);
            int next = Math.max(0, arr[slot] - decay);
            la[slot] = now - (elapsed % DECAY_TICKS);
            if (next != arr[slot]) {
                arr[slot] = next;
                syncStackModifiers(player);
                pushStacks(player);
            }
        }
    }

    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {}
    // 叠层由 onLivingDeath 事件触发

    // ── 击杀叠层：每件护臂各 +1 ────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // SPELL 溅射致死不叠层（防卢登无限链）
        if (net.minecraft.client.yiz.core.SpellSourceTracker.isActive()) return;
        if (event.getSource().getEntity() instanceof Player player
            && !player.level().isClientSide()) {
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            if (data == null) return;
            UUID uuid = player.getUUID();
            int[] arr = STACKS.computeIfAbsent(uuid, k -> new int[SLOT_COUNT]);
            long[] la = LAST_KILL.computeIfAbsent(uuid, k -> new long[SLOT_COUNT]);
            boolean any = false;
            long now = player.level().getGameTime();
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (data.equipment().getItem(i).getItem() instanceof ExplorerVambraceItem) {
                    arr[i] = Math.min(MAX_STACKS, arr[i] + 1);
                    la[i] = now;
                    any = true;
                }
            }
            if (any) {
                syncStackModifiers(player);
                pushStacks(player);
            }
        }
    }

    // ── 堆叠 modifier 同步（按槽独立 id）────────────

    private static void syncStackModifiers(Player player) {
        int[] arr = STACKS.get(player.getUUID());
        var spInst = player.getAttribute(YizAttributes.SPELL_POWER);
        var armInst = player.getAttribute(YizAttributes.ARMOR);
        var sdInst = player.getAttribute(YizAttributes.SPELL_DEFENSE);

        // 清旧
        for (int i = 0; i < SLOT_COUNT; i++) {
            ResourceLocation spId = ResourceLocation.parse(MOD_SP + "_" + i);
            ResourceLocation armId = ResourceLocation.parse(MOD_ARM + "_" + i);
            ResourceLocation sdId = ResourceLocation.parse(MOD_SD + "_" + i);
            if (spInst != null) spInst.removeModifier(spId);
            if (armInst != null) armInst.removeModifier(armId);
            if (sdInst != null) sdInst.removeModifier(sdId);
            if (arr != null && arr[i] > 0) {
                double sp = PER_STACK_SP * arr[i];
                double arm = PER_STACK_ARMOR * arr[i];
                double sd = PER_STACK_SD * arr[i];
                if (spInst != null) spInst.addTransientModifier(
                    new AttributeModifier(spId, sp, AttributeModifier.Operation.ADD_VALUE));
                if (armInst != null) armInst.addTransientModifier(
                    new AttributeModifier(armId, arm, AttributeModifier.Operation.ADD_VALUE));
                if (sdInst != null) sdInst.addTransientModifier(
                    new AttributeModifier(sdId, sd, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private static void clearStackModifiers(Player player) {
        var spInst = player.getAttribute(YizAttributes.SPELL_POWER);
        var armInst = player.getAttribute(YizAttributes.ARMOR);
        var sdInst = player.getAttribute(YizAttributes.SPELL_DEFENSE);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (spInst != null) spInst.removeModifier(ResourceLocation.parse(MOD_SP + "_" + i));
            if (armInst != null) armInst.removeModifier(ResourceLocation.parse(MOD_ARM + "_" + i));
            if (sdInst != null) sdInst.removeModifier(ResourceLocation.parse(MOD_SD + "_" + i));
        }
    }

    // ── S2C 客户端缓存 ──────────────────────────────

    public static void cacheClientStacks(int[] stacks) { CLIENT_CACHE = stacks; }

    private static void pushStacks(Player player) {
        if (player instanceof ServerPlayer sp) {
            int[] arr = STACKS.getOrDefault(sp.getUUID(), new int[SLOT_COUNT]);
            net.minecraft.client.yiz.xian.network.S2CExplorerStacksPayload.sendTo(sp, arr);
        }
    }

    // ── 死亡清零 ────────────────────────────────────

    public static void onPlayerDeath(Player player) {
        UUID uuid = player.getUUID();
        STACKS.remove(uuid);
        LAST_KILL.remove(uuid);
        clearStackModifiers(player);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CExplorerStacksPayload.sendTo(sp, new int[SLOT_COUNT]);
        }
    }

    // ── 登出持久化 / 登入恢复（JSON 存档目录，见 EquipmentStackPersist）──

    /** 登出时快照当前层数（供 EquipmentStackPersist 写 JSON）。 */
    @javax.annotation.Nullable
    public static int[] snapshotStacks(Player player) {
        int[] arr = STACKS.get(player.getUUID());
        if (arr == null) return null;
        boolean allZero = true;
        for (int i = 0; i < SLOT_COUNT; i++) if (arr[i] != 0) { allZero = false; break; }
        return allZero ? null : arr;
    }

    /** 登入时从 JSON 数据恢复。 */
    public static void loadFromPersist(Player player, int[] arr) {
        if (arr == null) return;
        boolean any = false;
        for (int i = 0; i < SLOT_COUNT; i++) if (arr[i] > 0) { any = true; break; }
        if (!any) return;
        int[] clamped = arr.clone();
        for (int i = 0; i < SLOT_COUNT; i++) clamped[i] = Math.min(MAX_STACKS, clamped[i]);
        STACKS.put(player.getUUID(), clamped);
        syncStackModifiers(player);
        pushStacks(player);
    }

    // ── 内部 ────────────────────────────────────────

    private int findSlot(Player player, ItemStack stack) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return -1;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (data.equipment().getItem(i) == stack) return i;
        }
        return -1;
    }
}
