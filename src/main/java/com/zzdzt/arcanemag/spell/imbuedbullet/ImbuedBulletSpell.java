package com.zzdzt.arcanemag.spell.imbuedbullet;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.zzdzt.arcanemag.ArcaneMag;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 注魔子弹法术抽象基类
 * 
 * 机制：Instant施法，为玩家施加学派特定的MobEffect标记。
 * 当玩家使用TACZ枪械射击命中时，通过 EntityHurtByGunEvent.Post 检测，
 * 额外造成 (枪械伤害 × 转化率 + 子类额外伤害) 的对应学派法术伤害，并触发学派特效。
 * 
 *   兼容TACZ护甲穿透：
 * - 在onBulletHit中重置目标无敌帧，避免被TACZ多次hurt()阻止
 * - 使用反射访问 LivingEntity.lastHurt（private字段）
 * - 反射方法为 static，供所有子类的 static 内部类使用
 */
public abstract class ImbuedBulletSpell extends AbstractSpell {

    /**
     * 反射缓存（static，供所有子类使用）
     * LivingEntity.lastHurt 是 private 字段，需要通过反射访问
     * 用于在重置无敌帧时同步重置 lastHurt
     */
    private static Field LAST_HURT_FIELD = null;
    private static boolean lastHurtReflectionAttempted = false;

    /**
     * 获取 LivingEntity.lastHurt 字段（通过反射）
     * 供所有子类的 static 内部类调用
     */
    private static Field getLastHurtField() {
        if (!lastHurtReflectionAttempted) {
            lastHurtReflectionAttempted = true;
            try {
                LAST_HURT_FIELD = LivingEntity.class.getDeclaredField("lastHurt");
                LAST_HURT_FIELD.setAccessible(true);
            } catch (NoSuchFieldException e) {
                ArcaneMag.LOGGER.error("Failed to find LivingEntity.lastHurt field", e);
            }
        }
        return LAST_HURT_FIELD;
    }

    /**
     * 获取目标的 lastHurt 值
     * 供所有子类的 static 内部类调用
     */
    protected static float getLastHurt(LivingEntity entity) {
        Field field = getLastHurtField();
        if (field == null) return 0;
        try {
            return field.getFloat(entity);
        } catch (IllegalAccessException e) {
            ArcaneMag.LOGGER.error("Failed to get lastHurt", e);
            return 0;
        }
    }

    /**
     * 设置目标的 lastHurt 值
     * 供所有子类的 static 内部类调用
     */
    protected static void setLastHurt(LivingEntity entity, float value) {
        Field field = getLastHurtField();
        if (field == null) return;
        try {
            field.setFloat(entity, value);
        } catch (IllegalAccessException e) {
            ArcaneMag.LOGGER.error("Failed to set lastHurt", e);
        }
    }

    // 子类配置接口 

    @Override
    public abstract ResourceLocation getSpellResource();

    @Override
    public abstract DefaultConfig getDefaultConfig();

    // 获取对应的MobEffect标记 
    public abstract MobEffect getImbuedEffect();

    // 获取学派特效处理器 
    protected abstract ImbuedBulletEffectProcessor getEffectProcessor();

    // 法术名称翻译键 
    protected abstract String getTranslationKey();

    // 子类可覆盖的数值配置 

    /** 伤害转化率：枪械伤害的百分比转为法术伤害 */
    protected float getDamageConversionRate(int spellLevel) {
        return 0.30f + (spellLevel - 1) * 0.08f; // 30%-62%
    }

    protected float getDamageConversionRate(int spellLevel, LivingEntity caster) {
        return getDamageConversionRate(spellLevel); // 默认回退
    }

    /** 基础持续时间（秒） */
    protected int getBaseDurationSeconds() {
        return 15;
    }

    /** 每级增加的持续时间（秒） */
    protected int getDurationPerLevelSeconds() {
        return 5;
    }

    /**
     * 获取额外的法术伤害（由子类覆盖，如鲜血注魔的牺牲值）。
     * 在 onBulletHit 中，此值会被追加到基础转化伤害之后。
     */
    protected float getAdditionalSpellDamage(ServerPlayer shooter, int spellLevel) {
        return 0f;
    }

    /** 清理子类可能存储的持久化数据（如NBT）。在注魔效果被清除时调用。 */
    public void clearPersistentData(ServerPlayer player) {}

    /** 当目标在枪械命中时已经死亡（死于枪械伤害本身）时调用。默认空实现，子类覆盖。 */
    public void onTargetDeadOnHit(ServerPlayer shooter, LivingEntity target, int spellLevel) {}

    // 通用常量 

    public ImbuedBulletSpell() {
        baseManaCost = 30;
        manaCostPerLevel = 5;
        baseSpellPower = 10;
        spellPowerPerLevel = 2;
        castTime = 0; // Instant
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
        return Optional.of(net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float rate = getDamageConversionRate(spellLevel, caster) * 100;
        int duration = getDurationTicks(spellLevel, caster) / 20;
        return List.of(
            Component.translatable("spell.arcane_mag.imbued_bullet.info.conversion", 
                Utils.stringTruncation(rate, 1)),
            Component.translatable("spell.arcane_mag.info.duration", duration)
        );
    }

    /** 用于在 onBulletHit 中临时缓存原始枪械伤害，供子类在 onHit / onPostBulletHit 中读取 */
    protected static final String GUN_DAMAGE_CACHE = "arcane_mag:gun_damage_cache";

    /**
     * 获取当前 tick 中缓存的原始枪械伤害。
     * 由基类 onBulletHit 自动设置，在 onBulletHit 结束后自动清除。
     * 子类在 onHit / onPostBulletHit 中调用此方法获取原始枪械伤害。
     */
    protected float getCachedGunDamage(ServerPlayer shooter) {
        return shooter.getPersistentData().getFloat(GUN_DAMAGE_CACHE);
    }

    // 核心施法逻辑 

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!(entity instanceof ServerPlayer player)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        // 只处理服务端
        if (level.isClientSide()) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        int duration = getDurationTicks(spellLevel, entity);
        int amplifier = spellLevel - 1; // 0-4 对应等级 1-5

        // 先清除其他学派的注魔效果（互斥）
        clearOtherImbuedEffects(player);

        // 施加新的注魔效果
        MobEffect effect = getImbuedEffect();
        player.removeEffect(effect); // 刷新
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));

        // 触发特效处理器的施法时效果
        getEffectProcessor().onCast(player, spellLevel);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    public void onBulletHit(ServerPlayer shooter, LivingEntity target, 
                            float gunDamage, int spellLevel) {

        // 再次确认效果仍在（防止刚好过期）
        MobEffectInstance effectInstance = shooter.getEffect(getImbuedEffect());
        if (effectInstance == null) {
            return;
        }

        // 缓存原始枪械伤害，供子类在 onHit / onPostBulletHit 中读取
        shooter.getPersistentData().putFloat(GUN_DAMAGE_CACHE, gunDamage);
        try {
            // 计算转化伤害 + 子类额外伤害
            float conversionRate = getDamageConversionRate(spellLevel, shooter);
            float additionalDamage = getAdditionalSpellDamage(shooter, spellLevel);
            float spellDamage = gunDamage * conversionRate + additionalDamage;

            // 确保伤害至少为1.0
            spellDamage = Math.max(spellDamage, 1.0f);

            // 创建学派法术伤害源
            SpellDamageSource damageSource = getDamageSource(shooter, shooter);

            // 临时重置无敌帧
            int oldInvulnerableTime = target.invulnerableTime;
            float oldLastHurt = getLastHurt(target);

            boolean hit = false;
            try {
                // 重置无敌帧，让法术伤害能够正常生效
                target.invulnerableTime = 0;
                setLastHurt(target, 0);

                hit = target.hurt(damageSource, spellDamage);
            } finally {
                // 如果hurt()失败，恢复原来的无敌帧状态
                if (!hit) {
                    target.invulnerableTime = oldInvulnerableTime;
                    setLastHurt(target, oldLastHurt);
                }
            }

            if (hit) {
                // 触发学派特效
                getEffectProcessor().onHit(shooter, target, spellDamage, spellLevel);

                // 触发子类自定义命中后逻辑（如鲜血标记）
                onPostBulletHit(shooter, target, spellDamage, spellLevel);
            }
        } finally {
            // 【清除】避免残留
            shooter.getPersistentData().remove(GUN_DAMAGE_CACHE);
        }
    }

    /**
     * 子类可覆盖的命中后逻辑。
     * 在法术伤害成功造成后调用，用于追加特殊效果（如鲜血标记）。
     */
    protected void onPostBulletHit(ServerPlayer shooter, LivingEntity target, 
                                     float spellDamage, int spellLevel) {}

    // 工具方法 

    private void clearOtherImbuedEffects(ServerPlayer player) {
        for (var entry : com.zzdzt.arcanemag.registry.SpellRegistry.SPELLS.getEntries()) {
            if (entry.get() instanceof ImbuedBulletSpell otherSpell) {
                if (otherSpell != this) {
                    player.removeEffect(otherSpell.getImbuedEffect());
                    otherSpell.clearPersistentData(player);
                }
            }
        }
    }

    protected int getDurationTicks(int spellLevel, LivingEntity caster) {
        int base = getBaseDurationSeconds() * 20;
        int perLevel = getDurationPerLevelSeconds() * 20;
        return base + (spellLevel - 1) * perLevel;
    }
}
