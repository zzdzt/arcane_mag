package com.zzdzt.arcanemag.spell.tacticaldash;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class TacticalDashSpell extends AbstractSpell {

    private static final int BASE_DURATION_TICKS = 20 * 7;
    private static final int DURATION_PER_LEVEL_TICKS = 10 * 1;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
            ArcaneMag.MODID, "tactical_dash"
    );

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(10)
            .build();

    public TacticalDashSpell() {
        this.baseManaCost = 25;  
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int durationSec = getDurationTicks(spellLevel) / 20;
        float headshotAdd = getHeadshotBonus(spellLevel);
        return List.of(
            Component.translatable("spell.arcane_mag.tactical_dash.info.headshot", Utils.stringTruncation(headshotAdd, 1)),
            Component.translatable("spell.arcane_mag.info.duration", durationSec)
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
        return Optional.of(SoundEvents.ENDERMAN_TELEPORT);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!(entity instanceof ServerPlayer player)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        // 向前冲刺：取水平视角方向
        Vec3 look = player.getLookAngle();
        Vec3 dashDir = new Vec3(look.x, 0, look.z).normalize();

        // 冲量计算
        double dashDistance = getDashDistance(spellLevel);
        Vec3 impulse = dashDir.scale(dashDistance * 0.33).add(0, 0.15, 0);

        // 保留部分原动量 + 新冲量
        Vec3 newMotion = player.getDeltaMovement().scale(0.2).add(impulse);
        player.setDeltaMovement(newMotion);
        player.hurtMarked = true;

        // 重置坠落距离 + 摔落保护
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(
                MobEffectRegistry.FALL_DAMAGE_IMMUNITY.get(),
                20, 0, false, false, true
        ));

        // 音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.2f);

        // 爆头增益效果
        int duration = getDurationTicks(spellLevel);
        int amplifier = spellLevel - 1;
        player.addEffect(new MobEffectInstance(
                EffectRegistry.TACTICAL_DASH.get(),
                duration, amplifier, false, false, true
        ));

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    //数值 

    public static double getDashDistance(int spellLevel) {
        return 4.0 + (spellLevel - 1) * 0.5;
    }

    public static int getDurationTicks(int spellLevel) {
        return BASE_DURATION_TICKS + (spellLevel - 1) * DURATION_PER_LEVEL_TICKS;
    }

    public static float getHeadshotBonus(int spellLevel) {
        return 0.5f + (spellLevel - 1) * 0.1f;
    }

    //客户端方向接收 
    public static void receiveDashDirection(ServerPlayer player, Vec3 validatedDirection) {
        if (player.hasEffect(EffectRegistry.TACTICAL_DASH.get())) {
            double dashDistance = getDashDistance(
                player.getEffect(EffectRegistry.TACTICAL_DASH.get()).getAmplifier() + 1
            );
            Vec3 impulse = validatedDirection.scale(dashDistance * 0.33).add(0, 0.15, 0);
            Vec3 newMotion = player.getDeltaMovement().scale(0.2).add(impulse);
            player.setDeltaMovement(newMotion);
            player.hurtMarked = true;
            player.resetFallDistance();
        }
    }
}