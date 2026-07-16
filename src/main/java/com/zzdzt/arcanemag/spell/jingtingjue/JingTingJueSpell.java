package com.zzdzt.arcanemag.spell.jingtingjue;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.JingTingChargeEffect;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import com.zzdzt.arcanemag.registry.EntityRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 惊霆诀 —— 锁定目标，造成范围雷击，获得充能叠加
 */
public class JingTingJueSpell extends AbstractSpell {

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "jing_ting_jue"
    );

    // 配置常量 方法
    private static final float BASE_RADIUS = 2.0f;
    private static final float RADIUS_PER_LEVEL = 1.0f;
    private static final int CHARGE_DURATION_TICKS = 20 * 10; // 10秒
    private static final float BASE_DAMAGE = 12.0f;
    private static final float DAMAGE_PER_LEVEL = 5.0f;
    private static final int TARGET_RANGE = 32;        // 目标锁定最大距离（int）
    private static final float TARGET_ANGLE = 0.35f;   // 目标锁定角度容差
    private static final float INK_STRIKE_LENGTH = 30.0f; // 闪电从落点到天空的长度

    private final DefaultConfig config = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
        .setMaxLevel(3)
        .setCooldownSeconds(9)
        .build();

    public JingTingJueSpell() {
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 90;
        this.manaCostPerLevel = 0;
        this.castTime = 15;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float damage = getSpellPower(spellLevel, caster);
        float radius = getRadius(spellLevel, caster);
        return List.of(
            Component.translatable("ui.irons_spellbooks.aoe_damage", Utils.stringTruncation(damage, 1)),
            Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation(radius, 1))
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
        return CastType.LONG;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    // 目标锁定 方法

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return Utils.preCastTargetHelper(level, entity, playerMagicData, this, TARGET_RANGE, TARGET_ANGLE);
    }

    // 核心施法逻辑 方法

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, 
                       CastSource castSource, MagicData playerMagicData) {

        // 获取锁定目标
        LivingEntity target = getTargetFromCastData(playerMagicData, level);
        if (target == null || !target.isAlive()) {
            return;
        }

        // 计算当前充能层数和伤害倍率
        int currentStacks = getCurrentChargeStacks(entity);
        float chargeMultiplier = JingTingChargeEffect.getDamageMultiplier(currentStacks - 1);

        float baseDamage = getSpellPower(spellLevel, entity);
        float finalDamage = baseDamage * chargeMultiplier;

        performInkThunderStrike(level, entity, target, spellLevel, finalDamage, currentStacks);

        // 添加/刷新充能效果
        applyChargeStack(entity, currentStacks);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    // 从施法数据中获取锁定的目标实体 方法
    @Nullable
    private LivingEntity getTargetFromCastData(MagicData magicData, Level level) {
        var castData = magicData.getAdditionalCastData();
        if (!(castData instanceof TargetEntityCastData targetData)) {
            return null;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            ArcaneMag.LOGGER.warn("JingTingJue: Cannot resolve target - level is not ServerLevel (isClientSide={})", 
                level == null ? "null" : level.isClientSide());
            return null;
        }

        Entity target = targetData.getTarget(serverLevel);
        if (target instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
            return livingTarget;
        }

        return null;
    }

    /**执行范围雷击
     * 伤害由 InkThunderStrikeEntity 在闪电落下时触发，与视觉效果同步
     */
    private void performInkThunderStrike(Level level, LivingEntity caster, LivingEntity target,
                                          int spellLevel, float damage, int currentStacks) {
        Vec3 strikePos = target.position().add(0, 0.1, 0);
        float radius = getRadius(spellLevel, caster);

        if (!level.isClientSide) {
            int colorTier = Math.min(4, currentStacks / 2);

            InkThunderStrikeEntity inkStrike = new InkThunderStrikeEntity(
                EntityRegistry.INK_THUNDER_STRIKE.get(),
                level,
                caster,
                strikePos,
                colorTier,
                INK_STRIKE_LENGTH,
                radius,
                damage,
                "arcane_mag:jing_ting_jue"
            );
            level.addFreshEntity(inkStrike);
        }
    }

    // 充能系统

    private int getCurrentChargeStacks(LivingEntity entity) {
        var effect = entity.getEffect(EffectRegistry.JING_TING_CHARGE.get());
        if (effect == null) return 0;
        return JingTingChargeEffect.getStackCount(effect.getAmplifier());
    }

    // 添加/刷新充能效果
    private void applyChargeStack(LivingEntity entity, int currentStacks) {
        int newStacks = Math.min(currentStacks + 1, JingTingChargeEffect.MAX_STACKS);
        int newAmplifier = newStacks - 1;

        entity.addEffect(new MobEffectInstance(
            EffectRegistry.JING_TING_CHARGE.get(),
            CHARGE_DURATION_TICKS,
            newAmplifier,
            false, false, true
        ));
    }

    // 数值计算 方法

    public static float getRadius(int spellLevel, LivingEntity caster) {
        return BASE_RADIUS + (spellLevel - 1) * RADIUS_PER_LEVEL;
    }

    /**
     * 保留备用：无 spell_power 加成的固定基础伤害
     */
    public static float getBaseDamage(int spellLevel, LivingEntity caster) {
        return BASE_DAMAGE + (spellLevel - 1) * DAMAGE_PER_LEVEL;
    }
}