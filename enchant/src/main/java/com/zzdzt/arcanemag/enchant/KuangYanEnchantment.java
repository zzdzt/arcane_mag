package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * kuang_yan 附魔（狂宴）
 *
 * 爆头击杀回复百分比法力
 * 附魔等级 = 回蓝百分比档位（回蓝百分比 = 等级 * 每级百分比）
 */
public class KuangYanEnchantment extends AbstractPerkEnchantment {

    public KuangYanEnchantment() {
        super(Rarity.UNCOMMON);
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
     * 指定等级下单次爆头击杀回复的法力占最大法力的比例（0~1）。
     * 公式：等级 * 每级百分比，后者见 {@link EnchantConfig#KUANG_YAN_MANA_PERCENT_PER_LEVEL}，可在配置中调整。
     * 实际回蓝量由触发端乘以玩家最大法力（IS MAX_MANA 属性）得出。
     */
    public static float restorePercentOnKill(int level) {
        return (float) (level * EnchantConfig.KUANG_YAN_MANA_PERCENT_PER_LEVEL.get());
    }
}
