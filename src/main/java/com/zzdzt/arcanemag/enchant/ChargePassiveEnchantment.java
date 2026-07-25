package com.zzdzt.arcanemag.enchant;

/**
 * charge_passive 附魔（被动充能）
 *
 * 持枪时自动缓慢恢复充能值，无需命中。
 * 附魔等级 = 速率档位（每秒恢复量 = 等级 × 配置基础速率）。
 */
public class ChargePassiveEnchantment extends AbstractChargePerkEnchantment {

    public ChargePassiveEnchantment() {
        super(Rarity.UNCOMMON);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 5 + (level - 1) * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 10;
    }
}
