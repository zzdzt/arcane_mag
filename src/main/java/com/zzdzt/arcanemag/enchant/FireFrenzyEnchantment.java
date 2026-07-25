package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * fire_frenzy 附魔（火力狂热）
 *
 * 持续战斗 12s 后，枪械伤害增加，直到 3s 内不再开火或不再受到攻击。
 * "战斗行为" = 开火命中或受到 LivingEntity 来源的伤害。
 * 等级 = 伤害加成档位（每级 +FIRE_FRENZY_DAMAGE_PER_LEVEL%）。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.FrenzyHandler}。
 */
public class FireFrenzyEnchantment extends AbstractPerkEnchantment {

    public FireFrenzyEnchantment() {
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
     * 公式：等级 × 每级百分比，后者见 {@link ArcaneMagConfig#FIRE_FRENZY_DAMAGE_PER_LEVEL}。
     */
    public static double damageBonusPercent(int level) {
        return level * ArcaneMagConfig.FIRE_FRENZY_DAMAGE_PER_LEVEL.get();
    }

    @Override
    public PerkExclusionGroup getExclusionGroup() {
        return PerkExclusionGroup.FRENZY;
    }

    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.MUZZLE;
    }
}
