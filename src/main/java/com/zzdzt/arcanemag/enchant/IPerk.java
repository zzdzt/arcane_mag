package com.zzdzt.arcanemag.enchant;

/**
 * Perk 标记接口。
 *
 * 作为 ArcaneMag 行为型 Perk 的统一标记。
 *
 * 互斥关系不由本接口驱动：基础 Perk 之间默认兼容，
 * 仅同一互斥组（见 {@link PerkExclusionGroup}，如充能机制）内的 Perk 互相排斥。
 * 不影响普通 / 原版 / TACZ 附魔的安装。
 */
public interface IPerk {
}
