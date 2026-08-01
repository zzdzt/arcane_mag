package com.zzdzt.arcanemag.api;

import net.minecraft.world.item.ItemStack;

/**
 * 充能修改器接口。
 *
 * 充能类附魔（charge_stacks / charge_passive / charge_overdrive / unstable_charge）
 * 实现此接口并自注册到 {@link ModifierRegistry}，让 core 的充能系统通过注册表
 * 遍历调用，而非硬编码引用具体附魔类。
 *
 * 未来饰品模块也可实现此接口参与充能修改。
 */
public interface ChargeModifier {

    /**
     * 获取弹匣上此 modifier 的等级（0 = 无）。
     */
    int getLevelOnMagazine(ItemStack magazine);

    /**
     * 修改充能获取量（默认不修改）。仅在 level > 0 时调用。
     * 例如 unstable_charge 用此方法对 gain 乘随机倍率。
     */
    default double modifyGain(double gain, int level) {
        return gain;
    }

    /**
     * 是否参与溢出处理。
     * 若返回 true，则 {@link #handleOverflow} 会在充能溢出时被调用。
     * 例如 charge_stacks / charge_overdrive 返回 true。
     */
    default boolean isOverflowHandler() {
        return false;
    }

    /**
     * 处理充能溢出。仅在 isOverflowHandler() == true 且 level > 0 时调用。
     *
     * @param overflowCharge 当前溢出的 charge 值（>= max）
     * @param max            充能上限
     * @param currentTick    当前游戏 tick
     * @return 处理后的 charge 值。返回 < max 表示继续循环尝试其他 handler，
     *         >= max 表示本 handler 无法继续处理（交由后续 handler 或停止）。
     */
    default double handleOverflow(ItemStack magazine, int level,
                                  double overflowCharge, double max, long currentTick) {
        return overflowCharge;
    }

    /**
     * 是否为过载提供者（charge_overdrive）。
     * core 的 applyOverdriveBonus 通过此标记查找过载附魔等级。
     */
    default boolean isOverdrive() {
        return false;
    }

    /**
     * 被动充能每 tick 速率（默认 0）。仅在 level > 0 时调用。
     * 例如 charge_passive 用此方法提供持枪自动回充。
     */
    default double getPassiveRatePerTick(int level) {
        return 0;
    }
}
