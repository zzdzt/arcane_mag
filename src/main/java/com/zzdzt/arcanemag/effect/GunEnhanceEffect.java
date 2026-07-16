package com.zzdzt.arcanemag.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 枪械强化效果标记。
 *
 * 纯标记类效果，不自带任何 AttributeModifier，
 * 实际属性修改由 {@link com.zzdzt.arcanemag.event.GunPropertyEventHandler} 在 TACZ 事件回调中完成。
 */
public class GunEnhanceEffect extends MobEffect {

    public GunEnhanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00AAFF); // 亮蓝色
    }
}
