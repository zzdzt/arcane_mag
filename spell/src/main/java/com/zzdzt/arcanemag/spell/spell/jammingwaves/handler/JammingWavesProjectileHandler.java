package com.zzdzt.arcanemag.spell.jammingwaves.handler;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesContext;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesSpell;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 干扰波纹弹射物拦截处理器。
 * 
 * 负责弹射物的三级处理：无害化偏移 → 捕获 → 消除。
 */
public final class JammingWavesProjectileHandler {

    private static final double MIN_APPROACH_SPEED_SQ = 0.0025;
    private static final double DEFLECT_MIN_SPEED = 0.75;
    private static final double DEFLECT_DEVIATION_ANGLE = Math.toRadians(45.0);
    private static final String PREFIX = ArcaneMag.MODID + ":jamming_waves_";
    private static final String DEFLECT_COUNT_TAG = PREFIX + "deflect_count";
    private static final String CAPTURED_TAG = PREFIX + "captured";

    private JammingWavesProjectileHandler() {}

    // 拦截条件 

    public static boolean shouldIntercept(LivingEntity caster, Projectile projectile, double interceptRadiusSq) {
        if (projectile.isRemoved() || projectile.getPersistentData().getBoolean(CAPTURED_TAG))
            return false;
        if (projectile.position().distanceToSqr(caster.position()) > interceptRadiusSq)
            return false;
        var owner = projectile.getOwner();
        if (owner == caster || (owner != null && owner.isAlliedTo(caster))) return false;
        var velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < MIN_APPROACH_SPEED_SQ) return false;
        var toCaster = caster.getEyePosition().subtract(projectile.position());
        return velocity.dot(toCaster) > 0.0;
    }

    // 三级处理入口 

    public static void neutralize(LivingEntity caster, JammingWavesContext context,
                                   Projectile projectile, Vec3 interceptPos,
                                   @Nullable Entity attackerEntity) {
        if (projectile.isRemoved()) return;

        var interceptNormal = JammingWavesGeometry.getInterceptNormal(caster, interceptPos, attackerEntity,
            projectile.getDeltaMovement());

        // 三级处理
        var deflectCount = projectile.getPersistentData().getInt(DEFLECT_COUNT_TAG);
        if (deflectCount <= 0 && tryHarmlessDeflect(caster, projectile)) {
            projectile.getPersistentData().putInt(DEFLECT_COUNT_TAG, 1);
            JammingWavesEffectBroadcaster.onIntercept(caster, context, interceptPos, interceptNormal);
            return;
        }

        if (tryCatchProjectile(caster, projectile)) {
            JammingWavesEffectBroadcaster.onIntercept(caster, context, interceptPos, interceptNormal);
            return;
        }

        projectile.discard();
        JammingWavesEffectBroadcaster.onIntercept(caster, context, interceptPos, interceptNormal);
    }

    public static void neutralize(LivingEntity caster, JammingWavesContext context, Projectile projectile) {
        neutralize(caster, context, projectile, projectile.position(), projectile.getOwner());
    }

    // 无害化偏移 

    private static boolean tryHarmlessDeflect(LivingEntity caster, Projectile projectile) {
        var velocity = projectile.getDeltaMovement();
        var speed = velocity.length();
        if (speed < 0.01) return false;

        var random = caster.getRandom();
        var deviationAngle = (random.nextBoolean() ? 1 : -1) * DEFLECT_DEVIATION_ANGLE;
        var cos = Math.cos(deviationAngle);
        var sin = Math.sin(deviationAngle);
        var newX = velocity.x * cos - velocity.z * sin;
        var newZ = velocity.x * sin + velocity.z * cos;
        var newVelocity = new Vec3(newX, velocity.y * 0.5, newZ)
            .normalize().scale(Math.max(speed, DEFLECT_MIN_SPEED));

        var offset = newVelocity.normalize().scale(0.3);
        projectile.setPos(projectile.getX() + offset.x,
            projectile.getY() + offset.y, projectile.getZ() + offset.z);
        projectile.setDeltaMovement(newVelocity);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        return true;
    }

    // 捕获弹射物 

    private static boolean tryCatchProjectile(LivingEntity caster, Projectile projectile) {
        var away = projectile.position().subtract(caster.getEyePosition());
        if (away.lengthSqr() < 1.0e-4) away = caster.getLookAngle().reverse();
        if (away.lengthSqr() < 1.0e-4) return false;

        var holdPos = caster.getEyePosition().add(away.normalize().scale(3.5 + 0.25)); // INTERCEPT_RADIUS + 0.25
        projectile.setPos(holdPos.x, holdPos.y, holdPos.z);
        projectile.setOwner(caster);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        projectile.getPersistentData().putBoolean(CAPTURED_TAG, true);

        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(0.0);
            arrow.setPierceLevel((byte) 0);
        }
        return true;
    }
}
