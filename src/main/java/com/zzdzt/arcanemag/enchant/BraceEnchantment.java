package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

/**
 * brace 附魔（蓄势）
 *
 * 限定枪托。非战斗状态时积累两种蓄势层数：
 *   - 枪械蓄势：枪械攻击消耗，每层 +3% baseAmount
 *   - 法术蓄势：枪械施法消耗，每层 +3% spell_power
 *
 * 积累：非战斗状态（3s 无战斗行为）后每秒 +1，上限 = level × 5。
 * 切枪丢失全部层数。两种蓄势独立消耗，互不干扰。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.BraceHandler}。
 */
public class BraceEnchantment extends AbstractPerkEnchantment {

    public BraceEnchantment() {
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

    /** 仅枪托可刻 */
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.STOCK;
    }

    /** 层数上限 = level × stacks_per_level */
    public static int getMaxStacks(int level) {
        return level * ArcaneMagConfig.BRACE_STACKS_PER_LEVEL.get();
    }
}
