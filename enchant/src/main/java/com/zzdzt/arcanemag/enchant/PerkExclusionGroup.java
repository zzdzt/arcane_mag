package com.zzdzt.arcanemag.enchant;

/**
 * Perk 互斥组。
 *
 * 同一互斥组内的 Perk 互相排斥（单个配件只能刻其中一个），
 * 不同组或无组的 Perk 彼此兼容（仍受单配件附魔总数上限约束）。
 *
 * 扩展方式：新增一个枚举常量，并让相关 Perk 覆写
 * {@link AbstractPerkEnchantment#getExclusionGroup()} 返回该常量即可，
 */
public enum PerkExclusionGroup {
    CHARGE_MECHANISM,
    FRENZY,
    MUZZLE_PERK,
}
