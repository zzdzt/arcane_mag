package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * spell_resonance 附魔（法术共鸣）
 *
 * 仅枪口可刻。子弹命中时，base damage 增加「附魔等级 × 弹匣法术等级 × 系数」的固定数值，
 * 走 TACZ 正常结算（按穿甲比例分割 + 护甲减伤）。
 *
 * 公式：bonus = enchantLevel × spellLevel × coefficient（默认系数 1.0）
 * 例：附魔 III + 法术 5 级 = +15 伤害（加入 baseAmount 后受护甲结算）
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.SpellResonanceHandler}。
 */
public class SpellResonanceEnchantment extends AbstractPerkEnchantment {

    public SpellResonanceEnchantment() {
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

    /** 仅枪口可刻 */
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.MUZZLE;
    }

    @Override
    public PerkExclusionGroup getExclusionGroup() {
        return PerkExclusionGroup.MUZZLE_PERK;
    }

    /**
     * 计算加成伤害 = 附魔等级 × 法术等级 × 系数。
     * 系数见 {@link ArcaneMagConfig#SPELL_RESONANCE_COEFFICIENT}。
     */
    public static float calculateBonus(int enchantLevel, int spellLevel) {
        return (float) (enchantLevel * spellLevel * ArcaneMagConfig.SPELL_RESONANCE_COEFFICIENT.get());
    }
}
