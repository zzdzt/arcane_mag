package com.zzdzt.arcanemag.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;


/**
 * 惊霆诀充能效果
 * 
 * 纯标记效果，不自带属性修改。
 * 层数 = amplifier + 1（0~8 对应 1~9 层）
 * 持续时间10秒，每次获得时刷新持续时间（不叠加层数，而是重新施加）
 */
public class JingTingChargeEffect extends MobEffect {
    
    public static final int MAX_STACKS = 9;
    public static final float DAMAGE_MULTIPLIER_PER_STACK = 0.5f;
    
    // 每层充能提供的法术强度加成（用于显示和计算）

    public JingTingChargeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1a1a2e);
    }

    /**
     * 计算当前层数对应的伤害倍率
     * 1层 = 1.5x
     */
    public static float getDamageMultiplier(int amplifier) {
        int stacks = Math.min(amplifier + 1, MAX_STACKS);
        return 1.0f + stacks * DAMAGE_MULTIPLIER_PER_STACK;
    }

    /**
     * 获取当前充能层数
     */
    public static int getStackCount(int amplifier) {
        return Math.min(amplifier + 1, MAX_STACKS);
    }
}