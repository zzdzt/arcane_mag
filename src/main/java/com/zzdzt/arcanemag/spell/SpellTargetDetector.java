package com.zzdzt.arcanemag.spell;

/**
 * 法术目标检测器 — 配合 {@code PreCastTargetHelperMixin} 使用。
 *
 * Mixin 拦截 {@code Utils.preCastTargetHelper} 时设置旗标，
 * {@code SpellCastHandler} 在每次 {@code checkPreCastConditions} 前后读取/重置旗标，
 * 以此判断法术是否需要选择目标实体。
 */
public final class SpellTargetDetector {

    private static final ThreadLocal<Boolean> CALLED = ThreadLocal.withInitial(() -> false);

    private SpellTargetDetector() {}

    /** 由 PreCastTargetHelperMixin 的 @Inject 回调调用 */
    public static void markCalled() {
        CALLED.set(true);
    }

    /** @return 当前调用链中 preCastTargetHelper 是否被调用过 */
    public static boolean wasCalled() {
        return CALLED.get();
    }

    /** 重置旗标，每次 checkPreCastConditions 前由 SpellCastHandler 调用 */
    public static void reset() {
        CALLED.set(false);
    }
}
