package com.zzdzt.arcanemag.spell.imbuedbullet;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 神圣注魔子弹
 *
 * 命中时概率召唤圣光射线：
 * 最终伤害 = 枪械原始伤害 × 玩家法术强度
 * 同一目标被 Sunbeam 轰击后，10 秒内不再触发。
 */
public class HolyImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "holy_imbued_bullet"
    );

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    // --- Sunbeam 触发配置 ---
    private static final float BASE_SUNBEAM_CHANCE = 0.05f;
    private static final float CHANCE_PER_LEVEL = 0.05f;
    private static final int SUNBEAM_COOLDOWN_TICKS = 200; //10
    private static final String SUNBEAM_COOLDOWN_TAG = "arcane_mag:holy_sunbeam_cd";

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
                return new DefaultConfig()
                .setMinRarity(SpellRarity.RARE)
                .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
                .setMaxLevel(5)
                .setCooldownSeconds(30)
                .build();
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_HOLY.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new HolyEffectProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.holy_imbued_bullet";
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
    protected int getBaseDurationSeconds() { return BASE_DURATION_SECONDS; }

    @Override
    protected int getDurationPerLevelSeconds() { return DURATION_PER_LEVEL_SECONDS; }

    private float getSunbeamChance(int spellLevel) {
        return Math.min(BASE_SUNBEAM_CHANCE + (spellLevel - 1) * CHANCE_PER_LEVEL, 1.0f);
    }



    //神圣特效处理器 

    private class HolyEffectProcessor implements ImbuedBulletEffectProcessor {

        @Override
        public MutableComponent getDescriptionComponent(int spellLevel) {
            float chance = getSunbeamChance(spellLevel) * 100;
            return Component.translatable(
                    "spell.arcane_mag.holy_imbued_bullet.effect",
                    Utils.stringTruncation(chance, 1)
            );
        }

        @Override
        public void onCast(ServerPlayer caster, int spellLevel) {
            // 施法时的瞬间特效（留空）
        }

        @Override
        public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
            ServerLevel level = (ServerLevel) caster.level();
            long gameTime = level.getGameTime();

            // 12秒冷却检查（目标级）
            if (target.getPersistentData().getLong(SUNBEAM_COOLDOWN_TAG) > gameTime) {
                return;
            }

            // 概率判定
            float chance = getSunbeamChance(spellLevel);
            if (level.random.nextFloat() >= chance) {
                return;
            }

            // 触发冷却
            target.getPersistentData().putLong(SUNBEAM_COOLDOWN_TAG, gameTime + SUNBEAM_COOLDOWN_TICKS);

            float spellPower = getEntityPowerMultiplier(caster);
            // 通过基类缓存获取原始枪械伤害
            float originalGunDamage = getCachedGunDamage(caster);

            // fallback：如果缓存丢失，用 spellDamage 反推
            if (originalGunDamage <= 0) {
                float conversionRate = getDamageConversionRate(spellLevel, caster);
                originalGunDamage = spellDamage / Math.max(conversionRate, 0.01f);
            }

            spawnImbuedSunbeam(level, caster, target, originalGunDamage, spellPower);
        }

        private void spawnImbuedSunbeam(ServerLevel level, ServerPlayer caster,
                                        LivingEntity target, float gunDamage, float spellPower) {
            Vec3 spawnPos = Utils.moveToRelativeGroundLevel(
                    level,
                    target.position().add(0, 2, 0),
                    3, 18
            );

            ImbuedSunbeamEntity sunbeam = new ImbuedSunbeamEntity(level);
            sunbeam.setOwner(caster);
            sunbeam.setTarget(target);
            sunbeam.moveTo(spawnPos);
            sunbeam.setGunDamage(gunDamage);
            sunbeam.setSpellPower(spellPower);

            level.addFreshEntity(sunbeam);

            level.playSound(
                    null,
                    sunbeam.blockPosition(),
                    SoundRegistry.SUNBEAM_WINDUP.get(),
                    SoundSource.NEUTRAL,
                    3.5f,
                    1.0f
            );
        }
    }
}
