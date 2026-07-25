package net.minecraft.client.yiz.xian.skill;

import net.minecraft.client.yiz.api.EffectRegistry;
import net.minecraft.client.yiz.api.IPassiveItem;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/**
 * 天雷引（被动技能物品）。
 *
 * <p><b>行为（E1 设计）</b>：每次攻击 +1 层充能；每攒满 6 层 → 获得 6 次强化攻击机会 +
 * 所有技能冷却减少 10%（立刻生效，持续至卸载）；充能满即清零可开始下一轮（与强化消耗独立）。
 * 强化攻击命中：回复 0.5 + 2% 最大生命值，消耗 1 次强化机会。</p>
 *
 * <p><b>两套独立计数</b>：充能层({@code yiz:tly_charge},0~6 满清零) 与
 * 强化次数({@code yiz:tly_boost},≥0 累加存储)。</p>
 *
 * <p><b>卸载清理</b>：onWornTick 首次登记 EffectRegistry 回调，被动卸载时清充能层/强化次数/冷却缩减修饰器。</p>
 */
public class TianLeiYinItem extends Item implements IPassiveItem {

    /** 来源 key（EffectRegistry 用） */
    private static final String SOURCE_KEY = "passive:yizxianmod:tian_lei_yin";
    /** 冷却缩减 transient 修饰器 id（区别于物品属性 tly_cdr） */
    private static final ResourceLocation CDR_BUFF_ID =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "tly_cdr_buff");

    private static final String PD_CHARGE = "yiz:tly_charge";
    private static final String PD_BOOST = "yiz:tly_boost";
    /** 客户端 HUD 读取的状态键（PlayerDataAPI，服务端写客户端读）：{charge, boost} */
    private static final String STATE_KEY = "yizxianmod:tianleiyin_state";

    /** 类加载时注册充能 HUD 条目（图标=天雷引，max=6，显示 charge 进度）。 */
    static {
        net.minecraft.client.yiz.hud.ChargeHudRegistry.register(player -> {
            net.minecraft.world.item.ItemStack icon = new net.minecraft.world.item.ItemStack(
                net.minecraft.client.yiz.xian.YizxianMod.TIAN_LEI_YIN.get());
            if (player == null) return net.minecraft.client.yiz.hud.ChargeHudEntry.Display.hidden();
            // 客户端判断：装了天雷引，或已有 charge/boost 状态
            boolean show = isTianLeiYinEquippedClient(player)
                || getClientCharge(player) > 0
                || getClientBoost(player) > 0;
            int charge = getClientCharge(player);
            return new net.minecraft.client.yiz.hud.ChargeHudEntry.Display(
                show, icon, charge, 6, false);
        });
    }

    /** 客户端判断被动槽是否装了天雷引。 */
    private static boolean isTianLeiYinEquippedClient(Player player) {
        if (player == null) return false;
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return false;
        for (int i = 0; i < 3; i++) {
            var s = data.passiveLoad().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof TianLeiYinItem) return true;
        }
        return false;
    }

    public TianLeiYinItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC)
            .component(DataComponents.ATTRIBUTE_MODIFIERS, buildModifiers()));
    }

    private static ItemAttributeModifiers buildModifiers() {
        // 注意：COOLDOWN_REDUCTION 不在此挂（E1：攒满 6 层才给 transient 冷却缩减）。
        // 保留伤害/回血系数供其他系统引用，冷却完全由 onAttack 攒满驱动。
        return ItemAttributeModifiers.builder()
            .add(YizAttributes.DAMAGE_BASE,         mod("tly_db", 2.0), EquipmentSlotGroup.ANY)
            .add(YizAttributes.DAMAGE_SPELL_COEFF,  mod("tly_dsc", 45), EquipmentSlotGroup.ANY)
            .add(YizAttributes.HEAL_BASE,           mod("tly_hb", 0.75), EquipmentSlotGroup.ANY)
            .add(YizAttributes.HEAL_HP_COEFF,       mod("tly_hhc", 1.2), EquipmentSlotGroup.ANY)
            .add(YizAttributes.DAMAGE_TYPE,         mod("tly_dt", 4), EquipmentSlotGroup.ANY)
            .add(YizAttributes.MANA_COST,           mod("tly_mc", 1), EquipmentSlotGroup.ANY)
            .build();
    }

    private static AttributeModifier mod(String id, double val) {
        return new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath("yizmodqzk", id),
            val, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§9天雷引"));
        tooltip.add(Component.literal("§7每次攻击 +1层充能，每 6 层获得 §96次强化攻击机会§7并使所有技能冷却 -10%"));
        tooltip.add(Component.literal("§7强化攻击命中：回复 §90.5 + 2%最大生命值"));
        tooltip.add(Component.literal("§7充能满即清零，可与强化消耗并行累积"));
    }

    @Override
    public void onWornTick(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer sp) || sp.level().isClientSide()) return;
        // 首次装备时登记卸载清理回调（幂等：已登记则跳过）
        if (!EffectRegistry.hasSource(sp.getUUID(), SOURCE_KEY)) {
            EffectRegistry.register(sp.getUUID(), SOURCE_KEY, () -> cleanup(sp));
        }
    }

    /** 玩家攻击命中时：充能 +1；满 6 给强化次数 + 冷却缩减；若有强化次数则回血并消耗。 */
    @Override
    public void onAttack(Player player, ItemStack stack, LivingEntity target) {
        if (!(player instanceof ServerPlayer sp) || sp.level().isClientSide()) return;
        var pd = sp.getPersistentData();

        // 充能 +1，满 6 清零 + 给 6 次强化 + 冷却缩减立刻生效
        int charge = pd.getInt(PD_CHARGE) + 1;
        if (charge >= 6) {
            pd.putInt(PD_CHARGE, 0);
            pd.putInt(PD_BOOST, pd.getInt(PD_BOOST) + 6);
            applyCooldownReduction(sp);
        } else {
            pd.putInt(PD_CHARGE, charge);
        }

        // 强化攻击：有次数则回血 + 额外感电伤害 + 消耗 1 次
        int boost = pd.getInt(PD_BOOST);
        if (boost > 0) {
            float heal = 0.5f + sp.getMaxHealth() * 0.02f;
            sp.heal(heal);
            // 额外感电伤害：DAMAGE_BASE + 法强×DAMAGE_SPELL_COEFF/100（取自天雷引属性）
            float dmg = computeShockDamage(sp, stack);
            if (dmg > 0 && target != null) {
                net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockWithDamage(
                    target, sp, dmg, 0, 0, 0);
            }
            pd.putInt(PD_BOOST, boost - 1);
        }

        writeState(sp);
    }

    /** 天雷引强化攻击的感电伤害 = DAMAGE_BASE × 有效法强/100。 */
    private static float computeShockDamage(ServerPlayer sp, ItemStack stack) {
        double base = readItemAttr(stack, YizAttributes.DAMAGE_BASE);
        double spellPow = YizAttributes.getEffectiveSpellPower(sp);
        return (float) (base * spellPow / 100.0);
    }

    private static double readItemAttr(ItemStack stack,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var mods = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        double val = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().is(attr)) val += e.modifier().amount();
        }
        return val;
    }

    /** 同步 charge/boost 给客户端 HUD（PlayerDataAPI 自动同步）。 */
    private static void writeState(ServerPlayer sp) {
        var pd = sp.getPersistentData();
        int charge = pd.getInt(PD_CHARGE);
        int boost = pd.getInt(PD_BOOST);
        PlayerDataAPI.set(sp, STATE_KEY, "{\"charge\":" + charge + ",\"boost\":" + boost + "}");
    }

    /** 客户端 HUD 读取 charge（0~6）。 */
    public static int getClientCharge(Player player) {
        String raw = PlayerDataAPI.get(player, STATE_KEY);
        return readInt(raw, "charge");
    }

    /** 客户端 HUD 读取 boost（强化次数）。 */
    public static int getClientBoost(Player player) {
        String raw = PlayerDataAPI.get(player, STATE_KEY);
        return readInt(raw, "boost");
    }

    private static int readInt(String json, String key) {
        if (json == null || json.isEmpty()) return 0;
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return 0;
        int start = i + key.length() + 3;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Integer.parseInt(json.substring(start, end)); }
        catch (Exception e) { return 0; }
    }

    /** 挂载/刷新冷却缩减 +10% 修饰器（攒满 6 层时调用，立刻生效）。 */
    private static void applyCooldownReduction(ServerPlayer sp) {
        var inst = sp.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst == null) return;
        inst.removeModifier(CDR_BUFF_ID);
        inst.addTransientModifier(new AttributeModifier(CDR_BUFF_ID, 10.0, AttributeModifier.Operation.ADD_VALUE));
    }

    /** 卸载清理：清充能层 / 强化次数 / 移除冷却缩减修饰器 / 清客户端状态。 */
    private static void cleanup(ServerPlayer sp) {
        var pd = sp.getPersistentData();
        pd.remove(PD_CHARGE);
        pd.remove(PD_BOOST);
        var inst = sp.getAttribute(YizAttributes.COOLDOWN_REDUCTION);
        if (inst != null) inst.removeModifier(CDR_BUFF_ID);
        PlayerDataAPI.discard(sp, STATE_KEY);
    }
}
