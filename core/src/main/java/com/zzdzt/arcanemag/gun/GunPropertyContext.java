package com.zzdzt.arcanemag.gun;

import net.minecraft.world.entity.LivingEntity;

/**
 * 枪械属性计算上下文。
 */
public class GunPropertyContext {
    private static final ThreadLocal<LivingEntity> CURRENT_SHOOTER = new ThreadLocal<>();

    public static void setShooter(LivingEntity shooter) {
        CURRENT_SHOOTER.set(shooter);
    }

    public static LivingEntity getShooter() {
        return CURRENT_SHOOTER.get();
    }

    public static void clear() {
        CURRENT_SHOOTER.remove();
    }
}
