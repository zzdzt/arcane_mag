package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.utils.ModChargeData;
import net.minecraft.world.item.ItemStack;

/**
 * charge_overdrive 附魔（过载）
 *
 * 充能满后继续命中，累计溢出达到阈值后激活过载状态，
 * 下次施法获得法术强度加成。
 * 附魔等级 = 倍率档位（法强倍率 = 1.0 + 等级 × 配置步进）。
 */
public class ChargeOverdriveEnchantment extends AbstractChargePerkEnchantment {

    public ChargeOverdriveEnchantment() {
        super(Rarity.VERY_RARE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    /**
     * 是溢出处理者：充能满后累计溢出，达阈值激活过载。
     */
    @Override
    public boolean isOverflowHandler() {
        return true;
    }

    /**
     * 是过载提供者：core 的 applyOverdriveBonus 通过此标记查找等级。
     */
    @Override
    public boolean isOverdrive() {
        return true;
    }

    /**
     * 溢出处理：累计溢出量，达阈值激活过载，返回 max 停止循环。
     */
    @Override
    public double handleOverflow(ItemStack magazine, int level,
                                 double overflowCharge, double max, long currentTick) {
        if (!ModChargeData.hasOverdrive(magazine)) {
            double overflow = overflowCharge - max;
            double accumulated = ModChargeData.getOverdriveProgress(magazine) + overflow;
            double threshold = max * ArcaneMagConfig.CHARGE_OVERDRIVE_THRESHOLD.get();
            if (accumulated >= threshold) {
                ModChargeData.activateOverdrive(magazine,
                        currentTick + ArcaneMagConfig.CHARGE_OVERDRIVE_DURATION_TICKS.get());
                ModChargeData.setOverdriveProgress(magazine, 0.0);
            } else {
                ModChargeData.setOverdriveProgress(magazine, accumulated);
            }
            return max;
        }
        return overflowCharge;
    }
}
