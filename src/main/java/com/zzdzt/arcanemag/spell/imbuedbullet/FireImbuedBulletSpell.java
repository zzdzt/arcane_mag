package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
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
 * 火焰注魔子弹
 *
 * 机制：
 * - 命中时额外造成火焰学派法术伤害
 * - 若目标已处于燃烧状态，转化伤害翻倍（额外造成等额伤害）
 * - 概率点燃目标并施加破甲（Rend）效果
 */
public class FireImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "fire_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    // 点燃与破甲触发概率
    private static final float BASE_EFFECT_CHANCE = 0.20f;
    private static final float CHANCE_PER_SPELLPOWER = 0.15f;

    // 点燃参数
    private static final int BASE_FIRE_TICKS = 60;      // 3秒
    private static final int MAX_FIRE_TICKS = 160;      // 8秒
    private static final int FIRE_TICKS_PER_LEVEL = 20; // 每级+1秒

    // 破甲参数
    // RendEffect: ARMOR_PER_LEVEL = -0.05, MULTIPLY_TOTAL
    // 实际削弱 = amplifier × 5%，amplifier=8 → 40% 削弱
    private static final int REND_AMPLIFIER = 8;
    private static final int REND_DURATION_SECONDS = 3;
    private static final int REND_DURATION_PER_LEVEL = 1; // 每级+1秒

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.RARE)
                .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_FIRE.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new FireEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.fire_imbued_bullet";
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
     * 火焰特效处理器
     */
    private class FireEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            float chance = Math.min(BASE_EFFECT_CHANCE + (spellLevel - 1) * 0.05f, 1.0f) * 100;
            return Component.translatable(
                    "spell.arcane_mag.fire_imbued_bullet.effect",
                    Utils.stringTruncation(chance, 0)
            );
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法时的瞬间特效（留空待定）
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();

            // 概率触发点燃 + 破甲
            float spellPower = getEntityPowerMultiplier(caster);
            float effectChance = BASE_EFFECT_CHANCE + ((spellPower - 1.0f) * CHANCE_PER_SPELLPOWER);
            effectChance = Math.min(effectChance, 1.0f);

            if (level.random.nextFloat() < effectChance) {
                applyIgniteAndRend(level, target, spellLevel);
            }

            // 燃烧状态伤害翻倍：额外造成一次等额法术伤害
            if (target.isOnFire()) {
                applyBurningBonusDamage(level, caster, target, spellDamage);
            }

            // 火焰粒子特效
            spawnHitParticles(level, target);
        }

        private void applyIgniteAndRend(ServerLevel level, LivingEntity target, int spellLevel) {
            // 点燃目标：3~8秒，随等级提升
            int fireTicks = Math.min(BASE_FIRE_TICKS + (spellLevel - 1) * FIRE_TICKS_PER_LEVEL, MAX_FIRE_TICKS);
            target.setRemainingFireTicks(fireTicks);

            // 施加破甲（Rend）：固定40%护甲削弱，持续3~7秒
            int rendDurationTicks = (REND_DURATION_SECONDS + (spellLevel - 1) * REND_DURATION_PER_LEVEL) * 20;
            target.addEffect(new MobEffectInstance(
                    MobEffectRegistry.REND.get(),
                    rendDurationTicks,
                    REND_AMPLIFIER,
                    false, false, true
            ));
        }

        private void applyBurningBonusDamage(ServerLevel level, ServerPlayer caster,
                                              LivingEntity target, float baseSpellDamage) {
            SpellDamageSource damageSource = getDamageSource(caster, caster);

            int oldInvulnerableTime = target.invulnerableTime;
            float oldLastHurt = getLastHurt(target);

            boolean hit = false;
            try {
                target.invulnerableTime = 0;
                setLastHurt(target, 0);
                hit = target.hurt(damageSource, baseSpellDamage);
            } finally {
                if (!hit) {
                    target.invulnerableTime = oldInvulnerableTime;
                    setLastHurt(target, oldLastHurt);
                }
            }

            if (hit) {
                spawnBonusHitParticles(level, target);
            }
        }

        private void spawnHitParticles(ServerLevel level, LivingEntity target) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            MagicManager.spawnParticles(
                    level, ParticleHelper.EMBERS,
                    hitPos.x, hitPos.y, hitPos.z,
                    6,
                    target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3,
                    0.05, false
            );

            level.sendParticles(
                    ParticleTypes.FLAME,
                    hitPos.x, hitPos.y, hitPos.z,
                    3,
                    0.2, 0.3, 0.2,
                    0.02
            );
        }

        private void spawnBonusHitParticles(ServerLevel level, LivingEntity target) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            MagicManager.spawnParticles(
                    level, ParticleHelper.FIRE,
                    hitPos.x, hitPos.y, hitPos.z,
                    8,
                    target.getBbWidth() * 0.4, target.getBbHeight() * 0.4, target.getBbWidth() * 0.4,
                    0.08, false
            );

            level.sendParticles(
                    ParticleTypes.LAVA,
                    hitPos.x, hitPos.y, hitPos.z,
                    2,
                    0.3, 0.4, 0.3,
                    0.05
            );
        }
    }
}