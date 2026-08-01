package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import org.jetbrains.annotations.Nullable;

/**
 * multi_trigger 附魔（多重扳机）
 *
 * 每次扣扳机额外射击 N 次（N = 附魔等级），弹药消耗随之翻倍。
 * 利用 TACZ shootOnce 内部的 cycles 连发机制：
 *   非连发枪 cycles=1，附魔 I → cycles=2（射 2 发、扣 2 弹药）；
 *   连发枪 cycles=N，附魔 I → cycles=N+1（叠加）。
 *
 * 弹药消耗、弹药不足处理、后坐力/枪声、下游效果触发均由 TACZ 原生 cycle 机制自动处理，
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.enchant.mixin.MultiTriggerMixin}：
 * 用 @ModifyVariable 改 shootOnce 内的 cycles 局部变量。
 *
 * 仅可刻于枪口（MUZZLE）配件
 */
public class MultiTriggerEnchantment extends AbstractPerkEnchantment {

    public MultiTriggerEnchantment() {
        super(Rarity.RARE);
    }

    /**
     * 多重扳机仅可刻于枪口（MUZZLE）槽位。
     */
    @Nullable
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.MUZZLE;
    }

    @Override
    public PerkExclusionGroup getExclusionGroup() {
        return PerkExclusionGroup.MUZZLE_PERK;
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 12;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 18;
    }
}
