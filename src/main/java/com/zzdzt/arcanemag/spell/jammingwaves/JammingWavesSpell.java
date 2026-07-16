package com.zzdzt.arcanemag.spell.jammingwaves;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.SpellRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 干扰波纹（Jamming Waves）—— 正面扇形防御法术
 *
 * 机制：持续施法 0.5 秒预热后，正面 150° 扇形内自动拦截攻击
 * - 弹射物：三级处理（偏移 → 捕获 → 消除），不反击
 * - 近战：击退攻击者
 * - 每次拦截消耗额外法力
 * - 持续施法期间每 tick 消耗基础维持法力
 */
public class JammingWavesSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "jamming_waves");

    private final DefaultConfig config = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
        .setMaxLevel(1)
        .setCooldownSeconds(15)
        .build();

    public static final int WARMUP_TICKS = 10; // 0.5 秒预热

    // 持续施法基础维持消耗（每 tick）
    public static final float BASE_TICK_MANA_DRAIN = 0.2f; // 每 tick 0.2 法力，约每秒 4 点

    // 每次拦截的基础法力消耗
    public static final float BASE_HIT_MANA_DRAIN = 90.0f;

    public JammingWavesSpell() {
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 2; 
        this.baseManaCost = 12;
        this.manaCostPerLevel = 1;
        this.castTime = 20 * 5;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float drain = getDrainManaPerHit(spellLevel, caster);
        return List.of(
            Component.translatable("ui.arcanemag.jamming_waves.warmup_time",
                String.format("%.1f", WARMUP_TICKS / 20.0)),
            Component.translatable("ui.arcanemag.jamming_waves.drain_per_hit",
                Utils.stringTruncation(drain, 1)),
            Component.translatable("ui.arcanemag.jamming_waves.front_arc", "150°")
        );
    }

    public static float getDrainManaPerHit(int spellLevel, LivingEntity entity) {
        float spellPowerRate = Math.max(1.0f,
            SpellRegistry.JAMMING_WAVES.get().getSpellPower(spellLevel, entity) / 10.0f);
        return BASE_HIT_MANA_DRAIN / spellPowerRate;
    }

    public static float getTickManaDrain(int spellLevel, LivingEntity entity) {
        float spellPowerRate = Math.max(1.0f,
            SpellRegistry.JAMMING_WAVES.get().getSpellPower(spellLevel, entity) / 10.0f);
        return BASE_TICK_MANA_DRAIN / spellPowerRate;
    }

    @Override
    public ResourceLocation getSpellResource() { return spellId; }

    @Override
    public DefaultConfig getDefaultConfig() { return config; }

    @Override
    public CastType getCastType() { return CastType.CONTINUOUS; }

    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.empty(); }

    @Override
    public Optional<SoundEvent> getCastFinishSound() { return Optional.empty(); }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST_ONE_HANDED;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.FINISH_ANIMATION;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                        CastSource castSource, MagicData playerMagicData) {
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
        JammingWavesState.startWarmup(entity);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity,
                                  @Nullable MagicData playerMagicData) {
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
        JammingWavesState.tickWarmup(entity);

        if (JammingWavesState.isWarmedUp(entity)) {
            // 持续施法期间每 tick 消耗维持法力
            if (playerMagicData != null) {
                float tickDrain = getTickManaDrain(spellLevel, entity);
                float currentMana = playerMagicData.getMana();
                float newMana = Math.max(0f, currentMana - tickDrain);
                playerMagicData.setMana(newMana);

                if (newMana <= 0f && entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    io.redspace.ironsspellbooks.api.util.Utils.serverSideCancelCast(serverPlayer);
                }
            }

            JammingWavesDefenseEvent.interceptNearbyProjectiles(
                level, spellLevel, entity, playerMagicData);
        }
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity,
                                      MagicData playerMagicData, boolean cancelled) {
        JammingWavesState.clear(entity);
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }
}