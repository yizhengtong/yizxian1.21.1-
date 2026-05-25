package net.minecraft.client.yiz.xian;

import com.mojang.serialization.Codec;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.api.YizModQZKAPI;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onLeftClickEmpty);
    }

    /** 会心一击触发：命中实体 → 拦截重定向到锁定目标 */
    private void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (!CriticalStrikeEffect.isReady(player)) return;

        String targetUuid = CriticalStrikeEffect.getTargetUuid(player);
        if (targetUuid.isEmpty()) return;

        Entity targetEntity = ((ServerLevel) player.level()).getEntity(UUID.fromString(targetUuid));
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) return;

        float baseDamage = event.getOriginalDamage();
        event.setNewDamage(0);
        executeCrit(player, target, baseDamage);
    }

    /** 会心一击触发：攻击实体 → 取消原攻击，转向锁定目标 */
    private void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!CriticalStrikeEffect.isReady(event.getEntity())) return;

        String targetUuid = CriticalStrikeEffect.getTargetUuid(event.getEntity());
        if (targetUuid.isEmpty()) return;
        Entity e = ((ServerLevel) event.getEntity().level()).getEntity(UUID.fromString(targetUuid));
        if (!(e instanceof LivingEntity target) || !target.isAlive()) return;

        event.setCanceled(true);
        float baseDmg = (float) event.getEntity().getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        executeCrit(event.getEntity(), target, baseDmg);
    }

    /** 会心一击触发：空砍 → 转向锁定目标 */
    private void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CriticalStrikeEffect.isReady(player)) return;

        String targetUuid = CriticalStrikeEffect.getTargetUuid(player);
        if (targetUuid.isEmpty()) return;
        Entity e = ((ServerLevel) player.level()).getEntity(UUID.fromString(targetUuid));
        if (!(e instanceof LivingEntity target) || !target.isAlive()) return;

        float baseDmg = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        executeCrit(player, target, baseDmg);
    }

    private void executeCrit(Player player, LivingEntity target, float baseDamage) {
        var dir = target.position().subtract(player.position()).normalize();
        player.setDeltaMovement(dir.scale(1.5));
        player.hurtMarked = true;

        // 视线转向目标
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

        int level = CriticalStrikeEffect.getPlayerLevel(player);
        YizModQZKAPI.trueDamage(target, baseDamage * 2, player);
        EntityASMUtil.modifyHealth(target, -(baseDamage * level));

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
