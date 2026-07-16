package com.zzdzt.arcanemag.spell.gunenhance;

import java.util.List;
import java.util.Optional;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 枪械强化法术：
 * 
 * 在效果持续期间，玩家手持的 TACZ 枪械会获得以下增益：
 *   伤害 +15% × 等级
 *   射速 +15% × 等级
 *   瞄准速度 +15% × 等级
 *   扩散 -15% × 等级（乘法）
 */
public class GunEnhanceSpell extends AbstractSpell {

    private static final int BASE_DURATION = 20 * 10;      // 10秒基础
    private static final int DURATION_PER_LEVEL = 20 * 3;    // 每级+3秒

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "gun_enhance"
    );

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.EVOCATION_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(45)
            .build();

    public GunEnhanceSpell() {
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 5;
        this.castTime = 0; 
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float dmgPct   = (getDamageMultiplier(spellLevel) - 1) * 100;      // 20% ~ 100%
        float rpmPct   = (getRpmMultiplier(spellLevel) - 1) * 100;         // 15% ~ 75%
        float adsPct   = (getAdsMultiplier(spellLevel) - 1) * 100;         // 15% ~ 75%
        float accPct   = (1 - getInaccuracyMultiplier(spellLevel)) * 100;  // 15% ~ 75%
  
        int duration   = getDuration(spellLevel, caster) / 20;
        return List.of(
                Component.translatable("spell.arcane_mag.gun_enhance.info.bonus", Utils.stringTruncation(dmgPct, 0)),
                Component.translatable("spell.arcane_mag.info.duration", duration)
        );
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (entity instanceof ServerPlayer player) {
            // 持续时间受 spell_power 影响
            int duration = getDuration(spellLevel, entity);
            int amplifier = spellLevel - 1; // amplifier 0 对应 1 级
            player.addEffect(new MobEffectInstance(
                    EffectRegistry.GUN_ENHANCE.get(),
                    duration,
                    amplifier,
                    false, false, true
            ));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

// 各属性独立倍率

    // 伤害倍率：每级 +20%
    private float getDamageMultiplier(int spellLevel) {
        return 1.0f + spellLevel * 0.20f;
    }

    // 射速倍率：每级 +15%
    private float getRpmMultiplier(int spellLevel) {
        return 1.0f + spellLevel * 0.15f;
    }

    // 瞄准速度倍率：每级 +15%（值越小越快，所以用除法）
    private float getAdsMultiplier(int spellLevel) {
        return 1.0f + spellLevel * 0.15f;
    }

    // 扩散倍率：每级 -15%（值越小越好，所以用倒数）
    private float getInaccuracyMultiplier(int spellLevel) {
        return 1.0f / (1.0f + spellLevel * 0.15f);
    }

    /**
     * 计算持续时间，受 spell_power 属性影响
     */
    private int getDuration(int spellLevel, LivingEntity caster) {
        int baseDuration = BASE_DURATION + (spellLevel - 1) * DURATION_PER_LEVEL;
        return (int)(baseDuration * getEntityPowerMultiplier(caster));
    }
}