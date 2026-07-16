package com.zzdzt.arcanemag.effect;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

public class ForcedMarchMobility extends MobEffect {
    
    // 每级速度加成（Speed I = 20%, V = 100%）
    public static final double SPEED_PER_LEVEL = 0.20;
    public static final double STEP_HEIGHT_ADDITION = 0.4;
    
    private static final UUID MOVE_SPEED_MODIFIER_UUID = UUID.fromString("7c3f8a2e-9b4d-4e1c-a6f5-8d2e4c7b9a3f");
    private static final UUID STEP_HEIGHT_MODIFIER_UUID = UUID.fromString("3e9b5d1a-7c4f-4e8b-b2a6-5d3f7e1c9b4a");
    private static final UUID CASTING_SPEED_MODIFIER_UUID = UUID.fromString("8a4e7b2c-5d1f-4e9a-a3b6-7c2d8f4e1b5a");
    
    private static final String MOVE_SPEED_MODIFIER_ID = MOVE_SPEED_MODIFIER_UUID.toString();
    private static final String STEP_HEIGHT_MODIFIER_ID = STEP_HEIGHT_MODIFIER_UUID.toString();
    private static final String CASTING_SPEED_MODIFIER_ID = CASTING_SPEED_MODIFIER_UUID.toString();

    public ForcedMarchMobility() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700);
        
        // 移速加成（基础值，会被 getAttributeModifierValue 覆盖）
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            MOVE_SPEED_MODIFIER_ID,
            SPEED_PER_LEVEL,
            AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        
        // 步高加成
        addAttributeModifier(
            ForgeMod.STEP_HEIGHT_ADDITION.get(),
            STEP_HEIGHT_MODIFIER_ID,
            STEP_HEIGHT_ADDITION,
            AttributeModifier.Operation.ADDITION
        );
        
        // 施法移速减免
        addAttributeModifier(
            AttributeRegistry.CASTING_MOVESPEED.get(),
            CASTING_SPEED_MODIFIER_ID,
            100.0,
            AttributeModifier.Operation.ADDITION
        );
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        if (MOVE_SPEED_MODIFIER_UUID.equals(modifier.getId())) {
            // amplifier 0-4 对应 1-5 级
            int clamped = Math.max(0, Math.min(4, amplifier));
            return SPEED_PER_LEVEL * (clamped + 1);
        }
        if (CASTING_SPEED_MODIFIER_UUID.equals(modifier.getId())) {
            return 100.0;
        }
        return super.getAttributeModifierValue(amplifier, modifier);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}