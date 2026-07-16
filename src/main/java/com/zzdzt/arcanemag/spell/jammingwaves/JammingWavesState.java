package com.zzdzt.arcanemag.spell.jammingwaves;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * 管理干扰波纹的预热状态
 */
public final class JammingWavesState {
    private static final String PREFIX = ArcaneMag.MODID + ":jamming_waves_";
    private static final String TAG_WARMUP_TICKS = PREFIX + "warmup";
    private static final String TAG_IS_WARMED_UP   = PREFIX + "ready";

    private JammingWavesState() {}

    public static void startWarmup(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        // 避免重复初始化：CONTINUOUS 类型的 onCast 会被多次调用，
        // 如果已经在预热中或已完成，不要重置计数
        if (tag.contains(TAG_WARMUP_TICKS) || tag.getBoolean(TAG_IS_WARMED_UP)) {
            return;
        }
        tag.putInt(TAG_WARMUP_TICKS, 0);
        tag.putBoolean(TAG_IS_WARMED_UP, false);
    }

    public static void tickWarmup(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (tag.getBoolean(TAG_IS_WARMED_UP)) {
            return;
        }

        int current = tag.getInt(TAG_WARMUP_TICKS) + 1;
        tag.putInt(TAG_WARMUP_TICKS, current);

        if (current >= JammingWavesSpell.WARMUP_TICKS) {
            tag.putBoolean(TAG_IS_WARMED_UP, true);
        }
    }

    public static boolean isWarmedUp(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(TAG_IS_WARMED_UP);
    }

    public static float getWarmupProgress(LivingEntity entity) {
        if (isWarmedUp(entity)) return 1.0f;
        return Math.min(1.0f, 
            entity.getPersistentData().getInt(TAG_WARMUP_TICKS) / (float) JammingWavesSpell.WARMUP_TICKS);
    }

    public static void clear(LivingEntity entity) {
        CompoundTag tag = entity.getPersistentData();
        tag.remove(TAG_WARMUP_TICKS);
        tag.remove(TAG_IS_WARMED_UP);
    }
}