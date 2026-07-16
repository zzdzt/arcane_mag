package com.zzdzt.arcanemag.effect;

import io.redspace.ironsspellbooks.effect.MagicMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class AbsoluteZeroEffect extends MagicMobEffect {
    // 创建一个唯一的 UUID 用于修改 MOVEMENT_SPEED 属性
    public static final UUID MODIFIER_UUID = UUID.fromString("588b1a13-699d-48c2-a952-120e14963cff");
    // 压制到 1% 移速 (即减少 99%)
    public static final double SPEED_REDUCTION = -0.99;

    public AbsoluteZeroEffect() {
        super(MobEffectCategory.HARMFUL, 0x00BFFF); 
        
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MODIFIER_UUID.toString(), SPEED_REDUCTION, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
