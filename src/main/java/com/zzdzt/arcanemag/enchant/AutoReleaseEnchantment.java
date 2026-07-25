package com.zzdzt.arcanemag.enchant;

/**
 * auto_release 附魔（充能随放）
 *
 * 弹匣充能满且子弹命中时，自动以命中实体为目标释放铭刻法术。
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.AutoReleaseHandler}。
 */
public class AutoReleaseEnchantment extends AbstractPerkEnchantment {

    public AutoReleaseEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 5;
    }

    @Override
    public int getMaxCost(int level) {
        return 20;
    }
}
