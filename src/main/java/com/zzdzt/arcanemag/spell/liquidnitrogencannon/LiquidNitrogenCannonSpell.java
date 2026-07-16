package com.zzdzt.arcanemag.spell.liquidnitrogencannon;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.LiquidNitrogenMarkedEffect;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;


public class LiquidNitrogenCannonSpell extends AbstractSpell {

    // 范围参数 
    private static final float RANGE = 8f;
    private static final float HALF_ANGLE_COS = (float) Math.cos(Math.toRadians(45));

    // 击退参数 
    private static final float KNOCKBACK_BASE = 1.2f;
    // 击退抗性衰减范围：最低 10%，最高 100%
    private static final float KB_RESIST_MIN = 0.1f;
    private static final float KB_RESIST_MAX = 1.0f;

    // 效果参数 
    private static final int EFFECT_DURATION = 20 * 8; // 8 秒 = 160 ticks

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "liquid_nitrogen_cannon"
    );

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
        .setMaxLevel(3)
        .setCooldownSeconds(15)
        .build();

    public LiquidNitrogenCannonSpell() {
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 5;
        this.castTime = 0;
        this.baseManaCost = 40;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.arcane_mag.info.duration", EFFECT_DURATION / 20),
            Component.translatable("spell.arcane_mag.liquid_nitrogen_cannon.info.knockback",
                Utils.stringTruncation(getKnockbackStrength(spellLevel, caster), 1))
        );
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_EXPLODE);
    }

    // 核心施法逻辑

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide) {
            Vec3 eyePos = entity.getEyePosition();
            Vec3 forward = entity.getForward();

            float knockbackStrength = getKnockbackStrength(spellLevel, entity);
            float powerMult = getEntityPowerMultiplier(entity);

            // 搜索范围内实体（围绕施法者的 AABB，再按锥形过滤）
            AABB searchBox = entity.getBoundingBox().inflate(RANGE);
            List<Entity> entities = level.getEntities(entity, searchBox);

            for (Entity targetEntity : entities) {
                if (!(targetEntity instanceof LivingEntity target)) continue;
                if (!target.isAlive() || !target.isPickable()) continue;

                // 距离过滤
                if (entity.distanceToSqr(target) >= RANGE * RANGE) continue;

                // 锥形角度过滤：目标方向与朝向的点积 >= cos(半角)
                Vec3 toTarget = target.position().subtract(eyePos);
                double dist = toTarget.length();
                if (dist < 0.1) continue;
                Vec3 toTargetNorm = toTarget.scale(1.0 / dist);
                if (toTargetNorm.dot(forward) < HALF_ANGLE_COS) continue;

                // 视线检测
                if (!Utils.hasLineOfSight(level, eyePos,
                        target.getBoundingBox().getCenter(), true)) continue;

                // 友军火焰检测
                if (DamageSources.isFriendlyFireBetween(entity, target)) continue;

                // 击退（受击退抗性衰减）
                // 纯水平方向推开（不依赖重力/坠落伤害）
                Vec3 knockbackDir = new Vec3(
                    target.getX() - entity.getX(),
                    0,
                    target.getZ() - entity.getZ()
                );
                if (knockbackDir.lengthSqr() < 0.001) {
                    // 目标恰好在施法者正上方，用朝向作为击退方向
                    knockbackDir = new Vec3(forward.x, 0, forward.z);
                }
                knockbackDir = knockbackDir.normalize();

                float kbFactor = Utils.clampedKnockbackResistanceFactor(
                    target, KB_RESIST_MIN, KB_RESIST_MAX);
                Vec3 knockback = knockbackDir.scale(knockbackStrength * kbFactor);
                target.setDeltaMovement(target.getDeltaMovement().add(knockback));
                target.hurtMarked = true; 

                // 施加液氮标记效果
                LiquidNitrogenMarkedEffect.storeCasterInfo(target, entity, powerMult);
                target.addEffect(new MobEffectInstance(
                    EffectRegistry.LIQUID_NITROGEN_MARKED.get(),
                    EFFECT_DURATION, spellLevel - 1,
                    false, false, true
                ));

                // 目标命中粒子（雪片） 
                MagicManager.spawnParticles(level, ParticleTypes.SNOWFLAKE,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.5f,
                    target.getZ(),
                    20,
                    target.getBbWidth() * 0.5f,
                    target.getBbHeight() * 0.5f,
                    target.getBbWidth() * 0.5f,
                    0.03, false
                );
            }

            // 锥形喷雾粒子（云雾效果） 
            Vec3 sprayCenter = eyePos.add(forward.scale(RANGE * 0.5));
            MagicManager.spawnParticles(level, ParticleTypes.CLOUD,
                sprayCenter.x, sprayCenter.y, sprayCenter.z,
                30,
                RANGE * 0.3f, RANGE * 0.3f, RANGE * 0.3f,
                0.02, false
            );

            // 音效 
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.8f);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    // 数值计算 

    public float getKnockbackStrength(int spellLevel, LivingEntity caster) {
        return KNOCKBACK_BASE + getSpellPower(spellLevel, caster) * 0.05f;
    }
}
