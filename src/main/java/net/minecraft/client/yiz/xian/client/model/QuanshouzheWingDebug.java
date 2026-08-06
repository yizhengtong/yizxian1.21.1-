package net.minecraft.client.yiz.xian.client.model;

/**
 * 全首者翅膀调试 — 由客户端命令 `/yizxian wingtest <part> <axis> <deg>` 驱动。
 * <p>现场锁定主翼板/翼膜的 x/y/z 旋转角（度），用于确定正确的扇动轴，找到后再固化进 setupAnim。</p>
 */
public final class QuanshouzheWingDebug {

    private QuanshouzheWingDebug() {}

    /** 激活调试时，模型跳过正常翼拍动画，改用下面锁定的角度。 */
    public static boolean active = false;

    /** 主翼板 bone4/bone10 三轴角度（度）。 */
    public static float mainX, mainY, mainZ;
    /** 翼膜 bone5/bone8 三轴角度（度）。 */
    public static float memX, memY, memZ;

    public static void set(String part, String axis, float deg) {
        active = true;
        boolean main = "main".equals(part);
        switch (axis) {
            case "x" -> { if (main) mainX = deg; else memX = deg; }
            case "y" -> { if (main) mainY = deg; else memY = deg; }
            case "z" -> { if (main) mainZ = deg; else memZ = deg; }
        }
    }

    public static void clear() {
        active = false;
        mainX = mainY = mainZ = memX = memY = memZ = 0f;
    }
}
