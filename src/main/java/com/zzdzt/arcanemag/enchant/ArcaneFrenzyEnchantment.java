package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * arcane_frenzy 附魔（奥术狂热）
 *
 * 持续战斗 12s 后，IS SPELL_POWER 属性增加，直到 3s 内不再开火或不再受到攻击。
 * "战斗行为" = 开火命中或受到 LivingEntity 来源的伤害。
 * 等级 = 法强加成档位（每级 +ARCANE_FRENZY_SPELL_POWER_PER_LEVEL，MULTIPLY_BASE）。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.FrenzyHandler}。
 */
public class ArcaneFrenzyEnchantment extends AbstractPerkEnchantment {

    public ArcaneFrenzyEnchantment() {
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
     * 指定等级下的 spell_power 加成（MULTIPLY_BASE 金额）。
     * 公式：等级 × 每级加成，后者见 {@link ArcaneMagConfig#ARCANE_FRENZY_SPELL_POWER_PER_LEVEL}。
     */
    public static double spellPowerBonus(int level) {
        return level * ArcaneMagConfig.ARCANE_FRENZY_SPELL_POWER_PER_LEVEL.get();
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
