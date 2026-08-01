package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.utils.ModChargeData;
import net.minecraft.world.item.ItemStack;

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

    /**
     * 是溢出处理者：充能满后溢出转层数。
     */
    @Override
    public boolean isOverflowHandler() {
        return true;
    }

    /**
     * 溢出处理：每次消耗 max 量，增加一层 stacks，直到 stacks 达到等级上限。
     * 返回 overflowCharge - max 表示消耗了溢出（继续循环）；
     * 返回 overflowCharge 表示已满（交由后续 handler）。
     */
    @Override
    public double handleOverflow(ItemStack magazine, int level,
                                 double overflowCharge, double max, long currentTick) {
        if (ModChargeData.getStacks(magazine) < level) {
            ModChargeData.setStacks(magazine, ModChargeData.getStacks(magazine) + 1);
            return overflowCharge - max;
        }
        return overflowCharge;
    }
}
