package net.minecraft.client.yiz.xian.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD 元素抽象基类。
 *
 * <p>每个 HUD（突进、泰拉棱镜状态等）注册为一个 {@code HudElement}，
 * 由 {@link HudManager} 统一管理位置/缩放/显隐与渲染分发。</p>
 *
 * <h3>坐标契约</h3>
 * <ul>
 *   <li>{@link #render} 在 PoseStack 已 translate(x,y)+scale(s) 的局部坐标系绘制，
 *       起点为 (0,0)，使用 {@link #getLogicalWidth()}/{@link #getLogicalHeight()} 的逻辑尺寸。</li>
 *   <li>不读取自身位置/scale —— 由 {@link HudManager#drawElement} 处理。</li>
 *   <li>{@code editMode=true} 时必须用示例数据强制画出（无视运行时条件），让玩家在编辑器里能拖动。</li>
 * </ul>
 */
public abstract class HudElement {

    private final String id;
    private final int defaultX;
    private final int defaultY;
    private final float defaultScale;

    protected HudElement(String id, int defaultX, int defaultY, float defaultScale) {
        this.id = id;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultScale = defaultScale;
    }

    public String getId() { return id; }
    public int getDefaultX() { return defaultX; }
    public int getDefaultY() { return defaultY; }
    public float getDefaultScale() { return defaultScale; }

    /** scale=1 时的占地宽（逻辑像素），用于命中测试与选中框。 */
    public abstract int getLogicalWidth();

    /** scale=1 时的占地高（逻辑像素）。 */
    public abstract int getLogicalHeight();

    /**
     * 绘制内容（局部坐标系，起点 0,0）。
     *
     * @param g        GuiGraphics
     * @param editMode true=编辑器内，用示例数据强制画
     */
    public abstract void render(GuiGraphics g, boolean editMode);
}
