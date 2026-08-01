package com.zzdzt.arcanemag.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player 枪械施法法力系数旗标。
 *
 * 在调用 canBeCastedBy 前设置旗标，Mixin 检测到旗标后使用折扣费用检查魔力。
 */
public final class GunCastManaContext {
    private static final Map<UUID, Float> ACTIVE_MULTIPLIERS = new ConcurrentHashMap<>();

    private GunCastManaContext() { }

    public static void begin(UUID playerId, float multiplier) {
        ACTIVE_MULTIPLIERS.put(playerId, multiplier);
    }

    public static void end(UUID playerId) {
        ACTIVE_MULTIPLIERS.remove(playerId);
    }

    /**
     * 获取当前活跃的系数，没有则返回 1.0（无折扣）
     */
    public static float get(UUID playerId) {
        Float v = ACTIVE_MULTIPLIERS.get(playerId);
        return v != null ? v : 1.0f;
    }
}
