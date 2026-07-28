package net.minecraft.client.yiz.xian.item.equipment;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.client.yiz.api.IPassiveItem;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.hud.BuffHudEntry;
import net.minecraft.client.yiz.hud.BuffHudRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
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
 *  衰减间隔     5 秒/层    5 秒/层 (不翻倍)
 *  层数上限     无上限      无上限
 * </pre>
 *
 * <p>支持多件同装备（多个装备槽各一份）：每件独立叠层，{@link #findSlot} 按 stack 引用
 * 精确定位当前件所在槽。叠层通过 {@link PlayerDataAPI}（key {@value #STACKS_KEY}）存储并
 * 自动 S2C 同步，客户端 {@link BuffHudRegistry} 注册的 HUD 条目据此每件各显示一行。</p>
 */
public class GuinsooRagebladeItem extends Item implements IEquipmentItem, IPassiveItem {

    private static final int DECAY_TICKS = 5 * 20;
    private static final int SLOT_COUNT = 6;
    /** 叠层 PlayerDataAPI 键：6 装备槽层数，逗号分隔（服务端写，客户端 BuffHud 读）。 */
    private static final String STACKS_KEY = "yizxianmod:guinsoo_stacks";
    /** 衰减计时（服务端临时，不需客户端同步；每槽一个） */
    private static final Map<UUID, long[]> LAST_ATK = new ConcurrentHashMap<>();
    /** 记录上次 tick 时玩家主手+副手的 ItemStack 引用，用 == 检测任意切换（同物品不同实例也触发） */
    private static final Map<UUID, ItemStack[]> LAST_HELD = new ConcurrentHashMap<>();

    private final boolean bright;

    static {
        // 类加载时注册 buff HUD 条目：返回所有激活槽的 Display（多件同装备各占一行）
        BuffHudRegistry.register(player -> {
            List<BuffHudEntry.Display> out = new ArrayList<>();
            if (player == null) return out;
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            if (data == null) return out;
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack eq = data.equipment().getItem(i);
                if (eq.getItem() instanceof GuinsooRagebladeItem) {
                    int stacks = getStacks(player, i);
                    if (stacks > 0) out.add(new BuffHudEntry.Display(true, eq, stacks));
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
        setStacks(player, slot, 0);  // 只清卸下的这一槽
    }

    // ── IPassiveItem: onAttack 叠层（每件各自触发，stack=该槽物品）─────────

    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {
        int slot = findSlot(player, stack);  // 按 stack 引用精确定位当前件槽位
        if (slot < 0) return;
        long now = player.level().getGameTime();
        ensureArrays(player.getUUID());
        setStacks(player, slot, getStacks(player, slot) + 1);  // 无叠层上限（CDR 由属性 100% 自然封顶）
        LAST_ATK.get(player.getUUID())[slot] = now;
        syncModifier(player, slot);
    }

    // ── IPassiveItem: onWornTick 衰减 + 切手检测 ────

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        int slot = findSlot(player, stack);  // 按 stack 引用精确定位当前件槽位
        if (slot < 0) return;

        // 检测主手/副手切换（ItemStack 引用比较，同物品不同实例也触发）
        // 空手→武器保留 20%，其他情况保留 50%
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

        // 衰减：只衰减当前 stack 所在槽
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

    // ── 动态修饰符同步 ──────────────────────────────

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

    // ── 叠层读写（PlayerDataAPI，自动 S2C 同步供 HUD 读）─────────

    private static void ensureArrays(UUID uuid) {
        LAST_ATK.computeIfAbsent(uuid, k -> new long[SLOT_COUNT]);
    }

    /** 读指定槽层数（客户端也能读，PlayerDataAPI 已 S2C 同步）。 */
    public static int getStacks(Player player, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        String raw = PlayerDataAPI.get(player, STACKS_KEY);
        String[] parts = raw.split(",");
        if (slot >= parts.length) return 0;
        try { return Integer.parseInt(parts[slot].trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** 写指定槽层数（服务端调用，读改写整串，自动 S2C 同步）。 */
    private static void setStacks(Player player, int slot, int val) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        int[] arr = new int[SLOT_COUNT];
        String raw = PlayerDataAPI.get(player, STACKS_KEY);
        String[] parts = raw.split(",");
        for (int i = 0; i < SLOT_COUNT && i < parts.length; i++) {
            try { arr[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException e) {}
        }
        arr[slot] = val;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOT_COUNT; i++) { if (i > 0) sb.append(","); sb.append(arr[i]); }
        PlayerDataAPI.set(player, STACKS_KEY, sb.toString());
    }

    /** 切手时所有槽叠层按比例保留（向下取整），重同步修饰符。 */
    private static void halveAllStacks(Player player, double ratio) {
        String raw = PlayerDataAPI.get(player, STACKS_KEY);
        String[] parts = raw.split(",");
        int[] arr = new int[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT && i < parts.length; i++) {
            try { arr[i] = Integer.parseInt(parts[i].trim()); } catch (NumberFormatException e) {}
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOT_COUNT; i++) {
            arr[i] = (int)(arr[i] * ratio); // ratio×stacks，向下取整
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        PlayerDataAPI.set(player, STACKS_KEY, sb.toString());
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            for (int i = 0; i < SLOT_COUNT; i++) {
                ResourceLocation modId = ResourceLocation.parse(MOD_ID_BASE + "_" + i);
                inst.removeModifier(modId);
                if (arr[i] > 0 && data != null) {
                    // 按该槽实际装备的 Guinsoo 实例取 perStack——
                    // 不同 bright 版本 perStack 不同（普通5/光明10），必须各槽各取，
                    // 不能用全局第一个 Guinsoo 的 perStack 代替（混搭时数值会错）。
                    ItemStack eq = data.equipment().getItem(i);
                    if (eq.getItem() instanceof GuinsooRagebladeItem g) {
                        inst.addTransientModifier(new AttributeModifier(
                            modId, g.getPerStackCDR() * arr[i], AttributeModifier.Operation.ADD_VALUE));
                    }
                }
            }
        }
    }

    /** 清所有槽的叠层 + 对应 CDR 修饰符（死亡调用）。 */
    private static void resetAllStacks(Player player) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOT_COUNT; i++) { if (i > 0) sb.append(","); sb.append(0); }
        PlayerDataAPI.set(player, STACKS_KEY, sb.toString());
        var inst = player.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) {
            for (int i = 0; i < SLOT_COUNT; i++)
                inst.removeModifier(ResourceLocation.parse(MOD_ID_BASE + "_" + i));
        }
    }

    // ── 内部 ────────────────────────────────

    /** 按 stack 引用精确定位当前件所在装备槽（多件同装备时各找各的槽）。 */
    private int findSlot(Player player, ItemStack stack) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return -1;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (data.equipment().getItem(i) == stack) return i;
        }
        return -1;
    }

    // ── 公开查询 ────────────────────────────────────

    public boolean isBright() { return bright; }

    /** 死亡时调用：清空该玩家所有叠层。 */
    public static void onPlayerDeath(Player player) {
        resetAllStacks(player);
        LAST_ATK.remove(player.getUUID());
    }

    public int getMaxStacks() { return Integer.MAX_VALUE; } // 无上限
    public double getPerStackCDR() { return bright ? 10.0 : 5.0; }
}
