package net.minecraft.client.yiz.xian;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.client.yiz.api.CritTracker;
import net.minecraft.client.yiz.api.DamageReductionRegistry;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.weapon.StagedWeaponRegistration;
import net.minecraft.client.yiz.weapon.WeaponLevelData;
import net.minecraft.client.yiz.weapon.WeaponProfileRegistry;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.client.yiz.xian.item.MeleeWeaponItem;
import net.minecraft.client.yiz.xian.item.WeaponItem;

import net.minecraft.client.yiz.xian.effect.LockOnHandler;

import net.minecraft.client.yiz.xian.item.AttributeScrollItem;
import net.minecraft.client.yiz.xian.item.MuramasaItem;
import net.minecraft.client.yiz.xian.item.TerraBladeItem;
import net.minecraft.client.yiz.xian.item.TerraprismaScrollItem;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.client.yiz.xian.command.YizxianCommand;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(YizxianMod.MODID)
public class YizxianMod {
    public static final String MODID = "yizxianmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, MODID);

    // JUMP_ATTRIBUTES 组件 + DATA_COMPONENTS 已随 terraria 属性子系统删除（阶段3D）

    public static final Supplier<Item> TIAN_LEI_YIN =
        ITEMS.register("tian_lei_yin", net.minecraft.client.yiz.xian.skill.TianLeiYinItem::new);
    public static final Supplier<Item> BEN_LEI_JI =
        ITEMS.register("ben_lei_ji", net.minecraft.client.yiz.xian.skill.BenLeiJiItem::new);
    public static final Supplier<Item> LEI_MING_DIAN_JIA =
        ITEMS.register("lei_ming_dian_jia", net.minecraft.client.yiz.xian.skill.LeiMingDianJiaItem::new);
    // 鬼索的狂暴之刃 — 普通版 + 光明版
    public static final Supplier<Item> GUINSOO_RAGEBLADE =
        ITEMS.register("guinsoo_rageblade", () -> new net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem(false));
    public static final Supplier<Item> GUINSOO_RAGEBLADE_BRIGHT =
        ITEMS.register("guinsoo_rageblade_bright", () -> new net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem(true));
    // 无尽之刃
    public static final Supplier<Item> INFINITY_EDGE =
        ITEMS.register("infinity_edge", () -> new net.minecraft.client.yiz.xian.item.equipment.InfinityEdgeItem(false));
    public static final Supplier<Item> INFINITY_EDGE_BRIGHT =
        ITEMS.register("infinity_edge_bright", () -> new net.minecraft.client.yiz.xian.item.equipment.InfinityEdgeItem(true));
    // 卢安娜的飓风
    public static final Supplier<Item> RUNAAN_HURRICANE =
        ITEMS.register("runaan_hurricane", () -> new net.minecraft.client.yiz.xian.item.equipment.RunaanHurricaneItem(false));
    public static final Supplier<Item> RUNAAN_HURRICANE_BRIGHT =
        ITEMS.register("runaan_hurricane_bright", () -> new net.minecraft.client.yiz.xian.item.equipment.RunaanHurricaneItem(true));
    // 疾射火炮
    public static final Supplier<Item> RAPID_FIRECANNON =
        ITEMS.register("rapid_firecannon", () -> new net.minecraft.client.yiz.xian.item.equipment.RapidFirecannonItem(false));
    public static final Supplier<Item> RAPID_FIRECANNON_BRIGHT =
        ITEMS.register("rapid_firecannon_bright", () -> new net.minecraft.client.yiz.xian.item.equipment.RapidFirecannonItem(true));
    // 珠光莲花（护手）
    public static final Supplier<Item> JEWELED_LOTUS =
        ITEMS.register("jeweled_lotus", () -> new net.minecraft.client.yiz.xian.item.equipment.JeweledLotusItem(false));
    public static final Supplier<Item> JEWELED_LOTUS_BRIGHT =
        ITEMS.register("jeweled_lotus_bright", () -> new net.minecraft.client.yiz.xian.item.equipment.JeweledLotusItem(true));
    // 卢登的激荡（单版奥恩神器）
    public static final Supplier<Item> LUDENS_ECHO =
        ITEMS.register("ludens_echo", () -> new net.minecraft.client.yiz.xian.item.equipment.LudensEchoItem());

    public static final Supplier<Item> ATTRIBUTE_SCROLL_ITEM =
        ITEMS.register("attribute_scroll", () -> new AttributeScrollItem(new Item.Properties().stacksTo(64)));
    // 泰拉棱镜卷轴 — 5 等级（召唤武器）
    public static final List<Supplier<Item>> TERRAPRISMA_SCROLLS =
        StagedWeaponRegistration.<TerraprismaScrollItem>create(ITEMS, MODID, "terraprisma_scroll", 5)
            .defaultTiers()
            .profile(TerraprismaScrollItem::buildDefault)
            .register(TerraprismaScrollItem::new);
    // 泰拉刃 — 5 等级（近战武器）
    public static final List<Supplier<Item>> TERRA_BLADES =
        StagedWeaponRegistration.<TerraBladeItem>create(ITEMS, MODID, "terra_blade", 5)
            .defaultTiers()
            .profile(TerraBladeItem::buildDefault)
            .register(TerraBladeItem::new);
    // 村正 — 5 等级（近战武器）
    public static final List<Supplier<Item>> MURAMASAS =
        StagedWeaponRegistration.<MuramasaItem>create(ITEMS, MODID, "muramasa", 5)
            .defaultTiers()
            .profile(MuramasaItem::buildDefault)
            .register(MuramasaItem::new);

    // ─── 创造模式标签页（2026-07-07 重构） ────────────────────────────
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final Supplier<CreativeModeTab> tab(String id, String titleKey, java.util.function.Supplier<Item> icon, java.util.function.Consumer<CreativeModeTab.Output> filler) {
        return CREATIVE_MODE_TABS.register(id, () -> CreativeModeTab.builder()
            .title(Component.translatable(titleKey))
            .icon(() -> new ItemStack(icon.get()))
            .displayItems((params, output) -> filler.accept(output))
            .build());
    }

    /** 注册某物品的默认最大堆叠数（仅当用户未自定义时生效）。 */
    private static void registerDefaultStackSize(String itemId, int size) {
        try {
            net.minecraft.client.yiz.core.ItemStackSizeOverride.setIfAbsent(
                ResourceLocation.parse(itemId), size);
        } catch (Throwable t) {
            LOGGER.warn("Failed to register default stack size for {}: {}", itemId, t.getMessage());
        }
    }

    /** 近战武器 */
    public static final Supplier<CreativeModeTab> MELEE_TAB = tab("melee", "itemGroup.yizxianmod.melee",
        () -> TERRA_BLADES.get(0).get(), o -> {
            for (var s : TERRA_BLADES) o.accept(s.get());
            for (var s : MURAMASAS) o.accept(s.get());
        });

    /** 召唤物 */
    public static final Supplier<CreativeModeTab> SUMMON_TAB = tab("summon", "itemGroup.yizxianmod.summon",
        () -> TERRAPRISMA_SCROLLS.get(0).get(), o -> {
            for (var s : TERRAPRISMA_SCROLLS) o.accept(s.get());
        });

    /** 装备 */
    public static final Supplier<CreativeModeTab> EQUIPMENT_TAB = tab("equipment", "itemGroup.yizxianmod.equipment",
        GUINSOO_RAGEBLADE, o -> {
            o.accept(GUINSOO_RAGEBLADE.get());
            o.accept(GUINSOO_RAGEBLADE_BRIGHT.get());
            o.accept(INFINITY_EDGE.get());
            o.accept(INFINITY_EDGE_BRIGHT.get());
            o.accept(RUNAAN_HURRICANE.get());
            o.accept(RUNAAN_HURRICANE_BRIGHT.get());
            o.accept(RAPID_FIRECANNON.get());
            o.accept(RAPID_FIRECANNON_BRIGHT.get());
            o.accept(JEWELED_LOTUS.get());
            o.accept(JEWELED_LOTUS_BRIGHT.get());
            o.accept(LUDENS_ECHO.get());
        });

    /** 技能 */
    public static final Supplier<CreativeModeTab> SKILL_TAB = tab("skill", "itemGroup.yizxianmod.skill",
        BEN_LEI_JI, o -> { o.accept(BEN_LEI_JI.get()); o.accept(LEI_MING_DIAN_JIA.get()); });

    /** 被动 */
    public static final Supplier<CreativeModeTab> PASSIVE_TAB = tab("passive", "itemGroup.yizxianmod.passive",
        TIAN_LEI_YIN, o -> { o.accept(TIAN_LEI_YIN.get()); });

    /** 属性卷轴 */
    public static final Supplier<CreativeModeTab> ATTR_SCROLL_TAB = tab("attr_scroll", "itemGroup.yizxianmod.attr_scroll",
        ATTRIBUTE_SCROLL_ITEM, o -> {
            for (String attrId : AttributeScrollItem.ATTRIBUTES.keySet()) {
                o.accept(AttributeScrollItem.createPlus(attrId));
                o.accept(AttributeScrollItem.createMinus(attrId));
            }
        });

    // ── 辅助物品 ──────────────────────────────────────────────

    public static final Supplier<Item> BRIGHT_ENDER_EYE =
        ITEMS.register("bright_ender_eye", () -> new Item(new Item.Properties()));
    public static final Supplier<Item> BRIGHT_COMPASS =
        ITEMS.register("bright_compass", () -> new net.minecraft.client.yiz.xian.item.BrightCompassItem(new Item.Properties().stacksTo(1)));
    /** 堆叠核心：铁砧左槽放目标物品、右槽放本物品，取出后该物品ID最大堆叠数×2（最多2次，封顶99） */
    public static final Supplier<Item> STACK_CORE =
        ITEMS.register("stack_core", () -> new Item(new Item.Properties().stacksTo(64)));

    /** 辅助物 */
    public static final Supplier<CreativeModeTab> AUXILIARY_TAB = tab("auxiliary", "itemGroup.yizxianmod.auxiliary",
        BRIGHT_ENDER_EYE, o -> {
            o.accept(BRIGHT_ENDER_EYE.get());
            o.accept(BRIGHT_COMPASS.get());
            o.accept(STACK_CORE.get());
        });


    public YizxianMod(IEventBus modEventBus) {
        LOGGER.info("Yiz Xian Mod initializing...");

        // ---- 物品注册 ----
        ITEMS.register(modEventBus);

        // ---- 创造模式标签页 ----
        CREATIVE_MODE_TABS.register(modEventBus);

        // ---- 容器 Menu 注册（光明指南针等）----
        net.minecraft.client.yiz.xian.menu.YizxianMenus.register(modEventBus);

        // ---- 网络包：属性卷轴 ----（SyncAccessoryPayload 已随饰品槽删除）
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var registrar = event.registrar(MODID);
            // 客户端 → 服务端：属性卷轴应用请求
            registrar.playToServer(
                net.minecraft.client.yiz.xian.network.C2SAttributeApplyPayload.TYPE,
                net.minecraft.client.yiz.xian.network.C2SAttributeApplyPayload.STREAM_CODEC,
                net.minecraft.client.yiz.xian.network.C2SAttributeApplyPayload::handle
            );
            // 客户端 → 服务端：光明指南针工作槽放入/移除
            registrar.playToServer(
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.TYPE,
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload.STREAM_CODEC,
                net.minecraft.client.yiz.xian.network.C2SLightCompassWorkSlotPayload::handle
            );
            // 服务端 → 客户端：连招动画索引（攻击事件驱动，取代 combo 每 tick 全量同步）
            registrar.playToClient(
                net.minecraft.client.yiz.xian.network.S2CComboAnimPayload.TYPE,
                net.minecraft.client.yiz.xian.network.S2CComboAnimPayload.STREAM_CODEC,
                net.minecraft.client.yiz.xian.network.S2CComboAnimPayload::handle
            );
        });

        // ---- 创造模式物品栏 ----



        // ---- yiz-qzk integration ----
        PlayerDataAPI.register("yizxgmod:star_body", Codec.BOOL, false);

        // ---- 物品堆叠数默认配置（三类药水 + 桶类 + 附魔书默认 16）----
        // setIfAbsent：用户用 /yiz stack set 自定义过的不会被覆盖；reset 删除后重启回到此默认值。
        registerDefaultStackSize("minecraft:potion", 16);            // 可饮用药水
        registerDefaultStackSize("minecraft:splash_potion", 16);     // 喷溅药水
        registerDefaultStackSize("minecraft:lingering_potion", 16);  // 滞留药水
        // 桶类（原版全部 11 种）：空桶 + 各液体/生物桶，统一默认 16
        registerDefaultStackSize("minecraft:bucket", 16);
        registerDefaultStackSize("minecraft:water_bucket", 16);
        registerDefaultStackSize("minecraft:lava_bucket", 16);
        registerDefaultStackSize("minecraft:milk_bucket", 16);
        registerDefaultStackSize("minecraft:powder_snow_bucket", 16);
        registerDefaultStackSize("minecraft:cod_bucket", 16);
        registerDefaultStackSize("minecraft:salmon_bucket", 16);
        registerDefaultStackSize("minecraft:pufferfish_bucket", 16);
        registerDefaultStackSize("minecraft:tropical_fish_bucket", 16);
        registerDefaultStackSize("minecraft:axolotl_bucket", 16);
        registerDefaultStackSize("minecraft:tadpole_bucket", 16);
        registerDefaultStackSize("minecraft:enchanted_book", 16);   // 附魔书
        // combo_step/combo_tick/attack_anim_index 已移除：连招改纯内存 + S2CComboAnimPayload 事件下发，
        // 不再走 PlayerDataAPI（原实现每 tick 全量同步整个玩家数据 root 到客户端）。
        PlayerDataAPI.register("yizxgmod:star_level", Codec.intRange(0, 10), 0);
        // 光明指南针工作槽（3 个 Item 注册表 ID，-1 表空位；绑玩家持久化）
        PlayerDataAPI.register("yizxianmod:light_compass_work_slots",
            Codec.INT.listOf(), java.util.List.of());
        // 天雷引充能状态（服务端写，客户端 ChargeHud 读）：{charge, boost}
        PlayerDataAPI.register("yizxianmod:tianleiyin_state", Codec.STRING, "{}");
        // Guinsoo 叠层（服务端写，客户端 BuffHud 读）：6 槽层数，逗号分隔
        PlayerDataAPI.register("yizxianmod:guinsoo_stacks", Codec.STRING, "0,0,0,0,0,0");

        // 饰品槽系统(AccessoryContainer) + terraria 减伤回调已随 terraria 子系统删除（阶段3D）
        // 装备减伤现由 yizmodqzk DAMAGE_REDUCTION 属性 + LivingEntityMixin.modifyHealthForHealBan 接管

        // ---- JSON 热重载（暂时禁用，排查进世界卡住问题） ----
        // NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // ---- 暴击判断提前到 CriticalHitEvent，让原版系统处理倍率+粒子+音效 ----
        NeoForge.EVENT_BUS.addListener(this::onCriticalHit);

        // ---- 堆叠核心：铁砧强化物品最大堆叠数 ----
        NeoForge.EVENT_BUS.register(net.minecraft.client.yiz.xian.item.StackCoreAnvilHandler.class);

        // ---- 事件 ----
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);

        // 心之翅/突进服务端(BoostHandler)已删除（阶段3C）
        // 附加跳服务端落地充能已由 yizmodqzk MultiJumpRechargeHandler 接管
        // 装备保护态(AccessoryProtectionHandler 闪避/无敌帧)已删除（阶段3D）—— 由 yizmodqzk AttackInvulnerabilityTracker 接管

        // 锁定系统：属性驱动充能 + 距离延伸
        NeoForge.EVENT_BUS.addListener(LockOnHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LockOnHandler::onLivingDamagePre);
    }

    /**
     * 暴击判定前置到 CriticalHitEvent，让原版系统处理倍率+粒子+音效。
     * 其他模组（如伤害显示）能正确检测到暴击并标红。
     */
    private void onCriticalHit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // 记录原版暴击（供吸血/溅射/CS 读取）
        CritTracker.mark(player, event.isCriticalHit());

        // 原版已经是暴击则不干预
        if (event.isCriticalHit()) return;

        // 读取暴击率/暴伤：NeoForge Attribute + 手持武器 Profile
        WeaponLevelData wld = getWeaponLevelData(player.getMainHandItem());
        float critRate = safeAttr(player, YizAttributes.CRIT_RATE)
            + (float) (wld != null ? wld.stats().critRate() : 0);
        float critDmg = safeAttr(player, YizAttributes.CRIT_DAMAGE)
            + (float) (wld != null ? wld.getExtra("critDmg") : 0);

        // 自定义概率暴击
        if (critRate > 0 && Math.random() < critRate / 100.0) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(1.5f + critDmg / 100.0f);
        }
    }

    /** 防止溅射伤害递归触发自身 */
    private static final ThreadLocal<Boolean> IN_SPLASH = ThreadLocal.withInitial(() -> false);

    /** 攻击命中：吸血 + 溅射 + 锁定系统已迁至 LockOnHandler */
    private void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // 溅射递归保护
        if (IN_SPLASH.get()) return;

        // ═══ 手持武器 Profile 数据（补充 NeoForge Attribute 不到的武器固有属性） ═══
        WeaponLevelData wld = getWeaponLevelData(player.getMainHandItem());

        // 暴击已在 CriticalHitEvent 中处理，此处只消费标记防止泄漏
        CritTracker.consume(player);

        // ═══ 吸血系统 ═══
        float lifeSteal = safeAttr(player, YizAttributes.LIFE_STEAL)
            + (float) (wld != null ? wld.getExtra("lifeSteal") : 0);
        if (lifeSteal > 0 && event.getNewDamage() > 0) {
            float healAmount = event.getNewDamage() * (lifeSteal / 100.0f);
            if (healAmount > 0) {
                player.heal(healAmount);
            }
        }

        // ═══ 范围溅射系统 ═══
        float splashRadius = safeAttr(player, YizAttributes.SPLASH_RADIUS)
            + (float) (wld != null ? wld.getExtra("splashRadius") : 0);
        if (splashRadius > 0 && event.getNewDamage() > 0
                && event.getEntity() instanceof LivingEntity primaryTarget) {
            float splashDmgPct = safeAttr(player, YizAttributes.SPLASH_DAMAGE)
                + (float) (wld != null ? wld.getExtra("splashDmg") : 0);
            float splashFalloff = safeAttr(player, YizAttributes.SPLASH_FALLOFF)
                + (float) (wld != null ? wld.getExtra("splashFalloff") : 0);
            if (splashDmgPct > 0) {
                executeSplash(player, primaryTarget, event.getNewDamage(),
                    splashRadius, splashDmgPct, splashFalloff, event.getSource());
            }
        }

    }

    /**
     * 范围溅射伤害：以被命中目标为中心，对范围内有效实体造成伤害。
     *
     * <h3>目标判定</h3>
     * <ul>
     * <li>敌对生物（{@link Monster}）始终命中</li>
     * <li>与主目标同类型的实体始终命中（如主目标是史莱姆，其他史莱姆也被命中）</li>
     * <li>其他非敌对、非同类型实体不受伤害</li>
     * </ul>
     *
     * <h3>衰减公式（平滑二次曲线）</h3>
     * <pre>
     *   t = distance / radius
     *   edgeMul = 1.0 - falloff / 100.0
     *   multiplier = edgeMul + (1.0 - edgeMul) * (1.0 - t²)
     *   splashDmg = baseDamage * (splashDmgPct / 100.0) * multiplier
     * </pre>
     *
     * @param player       攻击者
     * @param primaryTarget 被命中的主目标
     * @param baseDamage   有效伤害值
     * @param radius       溅射半径（格）
     * @param splashPct    溅射伤害百分比（0~100）
     * @param falloff      衰减强度（0~100）
     */
    private void executeSplash(Player player, LivingEntity primaryTarget, float baseDamage,
                               float radius, float splashPct, float falloff,
                               net.minecraft.world.damagesource.DamageSource source) {
        AABB box = primaryTarget.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = primaryTarget.level().getEntitiesOfClass(
            LivingEntity.class, box,
            e -> e != player && e != primaryTarget && e.isAlive()
                 && isValidSplashTarget(e, primaryTarget));

        IN_SPLASH.set(true);
        try {
            for (LivingEntity target : nearby) {
                double dist = primaryTarget.position().distanceTo(target.position());
                float t = (float) Math.min(dist / radius, 1.0);
                float edgeMul = 1.0f - falloff / 100.0f;
                float smoothMul = edgeMul + (1.0f - edgeMul) * (1.0f - t * t);
                float dmg = baseDamage * (splashPct / 100.0f) * smoothMul;
                if (dmg > 0) {
                    // 用无直接实体的 DamageSource 防止 modifyHurtAmount 二次放大
                    target.hurt(new net.minecraft.world.damagesource.DamageSource(
                        source.typeHolder(), null, player), dmg);
                }
            }
        } finally {
            IN_SPLASH.set(false);
        }
    }

    /** 判定候选实体是否应受到溅射伤害。 */
    private static boolean isValidSplashTarget(LivingEntity candidate, LivingEntity primaryTarget) {
        if (candidate instanceof Monster) return true;
        return candidate.getClass() == primaryTarget.getClass();
    }

    /** 安全读取属性值，不存在时返回 0。 */
    private static float safeAttr(LivingEntity entity, Holder<Attribute> attr) {
        var inst = entity.getAttribute(attr);
        return inst != null ? (float) inst.getValue() : 0f;
    }

    /** 从物品栈提取武器等级数据（支持 MeleeWeaponItem 和 WeaponItem 两棵树）。 */
    @Nullable
    private static WeaponLevelData getWeaponLevelData(ItemStack stack) {
        if (stack.getItem() instanceof MeleeWeaponItem mwi) return mwi.getLevelData();
        if (stack.getItem() instanceof WeaponItem wi) return wi.getLevelData();
        return null;
    }

    /** 上一 tick 各玩家的主手物品栈，用于检测「左手武器之间」的切换以重置连招。 */
    private static final java.util.WeakHashMap<UUID, ItemStack> LAST_MAIN_HAND = new java.util.WeakHashMap<>();

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        // 雷鸣电甲开关形 tick（与武器持有无关，放最前）
        net.minecraft.client.yiz.xian.skill.LeiMingDianJiaItem.onTick(serverPlayer);
        // 卢登激荡：延迟溅射 tick（按维度处理到期连锁）
        net.minecraft.client.yiz.xian.item.equipment.LudensEchoItem.tick(serverPlayer.serverLevel());
        // 连招切手检测（纯内存 reset，无 tick 计数 —— 超时由 onAttack 惰性判断）
        ItemStack held = serverPlayer.getMainHandItem();
        UUID puid = serverPlayer.getUUID();
        if (!(held.getItem() instanceof ILeftHandRender)) {
            ComboStateMachine.reset(serverPlayer);
            LAST_MAIN_HAND.remove(puid);
            return;
        }
        // 两把不同的「左手武器」之间切换时重置连招（如 TerraBlade → Muramasa），
        // 避免连招步骤错误延续到新武器上。
        ItemStack prev = LAST_MAIN_HAND.get(puid);
        if (prev != null && !ItemStack.isSameItemSameComponents(prev, held)) {
            ComboStateMachine.reset(serverPlayer);
        }
        LAST_MAIN_HAND.put(puid, held.copy());
    }

    // onPlayerLogin/onPlayerRespawn/syncAccessoryToClient 已随饰品槽系统删除（阶段3D）

    /** 玩家退出：清理会心一击的运行时状态与修饰符，避免下次登录残留脏数据。 */
    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            LockOnHandler.onPlayerLogout(serverPlayer);
            // 连招纯内存状态清理（防 Map 内存泄漏）
            ComboStateMachine.clear(serverPlayer.getUUID());
        }
    }

    /** 玩家重生：清空装备叠层（鬼索等），避免死亡残留。 */
    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof Player player) {
            net.minecraft.client.yiz.xian.item.equipment.GuinsooRagebladeItem.onPlayerDeath(player);
        }
    }

    /**
     * 玩家死亡：清理会心一击状态。
     * <p>前置库的玩家数据附件配置了 {@code copyOnDeath()}，会导致 {@code crit_timer}/{@code crit_target}
     * 重生后原样保留为满值，但 {@code entity_crit_range} 修饰符随实体重建丢失，
     * 造成「timer 卡满、修饰符不存在」的状态不一致。在此统一重置。
     */
    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            LockOnHandler.onPlayerDeath(player);
        }
        // 卢登的激荡：玩家击杀非玩家目标时溅射
        net.minecraft.client.yiz.xian.item.equipment.LudensEchoItem.onLivingDeath(event);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        YizxianCommand.register(event.getDispatcher());
    }

    // ── JSON 热重载 ──

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        Path configDir = Path.of("config", MODID, "weapons");
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(
                    PreparationBarrier barrier,
                    ResourceManager resourceManager,
                    ProfilerFiller preparationsProfiler,
                    ProfilerFiller reloadProfiler,
                    Executor backgroundExecutor,
                    Executor gameExecutor) {
                return CompletableFuture.runAsync(() -> {
                    WeaponProfileRegistry.reload(
                        WeaponProfileRegistry.loadFromJson(configDir, MODID));
                }, backgroundExecutor);
            }

            @Override
            public String getName() {
                return MODID + ":weapon_profile_reload";
            }
        });
        LOGGER.info("Weapon profile JSON reload listener registered");
    }

    // applyAccessoryArmor/applyAccessoryRegen/registerEditorEffectTags 已随 terraria 子系统删除（阶段3D）
    // 装备 ARMOR/LIFE_REGEN 现由 yizmodqzk YizAttributes + tizMod.mirrorArmor/AttributeEffectTicker 接管
}
