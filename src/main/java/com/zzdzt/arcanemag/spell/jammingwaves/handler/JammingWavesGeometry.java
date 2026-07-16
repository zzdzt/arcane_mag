package com.zzdzt.arcanemag.spell.jammingwaves.handler;

import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesDefenseEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 干扰波纹几何计算工具类。
 * 
 * 纯静态方法，负责所有拦截位置、法线、方向相关的数学计算。
 */
public final class JammingWavesGeometry {

    private JammingWavesGeometry() {}

    // 方向工具 

    public static boolean isUsableDirection(@Nullable Vec3 v) {
        return v != null && v.lengthSqr() > 1.0e-4;
    }

    public static Vec3 getHorizontalLook(LivingEntity entity) {
        var look = entity.getLookAngle();
        var horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() > 1.0e-6 ? horizontal.normalize() : new Vec3(0, 0, 1);
    }

    public static Vec3 resolveIncomingDirection(LivingEntity defender, net.minecraft.world.damagesource.DamageSource source) {
        var direct = source.getDirectEntity();
        if (direct != null && direct != defender) {
            return direct.getBoundingBox().getCenter().subtract(defender.getBoundingBox().getCenter());
        }
        var attacker = source.getEntity();
        if (attacker != null && attacker != defender) {
            return attacker.getBoundingBox().getCenter().subtract(defender.getBoundingBox().getCenter());
        }
        var sourcePos = source.getSourcePosition();
        if (sourcePos != null) {
            return sourcePos.subtract(defender.getBoundingBox().getCenter());
        }
        return Vec3.ZERO;
    }

    // 位置计算 

    public static Vec3 getMeleeInterceptPosition(LivingEntity defender, @Nullable net.minecraft.world.entity.Entity attacker, double meleeInterceptDistance) {
        var defenderCenter = defender.getBoundingBox().getCenter();
        var dir = attacker == null ? defender.getLookAngle()
            : attacker.getBoundingBox().getCenter().subtract(defenderCenter);
        var horizontal = new Vec3(dir.x, 0.0, dir.z);
        if (!isUsableDirection(horizontal))
            horizontal = new Vec3(defender.getLookAngle().x, 0.0, defender.getLookAngle().z);
        if (!isUsableDirection(horizontal)) return defenderCenter;

        var defenderRadius = defender.getBbWidth() * 0.5;
        var distance = Math.max(meleeInterceptDistance, defenderRadius * 0.8);
        return defenderCenter.add(horizontal.normalize().scale(distance));
    }

    public static Vec3 getMeleeInterceptNormal(LivingEntity defender, @Nullable net.minecraft.world.entity.Entity attacker) {
        var dir = attacker == null ? defender.getLookAngle()
            : attacker.getBoundingBox().getCenter().subtract(defender.getBoundingBox().getCenter());
        var horizontal = new Vec3(dir.x, 0.0, dir.z);
        return isUsableDirection(horizontal) ? horizontal.normalize() : new Vec3(0, 0, 1);
    }

    public static Vec3 getRangedInterceptPosition(LivingEntity defender,
                                                     @Nullable net.minecraft.world.entity.Entity attacker,
                                                     @Nullable Vec3 incomingMotion,
                                                     double interceptRadius) {
        var center = defender.getBoundingBox().getCenter();
        Vec3 direction = null;
        double segmentLength = interceptRadius;

        if (attacker != null) {
            var toAttacker = attacker.getBoundingBox().getCenter().subtract(center);
            if (isUsableDirection(toAttacker)) { direction = toAttacker; segmentLength = toAttacker.length(); }
        }
        if (!isUsableDirection(direction) && isUsableDirection(incomingMotion)) {
            direction = incomingMotion.scale(-1);
        }
        if (!isUsableDirection(direction)) direction = defender.getLookAngle();
        if (!isUsableDirection(direction)) return center;

        var dist = Math.min(interceptRadius, Math.max(0.0, segmentLength));
        return center.add(direction.normalize().scale(dist));
    }

    public static Vec3 getInterceptNormal(LivingEntity caster, Vec3 interceptPos,
                                            @Nullable net.minecraft.world.entity.Entity attacker, @Nullable Vec3 incomingMotion) {
        if (isUsableDirection(incomingMotion)) return incomingMotion.scale(-1).normalize();
        if (attacker != null) {
            var dir = attacker.position().subtract(caster.getEyePosition());
            if (isUsableDirection(dir)) return dir.normalize();
        }
        var fallback = interceptPos.subtract(caster.getEyePosition());
        return isUsableDirection(fallback) ? fallback.normalize() : caster.getLookAngle();
    }

    // 正面扇形判定 

    public static boolean isProjectileInFrontArc(LivingEntity defender, net.minecraft.world.entity.projectile.Projectile projectile, double frontDotThreshold) {
        var defenderForward = getHorizontalLook(defender);
        var toProjectile = projectile.position().subtract(defender.getEyePosition());
        var horizontal = new Vec3(toProjectile.x, 0.0, toProjectile.z);
        if (horizontal.lengthSqr() < 1.0e-6) return true;
        return defenderForward.dot(horizontal.normalize()) >= frontDotThreshold;
    }

    public static boolean isDamageFromFront(LivingEntity defender, net.minecraft.world.damagesource.DamageSource source, double frontDotThreshold) {
        var defenderForward = getHorizontalLook(defender);
        var incoming = resolveIncomingDirection(defender, source);
        if (!isUsableDirection(incoming)) return false;
        var horizontal = new Vec3(incoming.x, 0.0, incoming.z);
        if (horizontal.lengthSqr() < 1.0e-6) return true;
        return defenderForward.dot(horizontal.normalize()) >= frontDotThreshold;
    }
}
