package com.zzdzt.arcanemag.spell.arcaneaim;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.ArcaneAimEffect;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ArcaneAimGunHandler {

    private static final Map<UUID, AimSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final long SESSION_DURATION_TICKS = 5;

    private record AimSession(UUID targetUUID, long expiryTick) {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGunFire(GunFireEvent event) {
        LivingEntity shooter = event.getShooter();
        if (!(shooter instanceof Player player)) return;
        if (shooter.level().isClientSide()) return;

        MobEffectInstance aimEffect = player.getEffect(EffectRegistry.ARCANE_AIM.get());
        if (aimEffect == null) return;

        UUID targetUUID = player.getPersistentData().getUUID(ArcaneAimEffect.AIM_TARGET_TAG);
        if (targetUUID == null) return;

        LivingEntity target = findTargetByUUID(player, targetUUID);
        if (target == null || !target.isAlive()) {
            player.removeEffect(EffectRegistry.ARCANE_AIM.get());
            player.getPersistentData().remove(ArcaneAimEffect.AIM_TARGET_TAG);
            ACTIVE_SESSIONS.remove(player.getUUID());
            return;
        }

        Vec3 shooterEye = player.getEyePosition(1.0f);
        Vec3 targetHead = target.getEyePosition(1.0f).add(0, 0.1, 0);
        Vec3 idealDir = targetHead.subtract(shooterEye).normalize();
        Vec3 lookDir = player.getLookAngle();

        double dot = lookDir.dot(idealDir);
        double angle = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));

        int spellLevel = aimEffect.getAmplifier() + 1;
        float maxConeAngle = ArcaneAimSpell.getAimConeAngle(spellLevel);

        if (angle > maxConeAngle) return;
        if (!hasLineOfSight(player, targetHead)) return;

        long now = player.level().getGameTime();
        ACTIVE_SESSIONS.put(player.getUUID(), new AimSession(targetUUID, now + SESSION_DURATION_TICKS));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBulletJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof EntityKineticBullet bullet)) return;

        Entity owner = bullet.getOwner();
        if (!(owner instanceof Player shooter)) return;

        AimSession session = ACTIVE_SESSIONS.get(shooter.getUUID());
        if (session == null) return;

        long now = shooter.level().getGameTime();
        if (now > session.expiryTick()) {
            ACTIVE_SESSIONS.remove(shooter.getUUID());
            return;
        }

        LivingEntity target = findTargetByUUID(shooter, session.targetUUID());
        if (target == null || !target.isAlive()) {
            ACTIVE_SESSIONS.remove(shooter.getUUID());
            return;
        }

        Vec3 bulletPos = bullet.position();
        Vec3 targetHead = target.getEyePosition(1.0f).add(0, 0.1, 0);
        redirectBulletToTarget(bullet, bulletPos, targetHead);
    }

    @Nullable
    private static LivingEntity findTargetByUUID(Player shooter, UUID targetUUID) {
        if (shooter.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(targetUUID);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }

    private static boolean hasLineOfSight(Player shooter, Vec3 targetPoint) {
        Vec3 eyePos = shooter.getEyePosition(1.0f);
        return shooter.level().clip(new net.minecraft.world.level.ClipContext(
            eyePos, targetPoint,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            shooter
        )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static void redirectBulletToTarget(EntityKineticBullet bullet, Vec3 fromPos, Vec3 toPos) {
        Vec3 newDir = toPos.subtract(fromPos).normalize();
        
        double currentSpeed = bullet.getDeltaMovement().length();
        double speed = currentSpeed > 0.1 ? currentSpeed : 20.0;

        Vec3 newMotion = newDir.scale(speed);
        bullet.setDeltaMovement(newMotion);
        bullet.hasImpulse = true;

        double horizontalDist = Math.sqrt(newDir.x * newDir.x + newDir.z * newDir.z);
        float yaw = (float) Math.toDegrees(Math.atan2(newDir.x, newDir.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(newDir.y, horizontalDist));

        bullet.setYRot(yaw);
        bullet.setXRot(pitch);
    }
}