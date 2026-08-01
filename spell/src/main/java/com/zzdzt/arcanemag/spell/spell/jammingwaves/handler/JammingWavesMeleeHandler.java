package com.zzdzt.arcanemag.spell.jammingwaves.handler;

import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 干扰波纹近战拦截处理器。
 * <p>
 * 负责击退攻击者并在近战拦截位置生成护盾特效。
 */
public final class JammingWavesMeleeHandler {

    private static final float MELEE_KNOCKBACK_STRENGTH = 1.5f;
    private static final double MELEE_INTERCEPT_DISTANCE = 1.5;
    private static final float MELEE_WALL_SIZE_SCALE = 2.5f;

    private JammingWavesMeleeHandler() {}

    public static void handleIntercept(LivingEntity defender, JammingWavesContext context,
                                        net.minecraft.world.damagesource.DamageSource source) {
        var attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            livingAttacker.knockback(MELEE_KNOCKBACK_STRENGTH,
                defender.getX() - livingAttacker.getX(),
                defender.getZ() - livingAttacker.getZ());
        }
        var pos = JammingWavesGeometry.getMeleeInterceptPosition(defender, attacker, MELEE_INTERCEPT_DISTANCE);
        var normal = JammingWavesGeometry.getMeleeInterceptNormal(defender, attacker);
        JammingWavesEffectBroadcaster.onIntercept(defender, context, pos, normal, MELEE_WALL_SIZE_SCALE);
    }

    public static boolean isMeleeAttack(net.minecraft.world.damagesource.DamageSource source) {
        return source.getEntity() instanceof LivingEntity
            && source.getDirectEntity() == source.getEntity();
    }

    public static boolean isCloseRange(LivingEntity defender, @Nullable Entity attacker, double interceptRadiusSq) {
        if (attacker == null) return true;
        return attacker.getBoundingBox().getCenter()
            .distanceToSqr(defender.getBoundingBox().getCenter()) <= interceptRadiusSq;
    }
}
