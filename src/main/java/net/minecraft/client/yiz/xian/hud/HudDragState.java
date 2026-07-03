package net.minecraft.client.yiz.xian.hud;

/**
 * 编辑器内拖拽状态机（轻量，仅持有状态，逻辑在 {@link HudEditorScreen}）。
 *
 * <p>缩放通过滚轮实现（鼠标悬停在元素上滚动），故只需 DRAG 一种模式。</p>
 */
final class HudDragState {

    enum Mode { NONE, DRAG }

    /** 当前选中元素（松手后保留，用于滚轮缩放；点击空白处清空）。 */
    HudElement selected;
    Mode mode = Mode.NONE;

    /** 拖拽偏移：按下时 鼠标 - 元素左上角。 */
    double dragOffX, dragOffY;

    void reset() {
        selected = null;
        mode = Mode.NONE;
    }
}
