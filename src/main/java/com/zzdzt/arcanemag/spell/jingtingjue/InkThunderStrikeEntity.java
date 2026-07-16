package com.zzdzt.arcanemag.spell.jingtingjue;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.particle.InkZapParticleOption;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;

/**
 * 水墨雷击实体
 */
public class InkThunderStrikeEntity extends Entity {

    // 压缩int布局（32位）：
    // [0-3]   colorTier (0-15)                    4 bits
    // [4-11]  radiusIndex (0-255, 精度0.1)       8 bits
    // [12-19] lengthIndex (0-255, 精度0.2)       8 bits
    // [20-23] strikePhase (0-15)                   4 bits
    // [24-31] reserved                            8 bits
    
    private static final int COLOR_TIER_MASK = 0x0000000F;
    private static final int COLOR_TIER_SHIFT = 0;
    private static final int RADIUS_MASK = 0x00000FF0;
    private static final int RADIUS_SHIFT = 4;
    private static final int LENGTH_MASK = 0x00FFF000;
    private static final int LENGTH_SHIFT = 12;
    private static final int PHASE_MASK = 0x0F000000;
    private static final int PHASE_SHIFT = 24;
    
    private static final float RADIUS_SCALE = 10.0f;
    private static final float LENGTH_SCALE = 5.0f;
    
    private static final EntityDataAccessor<Integer> COMPRESSED_STATE = 
        SynchedEntityData.defineId(InkThunderStrikeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE = 
        SynchedEntityData.defineId(InkThunderStrikeEntity.class, EntityDataSerializers.FLOAT);

    // 客户端缓存
    private float cachedRadius = -1;
    private float cachedLength = -1;
    private int cachedColorTier = -1;
    private int cachedPhase = -1;
    private int lastSyncedState = 0;

    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;

    private static final int STRIKE_TIME = 10;
    private static final int TOTAL_LIFETIME = 25;

    private static final int MIN_STRIKES = 3;
    private static final int MAX_STRIKES = 5;
    private static final float STRIKE_HEIGHT = 25.0f;

    public InkThunderStrikeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public InkThunderStrikeEntity(EntityType<?> type, Level level,
                                   LivingEntity owner, Vec3 targetPos,
                                   int colorTier, float length, float radius,
                                   float damage, String spellId) {
        this(type, level);
        this.ownerUUID = owner.getUUID();
        this.ownerCached = owner;
        this.setPos(targetPos.x, targetPos.y, targetPos.z);
        
        int compressed = packState(colorTier, radius, length, 0);
        this.entityData.set(COMPRESSED_STATE, compressed);
        this.entityData.set(DAMAGE, damage);
        
        if (!level.isClientSide) {
            this.lastSyncedState = compressed;
        }
    }

    // 压缩/解包 方法

    private static int packState(int colorTier, float radius, float length, int phase) {
        int packed = 0;
        packed |= (Math.max(0, Math.min(15, colorTier)) << COLOR_TIER_SHIFT);
        packed |= (Math.max(0, Math.min(255, (int)(radius * RADIUS_SCALE))) << RADIUS_SHIFT);
        packed |= (Math.max(0, Math.min(255, (int)(length * LENGTH_SCALE))) << LENGTH_SHIFT);
        packed |= (Math.max(0, Math.min(15, phase)) << PHASE_SHIFT);
        return packed;
    }

    private void unpackState(int state) {
        this.cachedColorTier = (state & COLOR_TIER_MASK) >> COLOR_TIER_SHIFT;
        this.cachedRadius = ((state & RADIUS_MASK) >> RADIUS_SHIFT) / RADIUS_SCALE;
        this.cachedLength = ((state & LENGTH_MASK) >> LENGTH_SHIFT) / LENGTH_SCALE;
        this.cachedPhase = (state & PHASE_MASK) >> PHASE_SHIFT;
    }

    private void ensureUnpacked() {
        if (cachedColorTier < 0) {
            unpackState(entityData.get(COMPRESSED_STATE));
        }
    }

    // 公共访问器 方法

    public float getLength() {
        ensureUnpacked();
        return cachedLength;
    }

    public int getColorTier() {
        ensureUnpacked();
        return cachedColorTier;
    }

    public float getRadius() {
        ensureUnpacked();
        return cachedRadius;
    }

    public float getDamage() {
        return entityData.get(DAMAGE);
    }

    public int getPhase() {
        ensureUnpacked();
        return cachedPhase;
    }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerCached != null && ownerCached.isAlive()) return ownerCached;
        if (ownerUUID != null && level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(ownerUUID);
            if (entity instanceof LivingEntity living) {
                ownerCached = living;
                return living;
            }
        }
        return null;
    }

    // 数据同步

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COMPRESSED_STATE, 0);
        this.entityData.define(DAMAGE, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("CompressedState")) entityData.set(COMPRESSED_STATE, tag.getInt("CompressedState"));
        if (tag.contains("Damage")) entityData.set(DAMAGE, tag.getFloat("Damage"));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("CompressedState", entityData.get(COMPRESSED_STATE));
        tag.putFloat("Damage", entityData.get(DAMAGE));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512 * 512;  // ← 保持原始值
    }

    // 核心逻辑（保持原始实现）

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int currentState = entityData.get(COMPRESSED_STATE);
            if (currentState != lastSyncedState) {
                unpackState(currentState);
                lastSyncedState = currentState;
            }
            return;
        }

        if (tickCount == STRIKE_TIME) {
            Vec3 pos = this.position();
            ensureUnpacked();
            applyDamage(pos, cachedRadius);
            spawnMultiStrikeEffects(pos, cachedLength, cachedRadius, cachedColorTier);
            
            int newState = packState(cachedColorTier, cachedRadius, cachedLength, 1);
            if (newState != entityData.get(COMPRESSED_STATE)) {
                entityData.set(COMPRESSED_STATE, newState);
            }
        }

        if (tickCount >= TOTAL_LIFETIME) {
            discard();
        }
    }

    private void applyDamage(Vec3 pos, float radius) {
        LivingEntity owner = getOwner();
        if (owner == null) return;

        float damage = entityData.get(DAMAGE);
        if (damage <= 0) return;

        String spellIdStr = "arcane_mag:jing_ting_jue";
        SpellDamageSource damageSource = null;
        
        if (!spellIdStr.isEmpty()) {
            var spell = SpellRegistry.getSpell(ResourceLocation.parse(spellIdStr));
            if (spell != null) {
                damageSource = spell.getDamageSource(this, owner);
            }
        }

        AABB damageBox = new AABB(
            pos.x - radius, pos.y - 0.5, pos.z - radius,
            pos.x + radius, pos.y + 2.5, pos.z + radius
        );

        List<LivingEntity> entities = level().getEntitiesOfClass(LivingEntity.class, damageBox,
            e -> e != owner && 
                 e.isAlive() && 
                 e.isPickable() &&
                 !Utils.shouldHealEntity(owner, e) &&
                 e.distanceToSqr(pos.x, pos.y, pos.z) <= radius * radius
        );

        int hitCount = 0;
        for (LivingEntity victim : entities) {
            if (DamageSources.applyDamage(victim, damage, damageSource)) {
                hitCount++;
                MagicManager.spawnParticles(level(), ParticleTypes.ELECTRIC_SPARK,
                    victim.getX(), victim.getY() + 0.2, victim.getZ(),
                    8, 0.3, 0.3, 0.3, 0.05, true);
            }
        }

        level().playSound(null, pos.x, pos.y, pos.z,
            net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    // 视觉特效

    private void spawnMultiStrikeEffects(Vec3 center, float length, float radius, int colorTier) {
        int strikeCount = Utils.random.nextIntBetweenInclusive(MIN_STRIKES, MAX_STRIKES);
        
        for (int i = 0; i < strikeCount; i++) {
            double r = Math.sqrt(Utils.random.nextDouble()) * radius * 0.85;
            double angle = Utils.random.nextDouble() * Math.PI * 2;
            
            Vec3 strikeBottom = center.add(
                Math.cos(angle) * r,
                0.1,
                Math.sin(angle) * r
            );
            
            Vec3 strikeTop = strikeBottom.add(
                Utils.getRandomScaled(3),
                STRIKE_HEIGHT + Utils.random.nextFloat() * 5,
                Utils.getRandomScaled(3)
            );
            
            spawnSingleLightningStrike(strikeBottom, strikeTop, i == 0);
        }

        MagicManager.spawnParticles(level(), ParticleTypes.FLASH,
            center.x, center.y + 0.5, center.z,
            2, radius * 0.3, 0.5, radius * 0.3, 0, true);

        level().playSound(null, center.x, center.y, center.z,
            net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
            net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.7f + random.nextFloat() * 0.3f);
    }

    /**
     * 生成单道巨型闪电
     */
    private void spawnSingleLightningStrike(Vec3 bottom, Vec3 top, boolean isMainStrike) {
        Vec3 strikeBottom = bottom;
        
        Vec3 middle = strikeBottom.add(
            Utils.getRandomScaled(2),
            (top.y - strikeBottom.y) * (0.3 + Utils.random.nextFloat() * 0.4),
            Utils.getRandomScaled(2)
        );

        // ← 保持原始参数：主闪电1.3f，副闪电1.0f
        float widthScale = isMainStrike ? 1.3f : 1.0f;

        // 第1道：焦墨外层 —— 保持原始宽度和颜色
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(top, 0.25f * widthScale, 0),
            middle.x, middle.y, middle.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(middle, 0.25f * widthScale, 0),
            strikeBottom.x, strikeBottom.y, strikeBottom.z, 1, 0, 0, 0, 0, true);

        // 第2道：苍青中层
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(top, 0.18f * widthScale, 1),
            middle.x, middle.y, middle.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(middle, 0.18f * widthScale, 1),
            strikeBottom.x, strikeBottom.y, strikeBottom.z, 1, 0, 0, 0, 0, true);

        // 第3道：亮白核心 —— 保持原始0.08f
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(top, 0.08f * widthScale, 2),
            middle.x, middle.y, middle.z, 1, 0, 0, 0, 0, true);
        MagicManager.spawnParticles(level(),
            new InkZapParticleOption(middle, 0.08f * widthScale, 2),
            strikeBottom.x, strikeBottom.y, strikeBottom.z, 1, 0, 0, 0, 0, true);

        // 分叉 —— 保持原始逻辑
        if (isMainStrike || Utils.random.nextFloat() < 0.3f) {
            Vec3 split = middle.add(
                Utils.getRandomScaled(3), 
                -Math.abs(Utils.getRandomScaled(2)), 
                Utils.getRandomScaled(3)
            );
            MagicManager.spawnParticles(level(),
                new InkZapParticleOption(middle, 0.12f, 1),
                split.x, split.y, split.z, 1, 0, 0, 0, 0, true);
        }

        // 落点爆发
        MagicManager.spawnParticles(level(), ParticleTypes.ELECTRIC_SPARK,
            strikeBottom.x, strikeBottom.y, strikeBottom.z,
            3, 0.5, 0.3, 0.5, 0.1, true);
    }

    // 客户端处理自定义包（占位）
    public void handleClientStrikeTrigger(byte[] payload) {
        // 当前无客户端额外逻辑，保留用于未来扩展
    }
}