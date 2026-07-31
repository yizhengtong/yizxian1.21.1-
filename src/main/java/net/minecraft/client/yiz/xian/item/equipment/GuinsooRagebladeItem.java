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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鬼索的狂暴之刃 — 每次攻击叠加冷却缩减，逐层衰减。
 *
 * <pre>
 *              普通版      光明版
 *  冷却缩减      6          12
 *  攻击强度    10%        20%
 *  每层 CDR    +5         +10  (COOLDOWN_REDUCTION 值域 0-100)
 *  衰减间隔     5 秒/层    5 秒/层
 *  层数上限     无上限      无上限
 * </pre>
 *
 * <p>支持多件同装备（多个装备槽各一份）：每件独立叠层，{@link #findSlot} 按 stack 引用
 * 精确定位当前件所在槽。层数纯内存 {@link #STACKS} 服务端存、{@link #CLIENT_CACHE} 客户端读，
 * 通过 {@code S2CGuinsooStacksPayload} 按需同步供 HUD 渲染。</p>
 */
public class GuinsooRagebladeItem extends Item implements IEquipmentItem, IPassiveItem {

    private static final int DECAY_TICKS = 5 * 20;
    public static final int SLOT_COUNT = 6;
    /** 叠层：纯内存（服务端写，替换原 PlayerDataAPI 每 tick 全量同步）。 */
    private static final Map<UUID, int[]> STACKS = new ConcurrentHashMap<>();
    /** 客户端缓存：由 S2CGuinsooStacksPayload 更新，供 HUD 读。 */
    private static volatile int[] CLIENT_CACHE = new int[SLOT_COUNT];
    /** 服务端衰减计时 */
    private static final Map<UUID, long[]> LAST_ATK = new ConcurrentHashMap<>();
    /** 切手检测 */
    private static final Map<UUID, ItemStack[]> LAST_HELD = new ConcurrentHashMap<>();

    private final boolean bright;

    static {
        BuffHudRegistry.register(player -> {
            List<BuffHudEntry.Display> out = new ArrayList<>();
            if (player == null) return out;
            int[] stacks = getClientStacks(player);
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            if (data == null) return out;
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack eq = data.equipment().getItem(i);
                if (eq.getItem() instanceof GuinsooRagebladeItem && stacks[i] > 0) {
                    out.add(new BuffHudEntry.Display(true, eq, stacks[i]));
                }
            }
            return out;
        });
    }

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
                    6.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .add(YizAttributes.ATTACK_STRENGTH,
                new AttributeModifier(ResourceLocation.parse("yizxianmod:guinsoo_as"),
                    10.0 * m, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ANY)
            .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9被动·狂暴"));
        tooltip.add(Component.literal("§7每次攻击叠加攻击速度（每层 §f+" + (bright ? "10" : "5") + "%§7），§c无上限"));
        tooltip.add(Component.literal("§7未攻击 5 秒衰减 1 层；切手保留50%层数（空手→武器保留20%），死亡清零"));
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
        setStacks(player, slot, 0);
    }

    // ── IPassiveItem: onAttack 叠层 ────────────────

    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {
        int slot = findSlot(player, stack);
        if (slot < 0) return;
        long now = player.level().getGameTime();
        ensureArrays(player.getUUID());
        setStacks(player, slot, getStacks(player, slot) + 1);
        LAST_ATK.get(player.getUUID())[slot] = now;
        syncModifier(player, slot);
    }

    // ── IPassiveItem: onWornTick 衰减 + 切手 ───────

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        int slot = findSlot(player, stack);
        if (slot < 0) return;

        UUID uuid = player.getUUID();
        var mh = player.getMainHandItem();
        var oh = player.getOffhandItem();
        var last = LAST_HELD.computeIfAbsent(uuid, k -> new ItemStack[]{mh, oh});
        if (last[0] != mh || last[1] != oh) {
            boolean fromEmpty = last[0].isEmpty();
            last[0] = mh; last[1] = oh;
            halveAllStacks(player, fromEmpty ? 0.2 : 0.5);
            return;
        }

        long now = player.level().getGameTime();
        ensureArrays(uuid);
        long[] la = LAST_ATK.get(uuid);
        long elapsed = now - la[slot];
        int decay = (int) (elapsed / DECAY_TICKS);
        if (decay > 0) {
            int cur = getStacks(player, slot);
            int next = Math.max(0, cur - decay);
            la[slot] = now - (elapsed % DECAY_TICKS);
            if (next != cur) {
                setStacks(player, slot, next);
                syncModifier(player, slot);
            }
        }
    }

    // ── 动态修饰符 ──────────────────────────────────

    private static final ResourceLocation MOD_ID_BASE = ResourceLocation.parse("yizxianmod:guinsoo_stacks");

    private void syncModifier(Player player, int slot) {
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst == null) return;
        ResourceLocation modId = ResourceLocation.parse(MOD_ID_BASE + "_" + slot);
        inst.removeModifier(modId);
        int stacks = getStacks(player, slot);
        if (stacks > 0) {
            inst.addTransientModifier(new AttributeModifier(
                modId, getPerStackCDR() * stacks, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ── 叠层读写（纯内存 + 事件驱动 S2C）─────────────

    private static void ensureArrays(UUID uuid) {
        STACKS.computeIfAbsent(uuid, k -> new int[SLOT_COUNT]);
        LAST_ATK.computeIfAbsent(uuid, k -> new long[SLOT_COUNT]);
    }

    /** 服务端读指定槽层数。 */
    private static int getStacks(Player player, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        int[] arr = STACKS.get(player.getUUID());
        return arr != null ? arr[slot] : 0;
    }

    /** 服务端写指定槽 + 推 S2C 同步。 */
    private static void setStacks(Player player, int slot, int val) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        int[] arr = STACKS.computeIfAbsent(player.getUUID(), k -> new int[SLOT_COUNT]);
        arr[slot] = val;
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CGuinsooStacksPayload.sendTo(sp, arr);
        }
    }

    /** S2C payload 回调：写入客户端缓存供 HUD 读。 */
    public static void cacheClientStacks(int[] stacks) {
        CLIENT_CACHE = stacks;
    }

    /** 客户端 HUD 读：走缓存（服务端则直接读内存，但 HUD 只在客户端）。 */
    public static int[] getClientStacks(Player player) {
        // 客户端：读 payload 写入的缓存
        if (player != null && player.level().isClientSide) {
            return CLIENT_CACHE;
        }
        // 服务端：直接读内存
        return STACKS.getOrDefault(player.getUUID(), new int[SLOT_COUNT]);
    }

    // ── 切手/重置 ──────────────────────────────────

    private static void halveAllStacks(Player player, double ratio) {
        int[] arr = STACKS.computeIfAbsent(player.getUUID(), k -> new int[SLOT_COUNT]);
        for (int i = 0; i < SLOT_COUNT; i++) arr[i] = (int) (arr[i] * ratio);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CGuinsooStacksPayload.sendTo(sp, arr);
        }
        // 重建各槽 CDR modifier
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            for (int i = 0; i < SLOT_COUNT; i++) {
                ResourceLocation modId = ResourceLocation.parse(MOD_ID_BASE + "_" + i);
                inst.removeModifier(modId);
                if (arr[i] > 0 && data != null) {
                    ItemStack eq = data.equipment().getItem(i);
                    if (eq.getItem() instanceof GuinsooRagebladeItem g) {
                        inst.addTransientModifier(new AttributeModifier(
                            modId, g.getPerStackCDR() * arr[i], AttributeModifier.Operation.ADD_VALUE));
                    }
                }
            }
        }
    }

    public static void onPlayerDeath(Player player) {
        UUID uuid = player.getUUID();
        STACKS.remove(uuid);
        LAST_ATK.remove(uuid);
        if (player instanceof ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CGuinsooStacksPayload.sendTo(sp, new int[SLOT_COUNT]);
        }
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            for (int i = 0; i < SLOT_COUNT; i++)
                inst.removeModifier(ResourceLocation.parse(MOD_ID_BASE + "_" + i));
        }
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

    public boolean isBright() { return bright; }
    public int getMaxStacks() { return Integer.MAX_VALUE; }
    public double getPerStackCDR() { return bright ? 10.0 : 5.0; }

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
        STACKS.put(player.getUUID(), arr);
        applyAllModifiers(player, arr);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.client.yiz.xian.network.S2CGuinsooStacksPayload.sendTo(sp, arr);
        }
    }

    private static void applyAllModifiers(Player player, int[] arr) {
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst == null) return;
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        for (int i = 0; i < SLOT_COUNT; i++) {
            ResourceLocation modId = ResourceLocation.parse(MOD_ID_BASE + "_" + i);
            inst.removeModifier(modId);
            if (arr[i] > 0 && data != null) {
                ItemStack eq = data.equipment().getItem(i);
                if (eq.getItem() instanceof GuinsooRagebladeItem g) {
                    inst.addTransientModifier(new AttributeModifier(modId,
                        g.getPerStackCDR() * arr[i], AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }
}
