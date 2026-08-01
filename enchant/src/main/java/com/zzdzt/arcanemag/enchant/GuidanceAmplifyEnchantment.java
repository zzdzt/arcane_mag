package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * guidance_amplify 附魔（引导增幅）
 *
 * 使用枪械释放 ISS 持续型（CastType.CONTINUOUS）法术期间，枪械获得伤害与射速增益。
 * 等级 = 伤害加成档位（每级 +GUIDANCE_AMPLIFY_DAMAGE_BONUS_PER_LEVEL%，射速固定加成）。
 * 法术施放结束（isCasting=false 或 CastType 变化）自动移除增益。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.GuidanceAmplifyHandler}。
 */
public class GuidanceAmplifyEnchantment extends AbstractPerkEnchantment {

    public GuidanceAmplifyEnchantment() {
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
     * 公式：等级 × 每级百分比，后者见 {@link EnchantConfig#GUIDANCE_AMPLIFY_DAMAGE_BONUS_PER_LEVEL}。
     */
    public static double damageBonusPercent(int level) {
        return level * EnchantConfig.GUIDANCE_AMPLIFY_DAMAGE_BONUS_PER_LEVEL.get();
    }
}
