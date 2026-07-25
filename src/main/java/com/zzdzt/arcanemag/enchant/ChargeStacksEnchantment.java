package com.zzdzt.arcanemag.enchant;

/**
 * charge_stacks 附魔（储能）
 *
 * 充能满后继续命中，溢出转化为额外释放次数层数。
 * 附魔等级 = 最大额外释放次数。
 */
public class ChargeStacksEnchantment extends AbstractChargePerkEnchantment {

    public ChargeStacksEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }
}
