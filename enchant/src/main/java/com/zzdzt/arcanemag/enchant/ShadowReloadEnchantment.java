package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;

/**
 * shadow_reload 附魔（暗隐装填）
 *
 * 换弹开始时获得 Iron's Spells TrueInvisibility 隐身（默认 2 秒），
 * 内置冷却。仅弹匣可刻。
 *
 * 触发逻辑见 {@link com.zzdzt.arcanemag.event.enchant.ShadowReloadHandler}。
 */
public class ShadowReloadEnchantment extends AbstractPerkEnchantment {

    public ShadowReloadEnchantment() {
        super(Rarity.RARE);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 15;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 10;
    }

    /** 仅弹匣（扩容弹匣附件）可刻 */
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.EXTENDED_MAG;
    }

    /** 隐身持续 tick（默认 40 = 2s） */
    public static int invisDurationTicks() {
        return EnchantConfig.SHADOW_RELOAD_INVIS_DURATION_TICKS.get();
    }

    /** 内置冷却 tick（默认 100 = 5s） */
    public static int cooldownTicks() {
        return EnchantConfig.SHADOW_RELOAD_COOLDOWN_TICKS.get();
    }
}
