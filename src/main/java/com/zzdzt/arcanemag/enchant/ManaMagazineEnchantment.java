package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import org.jetbrains.annotations.Nullable;

/**
 * mana_magazine 附魔（魔力弹匣）
 *
 * 弹匣打空时不走换弹流程，而是支付法力凭空补 1 发继续射击。
 */
public class ManaMagazineEnchantment extends AbstractPerkEnchantment {

    public ManaMagazineEnchantment() {
        super(Rarity.RARE);
    }

    /**
     * 魔力弹匣仅可刻于弹匣（EXTENDED_MAG）槽位。
     */
    @Nullable
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.EXTENDED_MAG;
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
}
