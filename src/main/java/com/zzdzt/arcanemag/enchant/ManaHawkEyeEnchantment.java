package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * mana_hawk_eye 附魔（法力鹰眼）
 *
 * 开镜期间消耗max_mana，累计消耗的法力按比例转化为射击伤害加成
 * 退出开镜清零累计。增益通过 AttachmentPropertyEvent 应用到枪属性 cache。
 *
 * 与多重扳机兼容：增益作用在枪属性 cache 上，多重扳机每次额外射击共享同一 cache。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.ManaHawkEyeHandler}。
 */
public class ManaHawkEyeEnchantment extends AbstractPerkEnchantment {

    public ManaHawkEyeEnchantment() {
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

    /** 仅瞄准镜可刻（与"开镜"语义强相关） */
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.SCOPE;
    }

    /**
     * 指定等级下的伤害加成上限（百分比，1 = 1%）。
     * 公式：等级 × 每级上限，后者见 {@link ArcaneMagConfig#MANA_HAWK_EYE_MAX_BONUS_PER_LEVEL}。
     */
    public static int maxBonusPercent(int level) {
        return (int) (level * ArcaneMagConfig.MANA_HAWK_EYE_MAX_BONUS_PER_LEVEL.get());
    }
}
