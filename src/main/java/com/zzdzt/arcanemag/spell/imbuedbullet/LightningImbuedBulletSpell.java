package com.zzdzt.arcanemag.spell.imbuedbullet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.particle.InkZapParticleOption;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import com.zzdzt.arcanemag.utils.CombatTools;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 雷电注魔子弹法术。
 *
 */
public class LightningImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "lightning_imbued_bullet");

    private static final float CHAIN_RANGE = 4.0f;
    private static final double CHAIN_RANGE_SQ = CHAIN_RANGE * CHAIN_RANGE;
    private static final float FIRST_CHAIN_FALLOFF = 1.0f;
    private static final float SUBSEQUENT_CHAIN_FALLOFF = 0.8f;
    private static final int MAX_CHAIN_DEPTH = 3;
    private static final int MAX_CHAINS_PER_WAVE = 2;

    private static final float ZAP_WIDTH_CHAIN = 0.06f;
    private static final int ZAP_COLOR = 2;

    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.35f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;
    private static final float BASE_CHAIN_COUNT = 1.0f;
    private static final float CHAIN_COUNT_PER_LEVEL = 0.5f;

    @Override
    public ResourceLocation getSpellResource() { return SPELL_ID; }

    @Override
    public DefaultConfig getDefaultConfig() {
                return new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_LIGHTNING.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new LightningEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.lightning_imbued_bullet";
    }

    @Override
    protected float getDamageConversionRate(int spellLevel, LivingEntity caster) {
        float spellPower = getEntityPowerMultiplier(caster);
        return (BASE_CONVERSION_RATE + ((spellPower - 1.0f) * CONVERSION_SPELLPOWER_FACTOR));
    }

    @Override
    protected float getDamageConversionRate(int spellLevel) {
        return BASE_CONVERSION_RATE;
    }

    @Override
    protected int getBaseDurationSeconds() { return BASE_DURATION_SECONDS; }

    @Override
    protected int getDurationPerLevelSeconds() { return DURATION_PER_LEVEL_SECONDS; }

    protected int getChainCount(int spellLevel) {
        return (int) Math.ceil(BASE_CHAIN_COUNT + spellLevel * CHAIN_COUNT_PER_LEVEL);
    }

    private static class LightningEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            int chains = (int) Math.ceil(BASE_CHAIN_COUNT + spellLevel * CHAIN_COUNT_PER_LEVEL);
            return Component.translatable("spell.arcane_mag.lightning_imbued_bullet.effect", chains);
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target,
                          float spellDamage, int spellLevel) {

            ServerLevel level = (ServerLevel) caster.level();
            long gameTime = level.getGameTime();

            LightningImbuedBulletSpell spell = (LightningImbuedBulletSpell)
                io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(SPELL_ID);

            int maxChains = spell.getChainCount(spellLevel);
            if (maxChains <= 0) return;

            Set<Entity> processed = new HashSet<>(8);
            processed.add(target);
            processed.add(caster);

            List<LivingEntity> currentWave = new ArrayList<>(2);
            currentWave.add(target);
            float currentDamage = spellDamage * FIRST_CHAIN_FALLOFF;

            for (int depth = 0; depth < MAX_CHAIN_DEPTH && !currentWave.isEmpty(); depth++) {
                List<LivingEntity> nextWave = new ArrayList<>(2);

                for (LivingEntity source : currentWave) {
                    if (nextWave.size() >= MAX_CHAINS_PER_WAVE) break;

                    LivingEntity next = findBestChainTarget(level, caster, source, processed, gameTime);
                    if (next == null) continue;

                    // 确保连锁伤害有下限
                    float chainDamage = Math.max(currentDamage, 1.0f);

                    // 使用 SpellDamageSource 并临时重置无敌帧
                    SpellDamageSource chainSource = spell.getDamageSource(caster, caster);

                    int oldInvulnerableTime = next.invulnerableTime;
                    float oldLastHurt = getLastHurt(next);
                    boolean chainHit = false;

                    try {
                        // 重置无敌帧
                        next.invulnerableTime = 0;
                        setLastHurt(next, 0);

                        chainHit = next.hurt(chainSource, chainDamage);
                    } finally {
                        if (!chainHit) {
                            next.invulnerableTime = oldInvulnerableTime;
                            setLastHurt(next, oldLastHurt);
                        }
                    }

                    if (chainHit) {
                        spawnChainEffects(level, source, next, depth);
                        markTargetCooldown(next, gameTime);

                        processed.add(next);
                        nextWave.add(next);
                    }
                }

                currentWave = nextWave;
                currentDamage *= SUBSEQUENT_CHAIN_FALLOFF;
            }
        }

        private LivingEntity findBestChainTarget(ServerLevel level, ServerPlayer caster,
                                                  LivingEntity source, Set<Entity> excluded,
                                                  long gameTime) {
            AABB searchBox = source.getBoundingBox().inflate(CHAIN_RANGE);

            List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != source && e != caster && e.isAlive()
                    && CombatTools.isValidCombatTarget(e, caster)
                    && !excluded.contains(e)
                    && !isOnCooldown(e, gameTime)
                    && e.distanceToSqr(source) <= CHAIN_RANGE_SQ
            );

            if (candidates.isEmpty()) return null;

            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            for (LivingEntity candidate : candidates) {
                double d = candidate.distanceToSqr(source);
                if (d < closestDist) {
                    closestDist = d;
                    closest = candidate;
                }
            }
            return closest;
        }

        private static final String COOLDOWN_TAG = "arcane_mag:imbued_chain_cd";
        private static final int TARGET_COOLDOWN_TICKS = 5;

        private boolean isOnCooldown(Entity entity, long gameTime) {
            return entity.getPersistentData().getLong(COOLDOWN_TAG) > gameTime;
        }

        private void markTargetCooldown(Entity entity, long gameTime) {
            entity.getPersistentData().putLong(COOLDOWN_TAG, gameTime + TARGET_COOLDOWN_TICKS);
        }

        private void spawnHitEffects(ServerLevel level, LivingEntity target) {
            Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

            MagicManager.spawnParticles(
                level, ParticleHelper.ELECTRICITY,
                hitPos.x, hitPos.y, hitPos.z,
                4,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.4, target.getBbWidth() * 0.4,
                0.15, false
            );

            MagicManager.spawnParticles(
                level, ParticleTypes.ELECTRIC_SPARK,
                hitPos.x, hitPos.y, hitPos.z,
                2,
                0.2, 0.2, 0.2,
                0.1, false
            );
        }

        private void spawnChainEffects(ServerLevel level, LivingEntity source,
                                        LivingEntity target, int chainDepth) {
            Vec3 start = new Vec3(source.xOld, source.yOld + source.getBbHeight() * 0.5, source.zOld);
            Vec3 dest = target.position().add(0, target.getBbHeight() * 0.5, 0);

            float width = Math.max(0.04f, ZAP_WIDTH_CHAIN - chainDepth * 0.01f);
            level.sendParticles(
                new InkZapParticleOption(dest, width, ZAP_COLOR),
                start.x, start.y, start.z, 1, 0, 0, 0, 0
            );

            int particleCount = Math.max(1, 4 - chainDepth * 2);
            MagicManager.spawnParticles(
                level, ParticleHelper.ELECTRICITY,
                dest.x, dest.y, dest.z,
                particleCount,
                target.getBbWidth() * 0.2, target.getBbHeight() * 0.2, target.getBbWidth() * 0.2,
                0.08, false
            );
        }
    }
}