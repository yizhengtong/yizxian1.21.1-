package net.minecraft.client.yiz.xian.render;

import net.neoforged.fml.ModList;

/**
 * C2ME (Concurrent Chunk Management Engine) 兼容检测。
 *
 * <p>C2ME 使用多线程处理区块操作，其 {@code c2me-worker-*} 线程与
 * {@link net.minecraft.client.yiz.tool.health.EntityASMUtil} 和
 * {@link net.minecraft.client.yiz.tool.health.DirectHealthFallback} 的
 * 反射式实体数据直接操作（遍历/修改 SynchedEntityData DataItem[] 数组）
 * 存在线程安全冲突，击杀实体时可能导致 {@code ConcurrentModificationException}。
 *
 * <p>当 C2ME 加载时，上游代码应跳过 delta 系统的低级别数据操作，
 * 回退到 vanilla {@code hurt()} 路径。
 */
public final class C2MECompat {

    private static final String C2ME_MOD_ID = "c2me";

    /** C2ME 模组是否已加载 */
    public static final boolean LOADED =
        ModList.get().isLoaded(C2ME_MOD_ID);

    private C2MECompat() {}
}
