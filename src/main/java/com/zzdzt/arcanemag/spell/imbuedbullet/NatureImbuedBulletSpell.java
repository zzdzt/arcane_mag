package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.spells.poison_cloud.PoisonCloud;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 自然注魔子弹
 *
 * 机制：
 * - 子弹命中时额外造成自然学派法术伤害
 * - 概率触发以下两种效果之一：
 *   1. 毒云+中毒：在目标位置生成毒云，造成持续中毒伤害
 *   2. 枯萎：使目标造成的伤害减少+减疗
 * - 触发概率随法术强度提高
 * - 效果强度随法术等级提高
 */
public class NatureImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "nature_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    // 自然效果触发概率
    private static final float BASE_EFFECT_CHANCE = 0.20f;
    private static final float CHANCE_PER_SPELLPOWER = 0.10f;

    // 毒云参数
    private static final float POISON_CLOUD_DAMAGE_MULTIPLIER = 0.10f;
    private static final int POISON_CLOUD_DURATION_SECONDS = 30 * 1;// 20 * 1.5
    private static final int POISON_EFFECT_DURATION_SECONDS = 5;

    // 枯萎参数（固定值，不随等级变化）
    private static final int BLIGHT_DURATION_SECONDS = 5;
    // BlightEffect: DAMAGE_PER_LEVEL = -0.05f, HEALING_PER_LEVEL = -0.10f
    // 目标效果：降伤40% → 需要 amplifier 7 (8层: 8 × 5% = 40%)
    // 目标效果：减疗80% → 需要 amplifier 7 (8层: 8 × 10% = 80%)
    private static final int BLIGHT_AMPLIFIER = 7;

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.UNCOMMON)
                .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_NATURE.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new NatureEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.nature_imbued_bullet";
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

    // 自然特效处理器 

    private class NatureEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            return Component.empty();
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法粒子效果待定
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();

            // 概率触发自然效果（受法术强度影响）
            float effectChance = getEffectChance(spellLevel, caster);
            if (level.random.nextFloat() >= effectChance) {
                return;
            }

            // 随机选择两种效果之一
            boolean isPoison = level.random.nextBoolean();

            if (isPoison) {
                applyPoisonSplash(level, caster, target, spellLevel, spellDamage);
            } else {
                applyBlight(level, caster, target, spellLevel);
            }
        }

        /**
         * 实际效果触发概率（受法术强度影响）
         */
        private float getEffectChance(int spellLevel, ServerPlayer caster) {
            float spellPower = getEntityPowerMultiplier(caster);
            return Math.min(BASE_EFFECT_CHANCE + ((spellPower - 1.0f) * CHANCE_PER_SPELLPOWER), 1.0f);
        }

        /**
         * 效果1：毒云 + 中毒
         * 直接生成 PoisonCloud，绕过 PoisonSplash 的 0.1f 削弱
         */
        private void applyPoisonSplash(ServerLevel level, ServerPlayer caster,
                                        LivingEntity target, int spellLevel, float spellDamage) {
            // 获取原始枪械伤害（通过基类缓存）
            float originalGunDamage = getCachedGunDamage(caster);
            if (originalGunDamage <= 0) {
                originalGunDamage = spellDamage; // fallback
            }

            // 计算毒云参数
            float cloudDamage = originalGunDamage * POISON_CLOUD_DAMAGE_MULTIPLIER;
            int cloudDurationTicks = POISON_CLOUD_DURATION_SECONDS ;

            PoisonCloud cloud = new PoisonCloud(level);
            cloud.setOwner(caster);
            cloud.setDuration(cloudDurationTicks);
            cloud.setDamage(cloudDamage);
            cloud.moveTo(target.position().add(0, 0.1, 0));
            level.addFreshEntity(cloud);

            // 同时给目标直接施加中毒效果
            target.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.POISON,
                    POISON_EFFECT_DURATION_SECONDS * 20,
                    Math.min(spellLevel - 1, 2),
                    false, false, true
            ));

            // 毒云视觉效果
            MagicManager.spawnParticles(
                    level, ParticleHelper.POISON_CLOUD,
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    15,
                    1.5f, 0.3, 1.5f,
                    0.1, false
            );
            MagicManager.spawnParticles(
                    level, ParticleHelper.ACID,
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    20,
                    1.0f, 0.5, 1.0f,
                    0.2, false
            );
        }

        /**
         * 效果2：枯萎（减伤 + 减疗）
         * 固定数值：降伤40%，减疗80%，持续5秒，所有等级相同
         */
        private void applyBlight(ServerLevel level, ServerPlayer caster,
                                  LivingEntity target, int spellLevel) {
            int blightDurationTicks = BLIGHT_DURATION_SECONDS;

            // 施加铁魔法的 Blight 效果
            // amplifier = 7 → 8层效果
            // 降伤 = 8 × 5% = 40%
            // 减疗 = 8 × 10% = 80%
            target.addEffect(new MobEffectInstance(
                    MobEffectRegistry.BLIGHT.get(),
                    blightDurationTicks,
                    BLIGHT_AMPLIFIER,
                    false, false, true
            ));

            // 枯萎粒子效果（紫色）
            MagicManager.spawnParticles(
                    level, ParticleTypes.WITCH,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    6,
                    target.getBbWidth() * 0.4, target.getBbHeight() * 0.4, target.getBbWidth() * 0.4,
                    0.05, false
            );
        }
    }
}