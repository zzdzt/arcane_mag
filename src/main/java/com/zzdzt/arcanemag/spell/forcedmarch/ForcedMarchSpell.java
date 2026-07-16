package com.zzdzt.arcanemag.spell.forcedmarch;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.ForcedMarchMobility;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ForcedMarchSpell extends AbstractSpell {

    private static final int EFFECT_REFRESH_TICKS = 5;
    private static final int POST_CAST_EFFECT_DURATION = 20 * 20; // 20秒基础
    private static final int POST_CAST_RESISTANCE_AMPLIFIER = 1;   // 抗性提升 II
    private static final int POST_CAST_HASTE_AMPLIFIER = 1;       // 急迫 II

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "forced_march"
    );

    private final DefaultConfig config = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
        .setMaxLevel(5)
        .setCooldownSeconds(15)
        .build();

    public ForcedMarchSpell() {
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 6;
        this.manaCostPerLevel = 0;
        this.castTime = 20 * 30;        
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("attribute.modifier.plus.1", 
                Utils.stringTruncation(getPercentSpeed(spellLevel), 0),
                Component.translatable("attribute.name.generic.movement_speed"))
        );
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, LivingEntity entity) {
        return getCastTime(spellLevel);
    }

    /**
     * 计算速度加成百分比：只与 spellLevel 有关
     */
    private float getPercentSpeed(int spellLevel) {
        return spellLevel * (float) ForcedMarchMobility.SPEED_PER_LEVEL * 100;
    }

    /**
     * 计算后效持续时间，受 spell_power 属性影响
     */
    private int getPostCastDuration(int spellLevel, LivingEntity caster) {
        return (int)(POST_CAST_EFFECT_DURATION * getEntityPowerMultiplier(caster));
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
        return CastType.CONTINUOUS;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return AnimationHolder.none();
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.none();
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, 
                                  @Nullable MagicData playerMagicData) {
        // amplifier = spellLevel - 1，对应 Speed I-V
        entity.addEffect(new MobEffectInstance(
            EffectRegistry.FORCED_MARCH_MOBILITY.get(),
            EFFECT_REFRESH_TICKS,
            spellLevel - 1,
            false,
            true,
            true
        ));

        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, 
                       CastSource castSource, MagicData playerMagicData) {
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);

        int duration = getPostCastDuration(spellLevel, entity);

        // 引导结束后给予抗性提升 II + 急迫 II
        entity.addEffect(new MobEffectInstance(
            MobEffects.DAMAGE_RESISTANCE,
            duration,
            POST_CAST_RESISTANCE_AMPLIFIER,
            false,
            true,
            true
        ));

        entity.addEffect(new MobEffectInstance(
            MobEffects.DIG_SPEED,
            duration,
            POST_CAST_HASTE_AMPLIFIER,
            false,
            true,
            true
        ));
    }
}