package net.minecraft.client.yiz.xian;

import com.mojang.serialization.Codec;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.client.yiz.xian.effect.CriticalStrikeEffect;
import net.minecraft.client.yiz.xian.effect.CriticalStrikeProvider;
import net.minecraft.client.yiz.xian.effect.SharpBladeEffect;
import net.minecraft.client.yiz.xian.item.GeneralItemItem;
import net.minecraft.client.yiz.xian.item.SkillScrollItem;
import net.minecraft.client.yiz.xian.item.TalentCoreItem;
import net.minecraft.client.yiz.xian.item.WeaponCoreItem;
import net.minecraft.client.yiz.xian.realm.BreakthroughHandler;
import net.minecraft.client.yiz.xian.realm.RealmAttributeHandler;
import net.minecraft.client.yiz.xian.realm.RealmStages;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.client.yiz.xian.command.YizxianCommand;

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

    public YizxianMod(IEventBus modEventBus) {
        LOGGER.info("Yiz Xian Mod initializing...");

        // ---- 物品注册 ----
        ITEMS.register(modEventBus);

        // ---- 效果注册（构造函数自动注册到 ModRegistries） ----
        new SharpBladeEffect(5);
        LOGGER.info("SharpBladeEffect registered");
        new CriticalStrikeEffect(3);
        LOGGER.info("CriticalStrikeEffect registered");

        // ---- yiz-qzk integration ----
        PlayerDataAPI.register("yizxgmod:star_body", Codec.BOOL, false);
        PlayerDataAPI.register("yizxgmod:star_level", Codec.intRange(0, 10), 0);
        PlayerDataAPI.register(CriticalStrikeEffect.DATA_TIMER, Codec.intRange(0, 100), 0);
        PlayerDataAPI.register(CriticalStrikeEffect.DATA_TARGET, Codec.STRING, "");

        // ---- 境界跨度 ----
        RealmStages.register();
        RealmAttributeHandler.register();

        RealmProgressionAPI.onBreakthrough((player, stage) -> {
            RealmAttributeHandler.applyAttributes(player);
            LOGGER.info("{} broke through to {}", player.getName().getString(), stage.displayName());
        });

        LOGGER.info("Yiz Xian realm stages registered");

        // ---- 事件 ----
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onLivingDamage);
    }

    /** 玩家攻击拦截 — 会心一击触发 */
    private void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!CriticalStrikeEffect.isReady(player)) return;

        String targetUuid = CriticalStrikeEffect.getTargetUuid(player);
        if (targetUuid.isEmpty()) return;

        Entity targetEntity = player.level().getPlayerByUUID(UUID.fromString(targetUuid));
        if (!(targetEntity instanceof LivingEntity target)) return;
        if (!target.isAlive()) return;

        // 获取原始伤害值
        float baseDamage = event.getOriginalDamage();

        // 修改伤害为2倍
        event.setNewDamage(baseDamage * 2);

        // 突进
        var dir = target.position().subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(1.5));
        player.hurtMarked = true;

        // 额外改血伤害（绕过防御）
        int level = CriticalStrikeEffect.getPlayerLevel(player);
        float bonusDmg = baseDamage * 0.05f * level;
        if (bonusDmg > 0) {
            EntityASMUtil.modifyHealth(target, -bonusDmg);
        }

        // 重置锁定
        CriticalStrikeEffect.reset(player);
        CriticalStrikeProvider.reset(player);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            BreakthroughHandler.checkAndBreakthrough(serverPlayer);
            RealmAttributeHandler.applyHealthRegen(serverPlayer);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            RealmAttributeHandler.applyAttributes(serverPlayer);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        YizxianCommand.register(event.getDispatcher());
    }
}
