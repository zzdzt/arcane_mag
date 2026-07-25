package com.zzdzt.arcanemag.enchant;

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
}
