package com.zzdzt.arcanemag.enchant;

import java.util.Random;

/**
 * unstable_charge 附魔（不稳定充能）
 *
 * 限定弹匣，属于 CHARGE_MECHANISM 互斥组。
 *
 * 开火命中时充能值乘以随机系数：
 *   5%  供能故障 → -0.5 ~ -0.1×（负充能，抵消已积累充能）
 *  65%  普通     →  0.4 ~  1.0×
 *  25%  正常     →  1.0 ~  1.6×
 *   5%  暴击     →  2.0 ~  3.5×
 *
 * 期望 ≈ 0.9× 正常充能。负充能 clamp 至 0，不产生负充能条。
 */
public class UnstableChargeEnchantment extends AbstractChargePerkEnchantment {

    private static final Random RAND = new Random();

    public UnstableChargeEnchantment() {
        super(Rarity.UNCOMMON);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 10;
    }

    @Override
    public int getMaxCost(int level) {
        return 20;
    }

    /**
     * 随机充能倍率（可负）。
     *
     * 分布：
     *   5%  故障 → [-0.5, -0.1)
     *  65%  普通 → [ 0.4,  1.0)
     *  25%  正常 → [ 1.0,  1.6)
     *   5%  暴击 → [ 2.0,  3.5)
     *
     * 期望 ≈ 0.90
     */
    public static double rollFactor() {
        double roll = RAND.nextDouble();
        if (roll < 0.05) {
            // 5% 供能故障
            return -0.5 + RAND.nextDouble() * 0.4;
        } else if (roll < 0.70) {
            // 65% 普通
            return 0.4 + RAND.nextDouble() * 0.6;
        } else if (roll < 0.95) {
            // 25% 正常
            return 1.0 + RAND.nextDouble() * 0.6;
        } else {
            // 5% 暴击
            return 2.0 + RAND.nextDouble() * 1.5;
        }
    }
}
