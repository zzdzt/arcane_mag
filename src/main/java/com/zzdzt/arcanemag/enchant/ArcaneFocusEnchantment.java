package com.zzdzt.arcanemag.enchant;

/**
 * arcane_focus 附魔（奥术聚焦）
 *
 * 使用枪械释放法术后，接下来 X 发子弹获得爆头倍率提升。
 * 附魔等级 = 增强的子弹层数 X（Lv1=1发，Lv3=3发）。
 *
 * 层数在施法成功时写入玩家 NBT；子弹命中实体时即消耗一层
 * 爆头倍率加成仅在爆头命中时生效
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.ArcaneFocusHandler}。
 */
public class ArcaneFocusEnchantment extends AbstractPerkEnchantment {

    public ArcaneFocusEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 3;
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
