package com.zzdzt.arcanemag.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 枪械改装充能数据 NBT 读写工具
 *
 * 存储在弹匣 ItemStack 的 NBT 上：
 * - arcanemag:charge              → double, 当前充能值
 * - arcanemag:charge_max          → double, 充能条上限（铭刻时计算）
 * - arcanemag:charge_stacks      → int, 当前释放次数层数
 * - arcanemag:overdrive_stacks   → int, 当前过载层数（0=关闭 1=激活）
 * - arcanemag:overdrive_expire   → long, 过载过期时间（服务端 tick）
 * - arcanemag:overdrive_progress → double, 过载累计溢出进度
 *
 * 机制激活与数值参数由弹匣附魔决定（charge_stacks / charge_passive / charge_overdrive），
 * 本类仅负责运行时状态数据的读写。
 */
public final class ModChargeData {

    private static final String KEY_CHARGE = "arcanemag:charge";
    private static final String KEY_MAX = "arcanemag:charge_max";
    private static final String KEY_STACKS = "arcanemag:charge_stacks";
    private static final String KEY_OVERDRIVE = "arcanemag:overdrive_stacks";
    private static final String KEY_OVERDRIVE_EXPIRE = "arcanemag:overdrive_expire";
    private static final String KEY_OVERDRIVE_PROGRESS = "arcanemag:overdrive_progress";

    private ModChargeData() {}

    // ==================== 充能值 ====================

    public static double getCharge(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0.0 : tag.getDouble(KEY_CHARGE);
    }

    public static void setCharge(ItemStack magazine, double value) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putDouble(KEY_CHARGE, Math.max(0.0, value));
    }

    public static void addCharge(ItemStack magazine, double amount) {
        if (amount <= 0) return;
        double current = getCharge(magazine);
        double max = getMax(magazine);
        setCharge(magazine, Math.min(current + amount, max));
    }

    public static void consumeAll(ItemStack magazine) {
        setCharge(magazine, 0.0);
    }

    // ==================== 充能上限 ====================

    public static double getMax(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0.0 : tag.getDouble(KEY_MAX);
    }

    public static void setMax(ItemStack magazine, double value) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putDouble(KEY_MAX, Math.max(1.0, value));
    }

    public static boolean isFull(ItemStack magazine) {
        double charge = getCharge(magazine);
        double max = getMax(magazine);
        return max > 0.0 && charge >= max;
    }

    public static double getProgress(ItemStack magazine) {
        double max = getMax(magazine);
        if (max <= 0.0) return 0.0;
        return Math.min(1.0, getCharge(magazine) / max);
    }

    // ==================== 释放次数层数 ====================

    public static int getStacks(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0 : tag.getInt(KEY_STACKS);
    }

    public static void setStacks(ItemStack magazine, int value) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putInt(KEY_STACKS, Math.max(0, value));
    }

    public static boolean hasStacks(ItemStack magazine) {
        return getStacks(magazine) > 0;
    }

    public static void consumeStack(ItemStack magazine) {
        int stacks = getStacks(magazine);
        if (stacks > 0) {
            setStacks(magazine, stacks - 1);
        }
    }

    // ==================== 过载状态 ====================

    public static int getOverdriveStacks(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0 : tag.getInt(KEY_OVERDRIVE);
    }

    public static void setOverdriveStacks(ItemStack magazine, int value) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putInt(KEY_OVERDRIVE, Math.max(0, value));
    }

    public static boolean hasOverdrive(ItemStack magazine) {
        return getOverdriveStacks(magazine) > 0;
    }

    public static void activateOverdrive(ItemStack magazine, long expireTick) {
        setOverdriveStacks(magazine, 1);
        setOverdriveExpireTick(magazine, expireTick);
    }

    public static void clearOverdrive(ItemStack magazine) {
        setOverdriveStacks(magazine, 0);
        setOverdriveProgress(magazine, 0.0);
    }

    public static long getOverdriveExpireTick(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0L : tag.getLong(KEY_OVERDRIVE_EXPIRE);
    }

    public static void setOverdriveExpireTick(ItemStack magazine, long tick) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putLong(KEY_OVERDRIVE_EXPIRE, tick);
    }

    public static void resetOverdriveExpire(ItemStack magazine) {
        long expireTick = magazine.getOrCreateTag().getLong(KEY_OVERDRIVE_EXPIRE);
        if (expireTick > 0) {
            setOverdriveExpireTick(magazine, 0L);
        }
    }

    public static double getOverdriveProgress(ItemStack magazine) {
        CompoundTag tag = getTag(magazine);
        return tag == null ? 0.0 : tag.getDouble(KEY_OVERDRIVE_PROGRESS);
    }

    public static void setOverdriveProgress(ItemStack magazine, double value) {
        CompoundTag tag = magazine.getOrCreateTag();
        tag.putDouble(KEY_OVERDRIVE_PROGRESS, Math.max(0.0, value));
    }

    // ==================== 工具方法 ====================

    @Nullable
    private static CompoundTag getTag(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getTag();
    }

    public static void tickOverdriveExpire(ItemStack magazine, long currentTick) {
        long expireTick = getOverdriveExpireTick(magazine);
        if (expireTick > 0 && currentTick > expireTick) {
            clearOverdrive(magazine);
            resetOverdriveExpire(magazine);
            setOverdriveProgress(magazine, 0.0);
        }
    }

    /**
     * 释放法术后清理充能状态
     */
    public static void onSpellCast(ItemStack magazine) {
        if (hasStacks(magazine)) {
            consumeStack(magazine);
        } else {
            consumeAll(magazine);
        }
        clearOverdrive(magazine);
        resetOverdriveExpire(magazine);
    }
}
