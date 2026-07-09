package net.minecraft.client.yiz.xian.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.xian.api.terraria.EffectTag;
import net.minecraft.client.yiz.xian.api.terraria.ExtraJumpData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 跳跃次数 HUD —— 显示玩家当前可用的附加跳总次数（云 + 暴雪 + 沙剩余之和）。
 *
 * <p>每 1 次可用跳跃 = 1 个云朵瓶图标（{@code acc_53.png}）。落地充满、空中消耗一次少一个，
 * 次数为 0 时不显示。用户决策 2026-07-05：统一用云朵瓶图标表示所有附加跳
 * （不按跳类型分图标，后续如需分类型再加）。</p>
 *
 * <p>数据来自 {@link ExtraJumpData#getAvailable}（客户端读服务端同步值）。
 * 参照 {@link BoostHud} 范式。命中框按 4 个图标固定占地，避免次数变化抖动。</p>
 *
 * <p>纹理 {@code acc_53.png} 为 32×32（M0 处理），1:1 blit 后靠默认 scale=0.5 缩到屏幕 16×16，
 * 与突进 HUD 视觉协调。可在 HUD 编辑器（DEL+ALT）里拖动位置 / 调 scale。</p>
 */
public class ExtraJumpHud extends HudElement {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/item/acc_53.png");

    private static final int ICON = 32;        // acc_53.png 原始尺寸（32×32，1:1 blit）
    private static final int GAP = 4;          // 图标间距（逻辑像素）
    private static final int FRAME_BOX = 4;    // 命中框固定按 4 个图标占地

    public ExtraJumpHud() {
        // 默认位置在突进 HUD（100,80）下方，scale 0.5 → 单图标屏幕 16×16
        super("extra_jump", 100, 104, 0.5f);
    }

    @Override
    public int getLogicalWidth() {
        return FRAME_BOX * ICON + (FRAME_BOX - 1) * GAP;
    }

    @Override
    public int getLogicalHeight() {
        return ICON;
    }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        int count;
        if (editMode) {
            count = 2;   // 编辑器内固定画 2 个示例
        } else {
            Player player = mc.player;
            if (player == null) return;
            count = ExtraJumpData.getRemaining(player).stream().mapToInt(Integer::intValue).sum();
            if (count == 0) return;   // 无可用跳跃不显示
        }
        for (int i = 0; i < count; i++) {
            // blit(TEX, destX, destY, srcU, srcV, srcW, srcH, textureW, textureH)
            // 整张 32×32 1:1 绘制（缩放由 HudManager 的 PoseStack scale 处理）
            g.blit(TEX, i * (ICON + GAP), 0, 0, 0, ICON, ICON, ICON, ICON);
        }
    }
}
