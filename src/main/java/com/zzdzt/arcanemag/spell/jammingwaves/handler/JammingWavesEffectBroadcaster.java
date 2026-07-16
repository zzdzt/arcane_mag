package com.zzdzt.arcanemag.spell.jammingwaves.handler;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.network.ArcaneMagNetworking;
import com.zzdzt.arcanemag.network.JammingWavesEffectPacket;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * 干扰波纹特效广播器。
 * 
 * 负责拦截成功/失败时的网络包发送、音效播放，以及持续施法期间的环境护盾墙特效。
 */
public final class JammingWavesEffectBroadcaster {

    private static final float DEFAULT_WALL_SIZE_SCALE = 1.5f;
    private static final float DEFAULT_WALL_LIFETIME_SCALE = 1.0f;
    private static final int AMBIENT_WALL_INTERVAL_TICKS = 5;
    private static final float AMBIENT_WALL_LIFETIME_SCALE = 1.0f;
    private static final boolean AMBIENT_RENDER_WAVE = false;
    private static final int AMBIENT_DIRECTION_SAMPLE_COUNT = 3;
    private static final String PREFIX = ArcaneMag.MODID + ":jamming_waves_";
    private static final String AMBIENT_WALL_LAST_SPAWN_TAG = PREFIX + "ambient_last_spawn";

    private JammingWavesEffectBroadcaster() {}

    // 拦截成功/失败广播 

    public static void onIntercept(LivingEntity caster, JammingWavesContext context,
                                    Vec3 position, Vec3 normal) {
        onIntercept(caster, context, position, normal, DEFAULT_WALL_SIZE_SCALE);
    }

    public static void onIntercept(LivingEntity caster, JammingWavesContext context,
                                    Vec3 position, Vec3 normal, float sizeScale) {
        onIntercept(caster, context, position, normal, sizeScale, DEFAULT_WALL_LIFETIME_SCALE,
            true, false);
    }

    public static void onIntercept(LivingEntity caster, JammingWavesContext context,
                                    Vec3 position, Vec3 normal, float sizeScale,
                                    float lifetimeScale, boolean renderWave, boolean failed) {
        JammingWavesManaDrain.onIntercept(caster, context);
        broadcast(caster, position, normal, sizeScale, lifetimeScale, renderWave, failed);

        if (failed) {
            playSound(caster.level(), position, SoundEvents.ITEM_BREAK, 0.5f, 1.5f);
        } else {
            playSound(caster.level(), position, SoundEvents.SHIELD_BLOCK, 0.6f, 1.2f);
        }
    }

    public static void onDefenseFailed(LivingEntity defender, JammingWavesContext context,
                                        net.minecraft.world.damagesource.DamageSource source,
                                        double meleeInterceptDistance) {
        if (!shouldTriggerDefenseResponse(defender, source)) return;

        // 使用 defender 的正面方向生成失败特效
        var look = defender.getLookAngle();
        var horizontalLook = new Vec3(look.x, 0.0, look.z);
        if (!JammingWavesGeometry.isUsableDirection(horizontalLook)) {
            horizontalLook = new Vec3(0, 0, 1);
        }
        horizontalLook = horizontalLook.normalize();

        var defenderCenter = defender.getBoundingBox().getCenter();
        var pos = defenderCenter.add(horizontalLook.scale(meleeInterceptDistance));
        var normal = horizontalLook.reverse();

        onIntercept(defender, context, pos, normal, DEFAULT_WALL_SIZE_SCALE,
            DEFAULT_WALL_LIFETIME_SCALE, false, true);
    }

    // 环境护盾墙特效 

    public static void spawnAmbientWall(Level level, LivingEntity caster, double interceptRadius) {
        if (level.isClientSide) return;

        long currentGameTime = level.getGameTime();
        long lastSpawnTime = caster.getPersistentData().getLong(AMBIENT_WALL_LAST_SPAWN_TAG);

        if (currentGameTime - lastSpawnTime < AMBIENT_WALL_INTERVAL_TICKS) {
            return;
        }
        caster.getPersistentData().putLong(AMBIENT_WALL_LAST_SPAWN_TAG, currentGameTime);

        var direction = getAmbientWallDirection(caster);
        if (!JammingWavesGeometry.isUsableDirection(direction)) return;

        var position = caster.getBoundingBox().getCenter().add(direction.scale(interceptRadius));
        var normal = direction.reverse();

        broadcast(caster, position, normal, DEFAULT_WALL_SIZE_SCALE,
            AMBIENT_WALL_LIFETIME_SCALE, AMBIENT_RENDER_WAVE, false);
    }

    private static Vec3 getAmbientWallDirection(LivingEntity caster) {
        var look = caster.getLookAngle();
        if (!JammingWavesGeometry.isUsableDirection(look)) {
            look = new Vec3(0, 0, 1);
        } else {
            look = look.normalize();
        }

        var bestDirection = look;
        var bestDot = -Double.MAX_VALUE;
        for (int i = 0; i < AMBIENT_DIRECTION_SAMPLE_COUNT; i++) {
            var candidate = randomUnitVector(caster);
            var dot = candidate.dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                bestDirection = candidate;
            }
        }

        var blended = bestDirection.scale(0.45).add(look.scale(0.55));
        if (!JammingWavesGeometry.isUsableDirection(blended)) {
            return look;
        }
        return blended.normalize();
    }

    private static Vec3 randomUnitVector(LivingEntity entity) {
        var random = entity.getRandom();
        var azimuth = random.nextDouble() * Math.PI * 2.0;
        var y = random.nextDouble() * 2.0 - 1.0;
        var radial = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new Vec3(
            radial * Math.cos(azimuth),
            y,
            radial * Math.sin(azimuth)
        );
    }

    // 网络广播 

    private static void broadcast(LivingEntity caster, Vec3 position, Vec3 normal,
                                   float sizeScale, float lifetimeScale,
                                   boolean renderWave, boolean failed) {
        var safeNormal = JammingWavesGeometry.isUsableDirection(normal) ? normal.normalize()
            : JammingWavesGeometry.getInterceptNormal(caster, position, null, null);
        var packet = new JammingWavesEffectPacket(position, safeNormal, sizeScale,
            lifetimeScale, renderWave, failed);
        if (caster instanceof ServerPlayer serverPlayer) {
            ArcaneMagNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> serverPlayer),
                packet);
        }
    }

    // 冷却与音效 

    private static final int SAME_SOURCE_INTERCEPT_COOLDOWN_TICKS = 8;
    private static final String LAST_INTERCEPT_SOURCE_TAG = PREFIX + "last_source";
    private static final String LAST_INTERCEPT_TICK_TAG = PREFIX + "last_tick";

    private static boolean shouldTriggerDefenseResponse(LivingEntity defender, net.minecraft.world.damagesource.DamageSource source) {
        var sourceKey = buildDamageSourceKey(source);
        if (sourceKey.isBlank()) return true;
        var now = defender.level().getGameTime();
        var data = defender.getPersistentData();
        var lastKey = data.getString(LAST_INTERCEPT_SOURCE_TAG);
        var lastTick = data.getLong(LAST_INTERCEPT_TICK_TAG);
        if (sourceKey.equals(lastKey) && now - lastTick < SAME_SOURCE_INTERCEPT_COOLDOWN_TICKS)
            return false;
        data.putString(LAST_INTERCEPT_SOURCE_TAG, sourceKey);
        data.putLong(LAST_INTERCEPT_TICK_TAG, now);
        return true;
    }

    private static String buildDamageSourceKey(net.minecraft.world.damagesource.DamageSource source) {
        var id = source.type().msgId();
        var direct = source.getDirectEntity();
        if (direct != null) return id + "|direct:" + direct.getUUID();
        var attacker = source.getEntity();
        if (attacker != null) return id + "|attacker:" + attacker.getUUID();
        return id + "|environment";
    }

    private static void playSound(Level level, Vec3 pos, net.minecraft.sounds.SoundEvent sound,
                                   float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume,
            pitch + (level.random.nextFloat() - 0.5f) * 0.2f);
    }
}
