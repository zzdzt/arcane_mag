package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 冰霜注魔子弹
 * 
 */
public class FrostImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "frost_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.25f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;
    private static final float BASE_CHILL_CHANCE = 0.10f;
    private static final float CHANCE_PER_SPELLPOWER = 0.10f;
    private static final int FREEZE_TICKS_PER_HIT = 20;

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return new DefaultConfig()
                .setMinRarity(SpellRarity.RARE)
                .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_ICE.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new FrostEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.frost_imbued_bullet";
    }

    @Override
    protected float getDamageConversionRate(int spellLevel, LivingEntity caster) {
        float spellPower = getEntityPowerMultiplier(caster);
        return (BASE_CONVERSION_RATE + (spellPower - 1.0f) * CONVERSION_SPELLPOWER_FACTOR);
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
     * 冰霜特效处理器
     */
    private class FrostEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            int freezeDuration = getBaseDurationSeconds() + (spellLevel - 1) * getDurationPerLevelSeconds();
            return Component.translatable("spell.arcane_mag.frost_imbued_bullet.effect", freezeDuration);
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法时的瞬间特效（如果有）
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();

            // 1. 概率施加铁魔法的 "寒冷" 效果，累积冻结进度条
            float spellPower = getEntityPowerMultiplier(caster);
            float actualSpellPower = spellPower + 1.0f;
            float chillChance = BASE_CHILL_CHANCE + ((actualSpellPower - 1.0f) * CHANCE_PER_SPELLPOWER);
            chillChance = Math.min(chillChance, 1.0f); // 限制最大 100% 概率

            if (level.random.nextFloat() < chillChance) {
                target.addEffect(new MobEffectInstance(
                        MobEffectRegistry.CHILLED.get(),
                        100, 0, false, false, true
                ));
            }

            // 2. 核心硬控判定：如果目标冻结进度条已满
            if (target.isFullyFrozen()) {
                target.setTicksFrozen(target.getTicksRequiredToFreeze());

                // 施加 "绝对零度" 效果压制 99% 移速
                target.addEffect(new MobEffectInstance(
                        EffectRegistry.ABSOLUTE_ZERO.get(),
                        20, 0, false, false, true
                ));

                if (level.random.nextInt(5) == 0) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundRegistry.FROSTBITE_FREEZE.get(), target.getSoundSource(), 0.5f, 1.5f);
                }
            } else {
                // 3. 如果还没满，增加冻结刻数，加速冰封
                target.setTicksFrozen(target.getTicksFrozen() + FREEZE_TICKS_PER_HIT);
            }

            // 4. 播放冰碎音效
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, target.getSoundSource(), 1.0f, 1.5f);
        }
    }
}