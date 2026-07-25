package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * for_the_people 附魔（我为人人）
 *
 * 6 秒窗口内爆头命中 3 名不同敌人后，获得 10 秒 Iron's Spells spell_power 加成（可刷新）。
 * 附魔等级 = 法强加成档位（法强 = 等级 × 每级百分比）。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.ForThePeopleHandler}。
 */
public class ForThePeopleEnchantment extends AbstractPerkEnchantment {

    public ForThePeopleEnchantment() {
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
     * 指定等级下触发的 spell_power 加成比例（MULTIPLY_BASE）。
     * 公式：等级 × 每级百分比，后者见 {@link ArcaneMagConfig#FOR_THE_PEOPLE_SPELL_POWER_PER_LEVEL}，可在配置中调整。
     */
    public static double spellPowerBonus(int level) {
        return level * ArcaneMagConfig.FOR_THE_PEOPLE_SPELL_POWER_PER_LEVEL.get();
    }
}
