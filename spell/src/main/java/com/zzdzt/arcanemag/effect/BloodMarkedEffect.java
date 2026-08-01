package com.zzdzt.arcanemag.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 鲜血标记效果。
 *
 * 纯标记效果，不自带属性修改。
 * 由 BloodImbuedBulletSpell 施加给目标，用于检测目标在持续时间内死亡。
 * 视觉与音效设计留空（以后实现）。
 */
public class BloodMarkedEffect extends MobEffect {

    public BloodMarkedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000); // 深红色
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // 不需要每tick触发效果
    }
}
