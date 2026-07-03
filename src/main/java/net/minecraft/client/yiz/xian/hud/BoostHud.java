package net.minecraft.client.yiz.xian.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.xian.api.BoostData;
import net.minecraft.client.yiz.xian.api.BoostRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 突进 HUD（首个 {@link HudElement}）。
 *
 * <h3>展示规则</h3>
 * <ol>
 *   <li>boosts == 0：不显示任何圈圈（第 1 个突进后台静默恢复）</li>
 *   <li>boosts == 1：显示 1 个圈圈，在其上播放第 2 个突进的恢复动画</li>
 *   <li>boosts == max：显示 max 个圈圈，全部定格满帧</li>
 * </ol>
 * 消耗后瞬间回退：2→1 时只剩 1 个圈且动画从 0 开始，1→0 时全部消失。
 *
 * <p>数据来自通用 {@link BoostData}（与具体来源解耦）。</p>
 *
 * <p>纹理 {@code boost_glyph.png} 为 8×56 纵向 7 帧图集（每帧 8×8，MC 动画纹理标准）。
 * 帧位置由 {@link BoostData#getRegenProgress} 驱动（0.0 → 帧 0，1.0 → 帧 6），
 * 动画速度与当前 provider 的恢复间隔自然绑定。</p>
 *
 * <p>元素以 1:1 绘制 8×8 帧，默认 scale=2.0。</p>
 */
public class BoostHud extends HudElement {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/hud/boost_glyph.png");

    private static final int FRAMES = 7;
    private static final int FRAME = 8;          // 每帧像素（8×8）
    private static final int ATLAS_W = 8;        // 纵向帧条：宽 = 单帧宽
    private static final int ATLAS_H = 56;       // 纵向帧条：高 = 单帧高 × 帧数
    private static final int EDIT_FRAMETIME = 6; // 编辑器预览用固定帧速
    private static final int ICON = 8;           // 单个圈圈逻辑尺寸
    private static final int GAP = 2;            // 圈圈间距

    public BoostHud() {
        super("boost", 100, 80, 2.0f);
    }

    @Override
    public int getLogicalWidth() {
        // 按"最多 3 个圈圈"固定占地，避免 boosts 变化导致命中框抖动
        return BoostData.DEFAULT_MAX * ICON + (BoostData.DEFAULT_MAX - 1) * GAP;
    }

    @Override
    public int getLogicalHeight() {
        return ICON;
    }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        int boosts;
        int max;
        int recoveringFrame;

        if (editMode) {
            // 编辑器内用示例数据和固定帧速预览
            boosts = 2;
            max = BoostData.DEFAULT_MAX;
            long time = mc.level != null ? mc.level.getGameTime() : 0L;
            recoveringFrame = (int) ((time / EDIT_FRAMETIME) % FRAMES);
        } else {
            Player player = mc.player;
            if (player == null) return;
            if (BoostRegistry.getActive(player) == null) return;   // 无突进来源不画
            boosts = BoostData.getBoosts(player);
            max = BoostData.currentMax(player);
            if (boosts == 0) return;   // 第 1 个突进恢复中：不显示任何圈圈，后台静默
            // 恢复动画帧：progress 0.0→帧0，progress 1.0→帧6
            float progress = BoostData.getRegenProgress(player);
            recoveringFrame = Math.min((int) (progress * FRAMES), FRAMES - 1);
        }

        // 仅渲染已恢复的圈圈（boosts 个），最后一个圈圈在 boosts < max 时播放恢复动画
        for (int i = 0; i < boosts; i++) {
            int x = i * (ICON + GAP);
            boolean isLast = (i == boosts - 1);
            boolean needAnim = isLast && boosts < max;
            int frame = needAnim ? recoveringFrame : (FRAMES - 1);
            g.blit(TEX, x, 0, 0, frame * FRAME, FRAME, FRAME, ATLAS_W, ATLAS_H);
        }
    }
}
