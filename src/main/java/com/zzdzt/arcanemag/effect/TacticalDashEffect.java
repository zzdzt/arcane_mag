package com.zzdzt.arcanemag.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 战术冲刺增益标记效果。
 * 
 * 纯标记类效果，不自带任何 AttributeModifier。
 * 实际爆头伤害倍率提升由 {@link com.zzdzt.arcanemag.event.GunPropertyEventHandler}
 * 在 TACZ {@link com.tacz.guns.api.event.common.AttachmentPropertyEvent} 回调中完成。
 */
public class TacticalDashEffect extends MobEffect {

    public TacticalDashEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFAA00); // 橙金色
    }
}
