package com.zzdzt.arcanemag.enchant;

/**
 * ammo_retention 附魔（节约风气）
 *
 * 攻击时有概率不消耗弹药。附魔等级越高，保留概率越高
 * （概率 = 等级 × 配置步进，上限 1.0）。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.mixin.AmmoRetentionMixin}：
 * 拦截 TACZ shootOnce，阻止弹药消耗。
 */
public class AmmoRetentionEnchantment extends AbstractPerkEnchantment {

    public AmmoRetentionEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + (level - 1) * 10;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }
}
