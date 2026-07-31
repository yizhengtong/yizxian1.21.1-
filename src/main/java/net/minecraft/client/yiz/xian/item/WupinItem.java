package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.tool.health.ManaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 昭明法杖 — 史诗魔法武器。
 * <pre>
 *  耐久 1200 · 右键消耗 4 耐久 + 15 法力
 *  向前方发射紫昭明光（直线无重力 15 格/秒，2 秒）
 *  路径碰到第 1 个实体 → 8 法伤 → 消失
 *  2 秒未命中 / 撞方块 → 24 格寻敌（最近），接触 → 4 法伤 → 消失
 *  无敌人 → 头顶盘旋跟随 15 秒（期间可再寻敌），自然消失返还 2 耐久 + 10 法力
 * </pre>
 * 伤害为 spell 法强类型，按法强百分比放大。正式发射特效后续再写。
 */
public class WupinItem extends Item {

    /** 单次施法消耗 */
    private static final int DURABILITY_COST = 4;
    private static final float MANA_COST = 15f;

    public WupinItem() {
        super(new Properties().stacksTo(1).durability(1200).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // 法力不足则失败（不消耗耐久、不发射）
            if (!ManaTracker.consume(player, MANA_COST)) {
                player.displayClientMessage(Component.literal("§c法力不足"), true);
                return InteractionResultHolder.fail(stack);
            }
            // 消耗耐久
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(DURABILITY_COST, player,
                        LivingEntity.getSlotForHand(hand));
            }
            // 发射紫昭明光（服务端权威：伤害/命中/返还）
            net.minecraft.client.yiz.xian.fx.ZhaoMingLightManager.getInstance()
                    .add(player, player.getLookAngle());
        } else {
            // 客户端本地预测（立即显示；S2C 到达后由服务端 id 接管校准）
            net.minecraft.client.yiz.xian.render.ZhaoMingLightClientManager.getInstance()
                    .add(player, player.getLookAngle());
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§d昭明法杖 · 史诗"));
        tooltip.add(Component.literal("§9被动·紫昭明光"));
        tooltip.add(Component.literal("§7右键发射紫昭明光，直线飞行"));
        tooltip.add(Component.literal("§7命中首个目标造成 §f8 §7法伤（法强加成）"));
        tooltip.add(Component.literal("§72 秒未命中自动 24 格寻敌，命中造成 §f4 §7法伤"));
        tooltip.add(Component.literal("§7无敌人则在头顶盘旋 15 秒，消失返还 §f2 §7耐久 + §f10 §7法力"));
        tooltip.add(Component.literal("§8消耗：4 耐久 + 15 法力 / 次"));
    }
}
