package net.minecraft.client.yiz.xian.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
 *  耐久 1200 · 右键消耗 1 耐久 + 5 法力
 *  右键（点击/按住连发，越按越快）发射紫昭明光
 *  客户端连发 handler 检测右键按住，每 N tick 发 C2S 施法请求，服务端权威施法
 * </pre>
 * 伤害为 spell 法强类型，按法强百分比放大。
 */
public class WupinItem extends Item {

    /** 单次施法消耗（连发 C2S 使用） */
    public static final int DURABILITY_COST = 1;
    public static final float MANA_COST = 5f;

    public WupinItem() {
        super(new Properties().stacksTo(1).durability(1200).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 客户端：闪烁提示（施法由连发 handler 通过 C2S 驱动）
            Vec3 launchPos = net.minecraft.client.yiz.xian.core.ZhaoMingLaunchConfig
                    .launchPos(player, player.getLookAngle());
            if (level instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                for (int i = 0; i < 5; i++) {
                    cl.addParticle(net.minecraft.core.particles.ParticleTypes.CRIT,
                        launchPos.x, launchPos.y + 0.3, launchPos.z,
                        (level.random.nextDouble() - 0.5) * 0.08,
                        (level.random.nextDouble() - 0.5) * 0.02,
                        (level.random.nextDouble() - 0.5) * 0.08);
                }
            }
        }
        // consume：不进入持续使用（连发由客户端 handler 接管，避免 1.21.1 onUseTick 不触发问题）
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§d昭明法杖 · 史诗"));
        tooltip.add(Component.literal("§9被动·紫昭明光"));
        tooltip.add(Component.literal("§7右键发射紫昭明光，按住连发越按越快"));
        tooltip.add(Component.literal("§7命中首个目标造成 §f8 §7法伤（法强加成）"));
        tooltip.add(Component.literal("§72 秒未命中自动 24 格寻敌，命中造成 §f4 §7法伤"));
        tooltip.add(Component.literal("§7无敌人则在头顶盘旋 15 秒，消失返还 §f2 §7耐久 + §f10 §7法力"));
        tooltip.add(Component.literal("§8消耗：1 耐久 + 5 法力 / 次"));
    }
}
