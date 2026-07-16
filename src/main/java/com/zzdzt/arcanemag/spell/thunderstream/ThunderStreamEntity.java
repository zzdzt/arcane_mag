package com.zzdzt.arcanemag.spell.thunderstream;

import com.zzdzt.arcanemag.network.EntityStateUpdatePacket;
import com.zzdzt.arcanemag.registry.SpellRegistry;
import com.zzdzt.arcanemag.utils.CombatTools;
import com.zzdzt.arcanemag.utils.RaycastTools;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * 雷霆激流实体
 */
public class ThunderStreamEntity extends Entity implements TraceableEntity {

    // 压缩布局：
    // [0-11]  lengthIndex (0-4095, 精度0.01, 最大40.95)  12 bits
    // [12-19] radiusIndex (0-255, 精度0.01, 最大2.55)    8 bits
    // [20-23] tier (0-15)                               4 bits
    // [24-31] reserved                                  8 bits
    
    private static final EntityDataAccessor<Integer> BEAM_SHAPE = 
        SynchedEntityData.defineId(ThunderStreamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIGHTNING_SEED = 
        SynchedEntityData.defineId(ThunderStreamEntity.class, EntityDataSerializers.INT);

    private Entity owner;
    private float baseDamage;
    private int castTicks = 0;
    private int damageTickCounter = 0;
    private static final int DAMAGE_INTERVAL = 4;
    
    // 客户端缓存
    private float cachedLength = -1;
    private float cachedRadius = -1;
    private int cachedTier = -1;
    private boolean shapeDirty = true;

    // 网络同步节流
    private int lastSentTier = -1;
    private float lastSentLength = -1;
    private static final float LENGTH_SYNC_THRESHOLD = 0.5f;

    public ThunderStreamEntity(EntityType<? extends ThunderStreamEntity> entityType, Level level) {
        super(entityType, level);
    }

    public ThunderStreamEntity(EntityType<? extends ThunderStreamEntity> entityType, Level level, Entity owner) {
        super(entityType, level);
        this.owner = owner;
    }

    @Override
    public Entity getOwner() {
        return owner;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BEAM_SHAPE, 0);
        entityData.define(LIGHTNING_SEED, 0);
    }

    // 压缩访问器（保持原始API）

    private void setBeamShape(float length, float radius, int tier) {
        int lenIdx = Math.min(4095, Math.max(0, (int)(length * 100)));
        int radIdx = Math.min(255, Math.max(0, (int)(radius * 100)));
        int packed = (lenIdx) | (radIdx << 12) | ((tier & 0xF) << 20);
        entityData.set(BEAM_SHAPE, packed);
    }

    private void unpackShape() {
        int packed = entityData.get(BEAM_SHAPE);
        cachedLength = (packed & 0xFFF) / 100.0f;
        cachedRadius = ((packed >> 12) & 0xFF) / 100.0f;
        cachedTier = (packed >> 20) & 0xF;
        shapeDirty = false;
    }

    public float getLength() {
        if (shapeDirty) unpackShape();
        return cachedLength;
    }

    public float getRadius() {
        if (shapeDirty) unpackShape();
        return cachedRadius;
    }

    public int getTier() {
        if (shapeDirty) unpackShape();
        return cachedTier;
    }

    public int getLightningSeed() {
        return entityData.get(LIGHTNING_SEED);
    }

    // 保持原始tick逻辑 

    @Override
    public void tick() {
        var level = level();
        super.tick();

        if (!level.isClientSide && tickCount % 2 == 0) {
            entityData.set(LIGHTNING_SEED, random.nextInt());
        }

        if (level.isClientSide) {
            // 末端电火花（持续效果）— 原始逻辑
            if (tickCount % 4 == 0) {
                if (getTier() >= 1) {
                    var tip = position().add(getLookAngle().normalize().scale(getLength()));
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        tip.x + (random.nextDouble() - 0.5) * 0.3,
                        tip.y + (random.nextDouble() - 0.5) * 0.3,
                        tip.z + (random.nextDouble() - 0.5) * 0.3,
                        0, 0, 0);
                }
            }
        } else {
            if (owner == null || owner.isRemoved()) {
                discard();
                return;
            }
            damageTickCounter++;
            if (damageTickCounter >= DAMAGE_INTERVAL) {
                damageTickCounter = 0;
                damageHitTarget(level);
            }

            // 新增：智能同步（不改变功能，只优化网络）
            int currentTier = ThunderStream.getCurrentTier(castTicks);
            if (currentTier != lastSentTier) {
                lastSentTier = currentTier;
                EntityStateUpdatePacket.sendThunderBeamUpdate(this, currentTier, getLength());
            }
            castTicks++;
        }
    }

    //保持原始伤害逻辑 

    private void damageHitTarget(Level level) {
        var beamStart = position();
        var beamEnd = position().add(getLookAngle().normalize().scale(getLength()));

        var entities = RaycastTools.sampleBeamHits(
            level,
            beamStart,
            beamEnd,
            getRadius(),
            0.25,
            e -> e != owner && e.isAlive() && CombatTools.isValidCombatTarget(e, owner)
        );

        float multiplier = ThunderStream.getCurrentDamageMultiplier(castTicks);
        float currentDamage = baseDamage * multiplier;

        var damageTypeKey = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning_lance")
        );
        var source = CombatTools.getDamageSource(level, this, owner, damageTypeKey);

        for (var entity : entities) {
            // 视线检测：确保光束到目标之间没有方块阻挡
            if (!io.redspace.ironsspellbooks.api.util.Utils.hasLineOfSight(
                    level, beamStart, entity.getBoundingBox().getCenter(), true)) continue;

            boolean hit = CombatTools.applyDamage(entity, currentDamage, source,
                SpellRegistry.THUNDER_STREAM.get().getSchoolType(),
                CombatTools.KnockbackTypes.DEFAULT);

            if (hit && level instanceof ServerLevel serverLevel) {
                spawnImpactParticles(serverLevel, entity);
            }
        }
    }

    /**
     * 在命中实体位置生成电火花爆开粒子
     */
    private void spawnImpactParticles(ServerLevel level, Entity target) {
        var pos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        MagicManager.spawnParticles(level, ParticleHelper.ELECTRICITY,
            pos.x, pos.y, pos.z, 3, 0.04, 0.04, 0.04, 0.05, false);

        MagicManager.spawnParticles(level, ParticleTypes.ELECTRIC_SPARK,
            pos.x, pos.y, pos.z, 3, 0.2, 0.2, 0.2, 0.8, false);
    }

    public void setBaseDamage(float damage) {
        this.baseDamage = damage;
    }

    public void setCastTicks(int ticks) {
        this.castTicks = ticks;
    }

    // 客户端处理自定义包
    public void handleClientBeamUpdate(byte[] payload) {
        if (payload.length < 5) return;
        int tier = payload[0] & 0xFF;
        float length = EntityStateUpdatePacket.readFloat(payload, 1);
        setBeamShape(length, 0.25f, tier);
        shapeDirty = true;
    }

    public void setup(int outerArgb, int innerArgb, float length, float radius) {
        setBeamShape(length, radius, 0);
    }

    public void updateLength(float maxLength, Level level) {
        var currentLength = getLength();
        var blockHit = level.clip(new ClipContext(
            position(),
            position().add(getLookAngle().scale(maxLength)),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            this
        ));
        var hitLength = (float) blockHit.getLocation().distanceTo(position());
        if (Math.abs(currentLength - hitLength) > 0.001) {
            setBeamShape(hitLength, getRadius(), getTier());
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag pCompound) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag pCompound) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 64 * 64;  // ← 补回原始值
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        var length = getLength();
        var radius = getRadius();
        var dir = Vec3.directionFromRotation(getXRot(), getYRot()).normalize();
        var start = position();
        var end = start.add(dir.scale(length));
        var inflate = Math.max(0.5, radius * 2.0);
        return new AABB(start, end).inflate(inflate);
    }
}