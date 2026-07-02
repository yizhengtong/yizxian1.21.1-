package net.minecraft.client.yiz.xian;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.client.yiz.api.CritTracker;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.client.yiz.weapon.StagedWeaponRegistration;
import net.minecraft.client.yiz.weapon.WeaponLevelData;
import net.minecraft.client.yiz.weapon.WeaponProfileRegistry;
import net.minecraft.client.yiz.xian.api.ComboStateMachine;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.api.ILeftHandRender;
import net.minecraft.client.yiz.xian.item.MeleeWeaponItem;
import net.minecraft.client.yiz.xian.item.WeaponItem;

import net.minecraft.client.yiz.xian.effect.CriticalStrikeEffect;
import net.minecraft.client.yiz.xian.effect.CriticalStrikeProvider;
import net.minecraft.client.yiz.xian.effect.SharpBladeEffect;
import net.minecraft.client.yiz.xian.item.GeneralItemItem;
import net.minecraft.client.yiz.xian.item.SkillScrollItem;
import net.minecraft.client.yiz.xian.item.TalentCoreItem;
import net.minecraft.client.yiz.xian.item.MuramasaItem;
import net.minecraft.client.yiz.xian.item.TerraBladeItem;
import net.minecraft.client.yiz.xian.item.TerraprismaScrollItem;
import net.minecraft.client.yiz.xian.item.WeaponCoreItem;
import net.minecraft.client.yiz.xian.realm.BreakthroughHandler;
import net.minecraft.client.yiz.xian.realm.RealmAttributeHandler;
import net.minecraft.client.yiz.xian.realm.RealmStages;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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

    public static final Supplier<Item> TALENT_CORE =
        ITEMS.register("talent_core", TalentCoreItem::new);
    public static final Supplier<Item> SKILL_SCROLL =
        ITEMS.register("skill_scroll", SkillScrollItem::new);
    public static final Supplier<Item> GENERAL_ITEM =
        ITEMS.register("general_item", GeneralItemItem::new);
    public static final Supplier<Item> WEAPON_CORE =
        ITEMS.register("weapon_core", WeaponCoreItem::new);
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

    public YizxianMod(IEventBus modEventBus) {
        LOGGER.info("Yiz Xian Mod initializing...");

        // ---- 物品注册 ----
        ITEMS.register(modEventBus);

        // ---- 创造模式物品栏 ----
        modEventBus.addListener(this::onBuildCreativeTab);

        // ---- 效果注册（构造函数自动注册到 ModRegistries） ----
        new SharpBladeEffect(5);
        LOGGER.info("SharpBladeEffect registered");
        new CriticalStrikeEffect(3);
        LOGGER.info("CriticalStrikeEffect registered");

        // ---- yiz-qzk integration ----
        PlayerDataAPI.register("yizxgmod:star_body", Codec.BOOL, false);
        PlayerDataAPI.register("yizxianmod:attack_anim_index", Codec.INT, 0);
        PlayerDataAPI.register("yizxianmod:combo_step", Codec.INT, -1);
        PlayerDataAPI.register("yizxianmod:combo_tick", Codec.INT, 0);
        PlayerDataAPI.register("yizxgmod:star_level", Codec.intRange(0, 10), 0);
        PlayerDataAPI.register(CriticalStrikeEffect.DATA_TIMER, Codec.intRange(0, 100), 0);
        PlayerDataAPI.register(CriticalStrikeEffect.DATA_TARGET, Codec.STRING, "");

        // ---- 饰品槽数据（服务器持久化 + copyOnDeath + 客户端同步） ----
        AccessoryContainer.registerDataKeys();

        // ---- 境界跨度 ----
        RealmStages.register();
        RealmAttributeHandler.register();

        RealmProgressionAPI.onBreakthrough((player, stage) -> {
            RealmAttributeHandler.applyAttributes(player);
            LOGGER.info("{} broke through to {}", player.getName().getString(), stage.displayName());
        });

        LOGGER.info("Yiz Xian realm stages registered");

        // ---- JSON 热重载（暂时禁用，排查进世界卡住问题） ----
        // NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // ---- 暴击判断提前到 CriticalHitEvent，让原版系统处理倍率+粒子+音效 ----
        NeoForge.EVENT_BUS.addListener(this::onCriticalHit);

        // ---- 事件 ----
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLivingDamage);
    }

    /** 将本模组物品放入创造模式物品栏 */
    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (var s : TERRAPRISMA_SCROLLS) event.accept(s.get());
            for (var s : TERRA_BLADES) event.accept(s.get());
            for (var s : MURAMASAS) event.accept(s.get());
        }
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
    private static final ThreadLocal<Boolean> IN_CRIT = ThreadLocal.withInitial(() -> false);

    /** 会心一击：攻击命中 → 未满充重置 / 满充突进+伤害 */
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
        if (lifeSteal > 0 && event.getNewDamage() > 0
                && !CriticalStrikeEffect.isReady(player)) {
            float healAmount = event.getNewDamage() * (lifeSteal / 100.0f);
            if (healAmount > 0) {
                player.heal(healAmount);
            }
        }

        // ═══ 范围溅射系统 ═══
        float splashRadius = safeAttr(player, YizAttributes.SPLASH_RADIUS)
            + (float) (wld != null ? wld.getExtra("splashRadius") : 0);
        if (splashRadius > 0 && event.getNewDamage() > 0
                && !CriticalStrikeEffect.isReady(player)
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

        // ═══ 原有 CriticalStrikeEffect（计时锁定暴击，与概率暴击独立共存） ═══
        if (IN_SPLASH.get() || IN_CRIT.get()) return;
        if (!CriticalStrikeEffect.isReady(player)) {
            // 未满 2.5 秒就攻击 → 重置蓄力
            int timer = (int) PlayerDataAPI.get(player, CriticalStrikeEffect.DATA_TIMER);
            if (timer > 0) {
                CriticalStrikeEffect.reset(player);
                CriticalStrikeProvider.reset(player);
            }
            return;
        }

        float baseDamage = event.getOriginalDamage();
        event.setNewDamage(0);

        String targetUuid = CriticalStrikeEffect.getTargetUuid(player);
        if (targetUuid.isEmpty()) return;
        Entity lockedTarget = ((ServerLevel) player.level()).getEntity(UUID.fromString(targetUuid));
        if (!(lockedTarget instanceof LivingEntity target) || !target.isAlive()) return;

        IN_CRIT.set(true);
        try {
            executeCrit(player, target, baseDamage);
        } finally {
            IN_CRIT.set(false);
        }
    }

    private void executeCrit(Player player, LivingEntity target, float baseDamage) {
        var dir = target.position().subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(1.5));
        player.hurtMarked = true;

        int level = CriticalStrikeEffect.getPlayerLevel(player);

        if (net.minecraft.client.yiz.xian.render.C2MECompat.LOADED) {
            // C2ME 兼容：合并为 vanilla hurt()，跳过 delta 系统
            float total = baseDamage * 2 + baseDamage * level;
            target.hurt(player.damageSources().playerAttack(player), total);
        } else {
            YizModQZKAPI.trueDamage(target, baseDamage * 2, player);
            EntityASMUtil.modifyHealth(target, -(baseDamage * level));
        }

        CriticalStrikeEffect.reset(player);
        CriticalStrikeProvider.reset(player);
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
                    target.hurt(source, dmg);
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
        BreakthroughHandler.checkAndBreakthrough(serverPlayer);
        RealmAttributeHandler.applyHealthRegen(serverPlayer);
        // 连招 tick 计数
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
        ComboStateMachine.tick(serverPlayer);
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            RealmAttributeHandler.applyAttributes(serverPlayer);
        }
    }

    /** 玩家退出：清理会心一击的运行时状态与修饰符，避免下次登录残留脏数据。 */
    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            CriticalStrikeEffect.reset(serverPlayer);
            // 清理饰品槽单例，防止服务器端容器实例随玩家退出泄漏
            AccessoryContainer.discard(serverPlayer);
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
            CriticalStrikeEffect.reset(player);
            CriticalStrikeProvider.reset(player);
        }
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
}
