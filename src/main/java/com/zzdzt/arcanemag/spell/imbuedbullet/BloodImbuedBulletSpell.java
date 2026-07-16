package com.zzdzt.arcanemag.spell.imbuedbullet;

import java.util.List;
import java.util.UUID;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 鲜血注魔子弹
 * 机制：
 * 1. 施法时牺牲射击者当前生命值的 40%（至少保留 1 点），记录牺牲值。
 * 2. 注魔持续期间，每发子弹的法术伤害额外 + 牺牲值。
 * 3. 命中时给目标施加 1 秒鲜血标记（DEBUFF）。
 * 4. 目标在标记持续期间死亡，射击者获得 VIGOR 临时最大生命值奖励。
 */
public class BloodImbuedBulletSpell extends ImbuedBulletSpell {

    private static final ResourceLocation SPELL_ID =
            ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "blood_imbued_bullet");

    private static final DefaultConfig DEFAULT_CONFIG = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(SchoolRegistry.BLOOD_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    /** 玩家 NBT 中存储牺牲值的键 */
    private static final String SACRIFICE_KEY = "arcane_mag_blood_sacrifice";

    /** 目标 NBT 中存储鲜血标记的键（public 供外部检测） */
    public static final String MARK_TAG = "arcane_mag_blood_mark";
    public static final String MARK_SHOOTER = "shooter";
    public static final String MARK_EXPIRE = "expire";

    /** VIGOR 叠加上限（amplifier 0~9，对应 +2~20 Max HP） */
    private static final int MAX_VIGOR_AMPLIFIER = 19;

    // --- 数值配置 ---
    private static final float BASE_CONVERSION_RATE = 0.20f;
    private static final float CONVERSION_SPELLPOWER_FACTOR = 0.30f;
    private static final int BASE_DURATION_SECONDS = 15;
    private static final int DURATION_PER_LEVEL_SECONDS = 5;

    public BloodImbuedBulletSpell() {
        baseManaCost = 35;
        manaCostPerLevel = 5;
        baseSpellPower = 10;
        spellPowerPerLevel = 2;
        castTime = 0;
    }

    // 配置覆盖

    @Override
    public ResourceLocation getSpellResource() {
        return SPELL_ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return DEFAULT_CONFIG;
    }

    @Override
    public MobEffect getImbuedEffect() {
        return EffectRegistry.IMBUED_BLOOD.get();
    }

    @Override
    protected ImbuedBulletEffectProcessor getEffectProcessor() {
        return new BloodImbuedBulletProcessor();
    }

    @Override
    protected String getTranslationKey() {
        return "spell.arcane_mag.blood_imbued_bullet";
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

    // 施法时牺牲生命值

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       io.redspace.ironsspellbooks.api.spells.CastSource castSource,
                       io.redspace.ironsspellbooks.api.magic.MagicData playerMagicData) {
        if (!(entity instanceof ServerPlayer player)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (level.isClientSide()) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        // 计算牺牲值：当前生命值的 40%，至少保留 1 点
        float currentHealth = player.getHealth();
        float sacrifice = currentHealth * 0.40f;
        if (sacrifice >= currentHealth - 1.0f) {
            sacrifice = currentHealth - 1.0f;
        }
        if (sacrifice < 0) sacrifice = 0;

        // 扣除生命值
        player.setHealth(currentHealth - sacrifice);

        // 记录牺牲值到 NBT
        player.getPersistentData().putFloat(SACRIFICE_KEY, sacrifice);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    // 额外法术伤害（牺牲值）

    @Override
    protected float getAdditionalSpellDamage(ServerPlayer shooter, int spellLevel) {
        return shooter.getPersistentData().getFloat(SACRIFICE_KEY);
    }

    // 命中后：施加鲜血标记

    @Override
    protected void onPostBulletHit(ServerPlayer shooter, LivingEntity target,
                                     float spellDamage, int spellLevel) {
        if (target.level().isClientSide()) return;

        // 检查目标是否已有鲜血标记，避免冲锋枪等高射速武器频繁重新施加
        MobEffectInstance existingMark = target.getEffect(EffectRegistry.BLOOD_MARKED.get());
        if (existingMark == null) {
            // 首次施加 1 秒鲜血标记效果
            target.addEffect(new MobEffectInstance(
                    EffectRegistry.BLOOD_MARKED.get(),
                    20, // 1 秒 = 20 ticks
                    0, false, false, false
            ));
        }

        // 无论是否已有标记，都更新 NBT 过期时间（覆盖）
        long expireTick = target.level().getGameTime() + 20;
        CompoundTag markTag = new CompoundTag();
        markTag.putUUID(MARK_SHOOTER, shooter.getUUID());
        markTag.putLong(MARK_EXPIRE, expireTick);
        target.getPersistentData().put(MARK_TAG, markTag);
    }

    /**
     * 当目标在枪械命中时已经死亡（死于枪械伤害本身）时调用。
     * 直接触发 VIGOR 奖励，不依赖 NBT 标记（避免与 LivingDeathEvent 重复）。
     */
    @Override
    public void onTargetDeadOnHit(ServerPlayer shooter, LivingEntity target, int spellLevel) {
        if (target.level().isClientSide()) return;

        // 直接触发奖励
        applyVigorReward(shooter, spellLevel);
    }

    // 清理持久化数据

    @Override
    public void clearPersistentData(ServerPlayer player) {
        player.getPersistentData().remove(SACRIFICE_KEY);
    }

    // 目标死亡时触发击杀奖励

    /**
     * 由 ImbuedBulletEventHandler.onLivingDeath 调用。
     * 检查目标是否带有鲜血标记，如果有效则为标记者触发 VIGOR 奖励。
     */
    public static void onTargetMarkedDeath(LivingEntity target) {
        if (target.level().isClientSide()) return;

        // 检查目标 NBT 中的标记
        if (!target.getPersistentData().contains(MARK_TAG, CompoundTag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag markTag = target.getPersistentData().getCompound(MARK_TAG);
        if (!markTag.hasUUID(MARK_SHOOTER)) return;

        UUID shooterId = markTag.getUUID(MARK_SHOOTER);
        long expireTick = markTag.getLong(MARK_EXPIRE);
        long currentTick = target.level().getGameTime();

        // 清理标记（无论是否过期）
        target.getPersistentData().remove(MARK_TAG);

        // 检查是否过期
        if (currentTick > expireTick) {
            return;
        }

        // 查找 shooter 玩家
        MinecraftServer server = target.level().getServer();
        if (server == null) return;
        ServerPlayer shooter = server.getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;

        // 确认 shooter 仍有鲜血注魔效果
        ImbuedBulletSpell activeSpell = ImbuedBulletState.getActiveSpell(shooter);
        if (!(activeSpell instanceof BloodImbuedBulletSpell)) {
            return;
        }

        // 获取 spellLevel
        MobEffectInstance effectInstance = shooter.getEffect(activeSpell.getImbuedEffect());
        if (effectInstance == null) return;
        int spellLevel = effectInstance.getAmplifier() + 1;

        // 触发 VIGOR 奖励
        applyVigorReward(shooter, spellLevel);
    }

    // VIGOR 奖励

    public static void applyVigorReward(ServerPlayer player, int spellLevel) {
        int bonusHp = getBonusHp(spellLevel);
        int durationTicks = getBonusDurationSeconds(spellLevel) * 20;

        // 计算 VIGOR amplifier
        int vigorAmplifier = (bonusHp / 2) - 1;
        if (vigorAmplifier < 0) vigorAmplifier = 0;

        // 检查当前 VIGOR 层数
        MobEffectInstance currentVigor = player.getEffect(MobEffectRegistry.VIGOR.get());
        if (currentVigor != null) {
            if (currentVigor.getAmplifier() < MAX_VIGOR_AMPLIFIER) {
                vigorAmplifier = currentVigor.getAmplifier() + 1;
            } else {
                vigorAmplifier = currentVigor.getAmplifier(); // 已达上限，只刷新时间
            }
        }

        // 记录旧 Max HP（用于同步生命值）
        float oldMaxHp = player.getMaxHealth();
        float oldHealth = player.getHealth();

        // 施加 VIGOR
        player.addEffect(new MobEffectInstance(
                MobEffectRegistry.VIGOR.get(),
                durationTicks,
                vigorAmplifier,
                false, false, true
        ));

        // 生命值同步：如果之前是满血或接近满血，补上增加量
        float newMaxHp = player.getMaxHealth();
        float hpDiff = newMaxHp - oldMaxHp;
        if (hpDiff > 0 && oldHealth >= oldMaxHp - 0.5f) {
            player.setHealth(Math.min(oldHealth + hpDiff, newMaxHp));
        }
    }

    // 数值工具

    private static int getBonusHp(int spellLevel) {
        return 2 + (spellLevel - 1); // 1级=2, 5级=6
    }

    private static int getBonusDurationSeconds(int spellLevel) {
        return 20 + (spellLevel - 1) * 5; // 1级=20s, 5级=40s
    }
}
