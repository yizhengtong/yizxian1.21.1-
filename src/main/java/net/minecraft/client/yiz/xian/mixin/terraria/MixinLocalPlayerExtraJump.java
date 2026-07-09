package net.minecraft.client.yiz.xian.mixin.terraria;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.xian.api.AccessoryContainer;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.client.yiz.xian.item.HeartWingsItem;
import net.minecraft.client.yiz.xian.network.C2SExtraJumpPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * 客户端跳跃分发 —— 注入 {@code LocalPlayer.aiStep()} HEAD，统一处理一次跳跃键点击
 * （{@code keyJump.consumeClick()}），按优先级决定走心之翅展翅 还是 多段跳。
 *
 * <h3>为什么要"统一分发"（2026-07-05 重构）</h3>
 * <p>原来心之翅展翅和多段跳分别在两个 HEAD Mixin 里各自检测跳跃键，出现两个问题：</p>
 * <ol>
 *   <li><b>边沿检测失效</b>：用 {@code isDown()} 自记上 tick 状态时，玩家按住空格起跳后
 *       {@code isDown()} 持续 true，{@code lastJump} 永远等于 {@code nowJump}，边沿永不产生
 *       → 多段跳完全无法触发。</li>
 *   <li><b>双 Mixin 抢 click</b>：改用 {@code consumeClick()} 后，两个 Mixin 各自消费同一次
 *       点击，先跑的 Mixin 消费后后跑的 Mixin 拿不到 → 行为依赖未定义的 Mixin 顺序。</li>
 * </ol>
 * <p>合并到一个 Mixin、用一次 {@code consumeClick}、内部按优先级分发，根治两个问题。</p>
 *
 * <h3>手感：点按一次跳一次</h3>
 * <p>用 {@code keyJump.consumeClick()} —— 每次物理按下消费一次 click，对应"点按一次 = 一次动作"。
 * MC 1.21 的 {@code KeyMapping} 在 GLFW_REPEAT（按住期间）<b>不</b>累加 clickCount，所以按住
 * 不会持续触发。服务端消耗权威（次数有限/冷却兜底），双保险。</p>
 *
 * <h3>优先级（用户决策 2026-07-05：心之翅优先级高于多段跳）</h3>
 * <ol>
 *   <li><b>心之翅展翅</b>（优先）：装备心之翅（饰品槽）且满足原版鞘翅展开条件 → 展翅</li>
 *   <li><b>多段跳</b>（备胎）：未装备心之翅 + 有可用跳槽 → 消耗一次多段跳</li>
 * </ol>
 *
 * <h3>充能/消耗分工</h3>
 * <ul>
 *   <li>落地充能：服务端 {@code ExtraJumpHandler} 权威 —— {@code LivingFallEvent}（正常摔落）
 *       + {@code isFallFlying()} 下降沿（心之翅/鞘翅平滑落地 fallDistance=0 兜底）充满，
 *       客户端经 SyncPlayerDataPayload 同步。</li>
 *   <li>空中消耗：本 Mixin 乐观预测 + 发包，服务端权威消耗纠正客户端。</li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerExtraJump {

    /**
     * 多段跳内置 CD 计数（客户端本地，按 LocalPlayer 实例记录）。
     * <p>防长按跳跃键快速消耗全部次数：每次多段跳后进入 {@code ExtraJumpData.JUMP_COOLDOWN_TICKS}
     * （5 tick）冷却，CD 中即使 consumeClick 拿到点击也不触发。心之翅展翅不受此 CD 限制。</p>
     * <p>用客户端本地计数而非网络同步值，避免同步延迟导致的"前几跳刷屏"。
     * 服务端次数有限（落地恢复）是最终兜底。</p>
     */
    private static final WeakHashMap<LocalPlayer, Integer> JUMP_CD = new WeakHashMap<>();

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void yizxian$onJumpClick(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        // 先递减本 tick 的多段跳 CD（无论是否点击）
        int cd = JUMP_CD.getOrDefault(self, 0);
        if (cd > 0) JUMP_CD.put(self, cd - 1);

        // 点按一次 = 一次跳跃意图。MC 1.21 按住期间不会持续累加 clickCount
        if (!Minecraft.getInstance().options.keyJump.consumeClick()) return;

        // 落地按下跳跃键由原版走 jumpFromGround 起跳；本 Mixin 只处理"空中再跳"
        if (self.onGround()) return;
        // 已在飞行（鞘翅滑翔中）不再触发 —— 飞行中的突进由 BoostHandler 走 TAB 键
        if (self.isFallFlying()) return;
        if (self.isInWater()) return;        // 游泳上浮走原版
        if (self.isPassenger()) return;      // 骑乘
        if (self.hasEffect(MobEffects.LEVITATION)) return;

        // ── 优先级①：心之翅展翅（饰品槽有心之翅，不受多段跳 CD 限制） ──
        // 胸甲槽直接是鞘翅/心之翅物品时，原版 aiStep 自己会处理，不干预
        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        boolean vanillaHandlesChest = chest.is(Items.ELYTRA) || chest.getItem() instanceof HeartWingsItem;
        if (!vanillaHandlesChest && AccessoryContainer.hasHeartWings(self)) {
            self.startFallFlying();
            self.connection.send(new ServerboundPlayerCommandPacket(
                self, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            return;
        }

        // ── 优先级②：多段跳（有可用跳槽） ──
        // 多段跳内置 CD：CD 中不触发（consumeClick 已消费清积压，避免 CD 结束瞬间连发）。
        // 心之翅分支不受 CD（上面已 return）。
        if (JUMP_CD.getOrDefault(self, 0) > 0) return;

        int slot = ExtraJumpData.pickNextSlot(self);
        if (slot < 0) return;

        // 乐观预测：用该槽饰品的 JUMP_HEIGHT 反解 Y 初速（手感即时）。服务端权威消耗纠正
        int height = ExtraJumpData.jumpHeightOfSlot(self, slot);
        double vy = ExtraJumpData.velocityFromHeight(height);
        self.setDeltaMovement(self.getDeltaMovement().x, vy, self.getDeltaMovement().z);
        // 不归零 fallDistance：本下落从最高点累计，落地按常驻安全/减免计算（见 MixinExtraJumpFallDamage）
        self.hurtMarked = true;
        PacketDistributor.sendToServer(new C2SExtraJumpPayload());

        // 进入多段跳内置 CD（防长按快速消耗）
        JUMP_CD.put(self, ExtraJumpData.JUMP_COOLDOWN_TICKS);
    }
}
