package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * pellet_burst 附魔（爆破弹丸）
 *
 * 限定枪口。累计命中阈值后触发范围爆炸：
 *   - 消耗弹匣法术当前充能的一半
 *   - 范围伤害 = 消耗充能值 × 系数（受护甲减免，攻击来源=玩家）
 *   - 阈值随等级递减
 *
 * 与多重扳机、法术共鸣互斥（MUZZLE_PERK 组）。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.PelletBurstHandler}。
 */
public class PelletBurstEnchantment extends AbstractPerkEnchantment {

    public PelletBurstEnchantment() {
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
     * 计算触发阈值 = base - (level-1) * reduction。
     * 默认 I=50 / II=40 / III=30。
     */
    public static int getThreshold(int level) {
        int base = EnchantConfig.PELLET_BURST_THRESHOLD_BASE.get();
        int reduction = EnchantConfig.PELLET_BURST_THRESHOLD_REDUCTION_PER_LEVEL.get();
        return Math.max(1, base - (level - 1) * reduction);
    }
}
