package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * overflow 附魔（充盈）
 *
 * 枪械弹匣法术充能完全充满时，枪械获得伤害与射速增益，持续 10s 或法术被释放。
 * 等级 = 伤害加成档位（每级 +OVERFLOW_DAMAGE_BONUS_PER_LEVEL%，射速固定加成）。
 * 重新充满会刷新持续时间。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.OverflowHandler}。
 */
public class OverflowEnchantment extends AbstractPerkEnchantment {

    public OverflowEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return 12 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    /**
     * 指定等级下的伤害加成百分比（1 = 1%）。
     * 公式：等级 × 每级百分比，后者见 {@link EnchantConfig#OVERFLOW_DAMAGE_BONUS_PER_LEVEL}。
     */
    public static double damageBonusPercent(int level) {
        return level * EnchantConfig.OVERFLOW_DAMAGE_BONUS_PER_LEVEL.get();
    }
}
