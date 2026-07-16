package com.zzdzt.arcanemag.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 注魔子弹标记效果基类。
 * 
 * 纯标记效果，不自带属性修改。
 * 实际的伤害转化和特效由ImbuedBulletEventHandler处理。
 * amplifier存储法术等级（0-4对应1-5级）。
 */
public class ImbuedBulletMobEffect extends MobEffect {

    public ImbuedBulletMobEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // 不需要每tick触发效果
    }
}