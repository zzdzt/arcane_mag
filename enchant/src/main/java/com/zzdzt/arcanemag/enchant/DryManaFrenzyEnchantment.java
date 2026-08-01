package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * dry_mana_frenzy 附魔（涸法狂击）
 *
 * 当玩家 IS 法力百分比维持在配置区间 [min, max]时，
 * 手持枪械获得伤害（随等级）+ 射速（固定）增益。
 * 触发模式：持续检测法力，状态翻转时刷新枪属性缓存。
 *
 * 增益应用走 AttachmentPropertyEvent，与 GunEnhance 同机制。
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.DryManaFrenzyHandler}。
 */
public class DryManaFrenzyEnchantment extends AbstractPerkEnchantment {

    public DryManaFrenzyEnchantment() {
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
     * 伤害倍率 = 1.0 + 等级 × 每级百分比（默认 I=+15% / II=+30% / III=+45%）。
     */
    public static float damageMultiplier(int level) {
        return 1.0f + (float) (level * EnchantConfig.DRY_MANA_FRENZY_DAMAGE_PER_LEVEL.get());
    }

    /**
     * 射速倍率 = 1.0 + 固定百分比（不随等级，默认 +15%）。
     */
    public static float rpmMultiplier() {
        return 1.0f + EnchantConfig.DRY_MANA_FRENZY_FIRE_RATE_BONUS.get().floatValue();
    }
}
