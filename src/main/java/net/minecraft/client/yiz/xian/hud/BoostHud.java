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
 * <p>推进次数以横排的圈圈表示，每个圈圈代表一次推进的"充能槽"：
 * <ul>
 *   <li>已恢复完成的圈圈（第 0..boosts-1 个）→ 定格满帧（第 6 帧）</li>
 *   <li>正在恢复的那个（第 boosts 个，若 boosts &lt; max）→ 充能涌动动画</li>
 *   <li>全满（boosts &gt;= max）→ 所有圈圈定格</li>
 * </ul>
 * 数据来自通用 {@link BoostData}（与具体来源解耦）。</p>
 *
 * <p>纹理 {@code boost_glyph.png} 为 8×56 纵向 7 帧图集（每帧 8×8，MC 动画纹理标准）。
 * 元素以 1:1 绘制 8×8 帧，默认 scale=2.0。</p>
 */
public class BoostHud extends HudElement {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/hud/boost_glyph.png");

    private static final int FRAMES = 7;
    private static final int FRAME = 8;          // 每帧像素（8×8）
    private static final int ATLAS_W = 8;        // 纵向帧条：宽 = 单帧宽
    private static final int ATLAS_H = 56;       // 纵向帧条：高 = 单帧高 × 帧数
    private static final int FRAMETIME = 6;      // 充能动画每帧 tick
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

        if (editMode) {
            // 编辑器内用示例数据强制画，无视运行时条件
            boosts = 2;
            max = BoostData.DEFAULT_MAX;
        } else {
            Player player = mc.player;
            if (player == null) return;
            if (BoostRegistry.getActive(player) == null) return;   // 无突进来源不画
            boosts = BoostData.getBoosts(player);
            max = BoostData.currentMax(player);
        }

        // 充能中圈圈的动画帧（按时间从上到下循环）
        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        int chargeFrame = (int) ((time / FRAMETIME) % FRAMES);

        // 第 0..boosts-1 个：已恢复 → 定格满帧；第 boosts 个（若 < max）：正在恢复 → 充能动画
        int slots = Math.min(boosts + 1, max);
        for (int i = 0; i < slots; i++) {
            int x = i * (ICON + GAP);
            int frame = (i < boosts) ? (FRAMES - 1) : chargeFrame;
            // 纵向帧条取第 frame 帧：uOffset=0, vOffset=frame*8
            g.blit(TEX, x, 0, 0, frame * FRAME, FRAME, FRAME, ATLAS_W, ATLAS_H);
        }
    }
}
