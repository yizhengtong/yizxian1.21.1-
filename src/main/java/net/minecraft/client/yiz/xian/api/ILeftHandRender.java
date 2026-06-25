package net.minecraft.client.yiz.xian.api;

/**
 * 左侧展示标记接口。
 *
 * <p>实现此接口的物品自动获得：
 * <ul>
 *   <li>第一人称：双手统一走右手渲染管线，模型出现在屏幕左侧</li>
 *   <li>第三人称：双手统一走左手渲染管线（拔刀姿势）</li>
 * </ul>
 *
 * <p>模型 JSON 只需定义 {@code firstperson_righthand} 和
 * {@code thirdperson_lefthand} 两组 display transforms，
 * 或使用共享父模型 {@code yizxianmod:item/left_hand_held}。</p>
 */
public interface ILeftHandRender {
}
