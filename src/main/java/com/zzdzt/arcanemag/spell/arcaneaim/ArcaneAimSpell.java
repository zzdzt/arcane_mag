package com.zzdzt.arcanemag.spell.arcaneaim;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.ArcaneAimEffect;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
/**
 * 智慧核心：
 * - 实现锁定目标，枪械子弹自瞄
 */
public class ArcaneAimSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "arcane_aim"
    );

    private static final int BASE_DURATION_TICKS = 100;   // 5秒基础
    private static final int DURATION_PER_LEVEL = 40;      
    private static final int TARGET_RANGE = 48;
    private static final float TARGET_ANGLE = 0.35f;

    private final DefaultConfig config = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(SchoolRegistry.ENDER_RESOURCE)
        .setMaxLevel(3)
        .setCooldownSeconds(35)
        .build();

    public ArcaneAimSpell() {
        this.baseManaCost = 60;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = 0;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int duration = getDuration(spellLevel, caster);
        float coneAngle = getAimConeAngle(spellLevel);
        return List.of(
            Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks(duration,1)),
            Component.translatable("spell.arcane_mag.arcane_aim.info.cone", String.format("%.0f", coneAngle))
        );
    }

    @Override
    public ResourceLocation getSpellResource() { return spellId; }
    @Override
    public DefaultConfig getDefaultConfig() { return config; }
    @Override
    public CastType getCastType() { return CastType.INSTANT; }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return Utils.preCastTargetHelper(level, entity, playerMagicData, this, TARGET_RANGE, TARGET_ANGLE);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!(entity instanceof Player player)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        LivingEntity target = getTargetFromCastData(playerMagicData, level);
        if (target == null || !target.isAlive() || target instanceof Player) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        int duration = getDuration(spellLevel, player);
        player.addEffect(new MobEffectInstance(
            EffectRegistry.ARCANE_AIM.get(), duration, spellLevel - 1,
            false, true, true
        ));
        player.getPersistentData().putUUID(ArcaneAimEffect.AIM_TARGET_TAG, target.getUUID());

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Vec3 pos = target.position().add(0, target.getBbHeight() / 2, 0);
            serverLevel.sendParticles(
                serverPlayer,  // 指定接收玩家
                ParticleTypes.WITCH,
                true,          // force - 是否强制显示（无视粒子设置）
                pos.x, pos.y, pos.z,
                30,
                0.4, 0.4, 0.4,
                0.05
            );
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Nullable
    private LivingEntity getTargetFromCastData(MagicData magicData, Level level) {
        var castData = magicData.getAdditionalCastData();
        if (!(castData instanceof TargetEntityCastData targetData)) return null;
        if (!(level instanceof ServerLevel serverLevel)) return null;

        Entity target = targetData.getTarget(serverLevel);
        return (target instanceof LivingEntity living && living.isAlive()) ? living : null;
    }

    private int getDuration(int spellLevel, LivingEntity caster) {
        int baseDuration = BASE_DURATION_TICKS + (spellLevel - 1) * DURATION_PER_LEVEL;
        return (int)(baseDuration * getEntityPowerMultiplier(caster));
    }

    public static float getAimConeAngle(int spellLevel) {
        return 15.0f + spellLevel * 10.0f;
    }
}