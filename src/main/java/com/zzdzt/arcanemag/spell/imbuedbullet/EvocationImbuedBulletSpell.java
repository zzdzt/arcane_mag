package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
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
 * 唤魔注魔子弹
 *
 * 机制：
 * - 命中时额外造成唤魔学派法术伤害
 * - 概率使目标获得脆弱效果：受到的所有伤害增加
 * - 增伤幅度随法术等级提升
 */
public class EvocationImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "evocation_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    // 脆弱效果触发概率
    private static final float BASE_VULNERABILITY_CHANCE = 0.15f;
    private static final float CHANCE_PER_SPELLPOWER = 0.20f;

    // 脆弱持续时间：固定 6 秒，不随等级变化
    private static final int VULNERABILITY_DURATION_SECONDS = 6;
    private static final int VULNERABILITY_DURATION_TICKS = VULNERABILITY_DURATION_SECONDS * 20;

    // 增伤比例映射：amplifier -> 增伤百分比
    // 等级1=20%, 等级2=35%, 等级3=50%, 等级4=65%, 等级5=80%
    private static final float[] DAMAGE_INCREASE_TABLE = {
        0.20f,  // amp 0 (等级1): +20%
        0.35f,  // amp 1 (等级2): +35%
        0.50f,  // amp 2 (等级3): +50%
        0.65f,  // amp 3 (等级4): +65%
        0.80f   // amp 4 (等级5): +80%
    };

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.RARE)
                .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_EVOCATION.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new EvocationEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.evocation_imbued_bullet";
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
     * 唤魔特效处理器
     */
    private class EvocationEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            float chance = Math.min(BASE_VULNERABILITY_CHANCE + (spellLevel - 1) * 0.05f, 1.0f) * 100;
            float increase = DAMAGE_INCREASE_TABLE[spellLevel - 1] * 100;
            return Component.translatable(
                    "spell.arcane_mag.evocation_imbued_bullet.effect",
                    Utils.stringTruncation(chance, 0),
                    Utils.stringTruncation(increase, 0)
            );
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法时的瞬间特效（留空待定）
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();

            // 概率施加脆弱效果
            float spellPower = getEntityPowerMultiplier(caster);
            float vulnChance = BASE_VULNERABILITY_CHANCE + ((spellPower - 1.0f) * CHANCE_PER_SPELLPOWER);
            vulnChance = Math.min(vulnChance, 1.0f);

            if (level.random.nextFloat() < vulnChance) {
                applyVulnerability(level, target, spellLevel);
            }

            // 唤魔粒子特效
            spawnHitParticles(level, target);
        }

        private void applyVulnerability(ServerLevel level, LivingEntity target, int spellLevel) {
            // 等级1~5 对应 amplifier 0~4
            int amplifier = spellLevel - 1;
            if (amplifier < 0) amplifier = 0;
            if (amplifier >= DAMAGE_INCREASE_TABLE.length) {
                amplifier = DAMAGE_INCREASE_TABLE.length - 1;
            }

            // 施加脆弱效果，固定 5 秒持续时间
            target.addEffect(new MobEffectInstance(
                    EffectRegistry.VULNERABILITY.get(),
                    VULNERABILITY_DURATION_TICKS,
                    amplifier,
                    false, false, true
            ));

            float increase = DAMAGE_INCREASE_TABLE[amplifier];
        }

        private void spawnHitParticles(ServerLevel level, LivingEntity target) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            MagicManager.spawnParticles(
                    level, ParticleHelper.WISP,
                    hitPos.x, hitPos.y, hitPos.z,
                    5,
                    target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3,
                    0.05, false
            );

            level.sendParticles(
                    ParticleTypes.ENCHANTED_HIT,
                    hitPos.x, hitPos.y, hitPos.z,
                    4,
                    0.2, 0.3, 0.2,
                    0.03
            );
        }
    }
}