package com.zzdzt.arcanemag.spell.imbuedbullet;

import java.util.List;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import com.zzdzt.arcanemag.utils.CombatTools;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 末影注魔子弹
 * 机制：
 * - 子弹命中时额外造成末影学派法术伤害
 * - 概率触发引力牵引，将周围敌人向命中点拉扯
 * - 触发概率随法术强度提高
 * - 引力半径随法术等级提高
 */
public class EnderImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "ender_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.30f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.40f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    // 引力触发概率
    private static final float BASE_PULL_CHANCE = 0.15f;
    private static final float CHANCE_PER_SPELLPOWER = 0.10f;

    // 引力参数
    private static final float BASE_PULL_RADIUS = 3.0f;
    private static final float PULL_RADIUS_PER_LEVEL = 1.0f;
    private static final float BASE_PULL_STRENGTH = 0.30f; // 基础强度
    private static final float PULL_STRENGTH_PER_LEVEL = 0.10f;
    private static final double MAX_PULL_SPEED = 2.0;
    private static final double Y_DAMPING = 0.25;

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.RARE)
                .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_ENDER.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new EnderEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.ender_imbued_bullet";
    }

    @Override
    protected float getDamageConversionRate(int spellLevel, LivingEntity caster) {
        float spellPower = getEntityPowerMultiplier(caster);
        return BASE_CONVERSION_RATE + ((spellPower - 1.0f) * CONVERSION_SPELLPOWER_FACTOR);
    }

    @Override
    protected float getDamageConversionRate(int spellLevel) {
        return BASE_CONVERSION_RATE;
    }

    @Override
    protected int getBaseDurationSeconds() {
        return BASE_DURATION_SECONDS;
    }

    @Override
    protected int getDurationPerLevelSeconds() {
        return DURATION_PER_LEVEL_SECONDS;
    }

    /**
     * 末影特效处理器
     */
    private class EnderEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            int radius = (int) (BASE_PULL_RADIUS + spellLevel * PULL_RADIUS_PER_LEVEL);
            return Component.translatable("spell.arcane_mag.ender_imbued_bullet.effect", radius);
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法音效接口保留，当前空实现
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();

            // 末影命中粒子：UNSTABLE_ENDER 万能粒子（紫粉色自发光）
            spawnHitParticles(level, target);

            // 概率触发引力牵引
            float spellPower = getEntityPowerMultiplier(caster);
            float pullChance = BASE_PULL_CHANCE + ((spellPower - 1.0f) * CHANCE_PER_SPELLPOWER);
            pullChance = Math.min(pullChance, 1.0f); // 限制最大 100% 概率

            if (level.random.nextFloat() < pullChance) {
                applyGravityPull(level, caster, target, spellLevel);
            }
        }

        /**
         * 末影命中粒子
         */
        private void spawnHitParticles(ServerLevel level, LivingEntity target) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            MagicManager.spawnParticles(
                    level, ParticleHelper.UNSTABLE_ENDER,
                    hitPos.x, hitPos.y, hitPos.z,
                    2,
                    target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3,
                    0.05, false
            );

        }

        private void applyGravityPull(ServerLevel level, ServerPlayer caster,
                                      LivingEntity target, int spellLevel) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            float pullRadius = BASE_PULL_RADIUS + spellLevel * PULL_RADIUS_PER_LEVEL;
            double pullRadiusSq = pullRadius * pullRadius;
            float pullStrength = BASE_PULL_STRENGTH + spellLevel * PULL_STRENGTH_PER_LEVEL;

            AABB searchBox = new AABB(
                    hitPos.x - pullRadius, hitPos.y - pullRadius * 0.5, hitPos.z - pullRadius,
                    hitPos.x + pullRadius, hitPos.y + pullRadius * 0.5, hitPos.z + pullRadius
            );

            List<LivingEntity> nearby = level.getEntitiesOfClass(
                    LivingEntity.class, searchBox,
                    e -> e != target
                            && e != caster
                            && e.isAlive()
                            && CombatTools.isValidCombatTarget(e, caster)
                            && e.distanceToSqr(hitPos) <= pullRadiusSq
            );

            for (LivingEntity entity : nearby) {
                Vec3 entityPos = entity.position();
                Vec3 direction = hitPos.subtract(entityPos);
                double distance = direction.length();

                if (distance < 0.001) continue;

                Vec3 normalizedDir = direction.scale(1.0 / distance);
                double distanceFactor = Math.max(0.0, 1.0 - distance / pullRadius);
                double finalStrength = pullStrength * distanceFactor;

                Vec3 currentMotion = entity.getDeltaMovement();
                double dx = clamp(currentMotion.x + normalizedDir.x * finalStrength, -MAX_PULL_SPEED, MAX_PULL_SPEED);
                double dy = clamp(currentMotion.y + normalizedDir.y * finalStrength * Y_DAMPING, -MAX_PULL_SPEED * 0.5, MAX_PULL_SPEED * 0.5);
                double dz = clamp(currentMotion.z + normalizedDir.z * finalStrength, -MAX_PULL_SPEED, MAX_PULL_SPEED);

                entity.setDeltaMovement(dx, dy, dz);
                entity.hurtMarked = true;

                if (entity instanceof ServerPlayer pulledPlayer) {
                    pulledPlayer.connection.send(new ClientboundSetEntityMotionPacket(pulledPlayer));
                }
            }

            // 引力漩涡粒子
            int particleCount = 3 + spellLevel * 2;
            for (int i = 0; i < particleCount; i++) {
                double angle = (Math.PI * 2 * i) / particleCount;
                double radius = pullRadius * 0.6 * (0.7 + 0.3 * level.random.nextDouble());
                double px = hitPos.x + Math.cos(angle) * radius;
                double pz = hitPos.z + Math.sin(angle) * radius;
                double py = hitPos.y + (level.random.nextDouble() - 0.5) * 0.3;

                Vec3 toCenter = hitPos.subtract(px, py, pz).normalize().scale(0.2);
                level.sendParticles(
                        ParticleTypes.WITCH,
                        px, py, pz, 1,
                        toCenter.x, toCenter.y, toCenter.z, 0.05
                );
            }
        }

        private double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}