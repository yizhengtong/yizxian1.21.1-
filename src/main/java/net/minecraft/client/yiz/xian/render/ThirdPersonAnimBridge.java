package net.minecraft.client.yiz.xian.render;

/** ThreadLocal 桥接：TerraBladeThirdPersonMixin → ItemRendererMixin 传递关键帧数据。 */
public final class ThirdPersonAnimBridge {
    private ThirdPersonAnimBridge() {}

    static final ThreadLocal<float[]> BUF = new ThreadLocal<>();
    static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public static void set(float[] kf) { BUF.set(kf); ACTIVE.set(true); }
    public static float[] get() { return BUF.get(); }
    public static boolean isActive() { return ACTIVE.get(); }
    public static void clear() { BUF.remove(); ACTIVE.set(false); }
}
