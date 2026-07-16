package com.zzdzt.arcanemag.spell.lizhiyan;

import com.zzdzt.arcanemag.event.GunHitTargetTracker;
import com.zzdzt.arcanemag.registry.EntityRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class LizhiYanEntity extends Entity implements IMagicSummon, AntiMagicSusceptible {

    //同步数据 
    private static final EntityDataAccessor<Integer> ATTACK_PHASE =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CHARGE_SCALE =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_THRUSTING =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AIM_YAW =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH =
        SynchedEntityData.defineId(LizhiYanEntity.class, EntityDataSerializers.FLOAT);

    enum Phase { IDLE, CHARGE, STAB, BLAST, BLAST_DELAY, SPIN, COOLDOWN }

    //排列配置 
    private static final Vec3[] FORMATION_OFFSETS = {
        new Vec3(-2.0, 1.2, 0.0),
        new Vec3(2.0, 1.2, 0.0),
        new Vec3(0.0, 1.8, -1.5)
    };

    private static final Vec3[] DEFAULT_AIM = {
        new Vec3(-0.5, 0, 0.8).normalize(),
        new Vec3(0.5, 0, 0.8).normalize(),
        new Vec3(0.0, 0.1, 1.0).normalize()
    };

    private static final float[][] BLAST_TINTS = {
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f},
        {0.22f, 0.71f, 0.97f}
    };

    //性能优化常量 
    private static final float FORMATION_LERP = 0.12f;
    private static final float IDLE_SWAY_AMP = 0.03f;
    private static final float IDLE_SWAY_FREQ = 0.02f;
    // 视线检测降频：每 2 tick 一次
    private static final int LOS_CHECK_INTERVAL = 2;
    // 丢失视线超时：13 * 2tick = 26tick ≈ 1.3秒
    private static final int LOST_SIGHT_TIMEOUT = 13;
    // 目标扫描间隔：无目标时更频繁
    private static final int SCAN_INTERVAL_NO_TARGET = 10;
    private static final int SCAN_INTERVAL_HAS_TARGET = 20;
    // 方向变化阈值：小于此值时跳过三角函数
    private static final double AIM_DIR_THRESHOLD_SQ = 0.001;
    // 位置更新降频：每 2 tick 计算一次
    private static final int POSITION_UPDATE_INTERVAL = 2;

    //攻击时序 
    private static final int CHARGE_TICKS_STAB = 10;
    private static final int STAB_TICKS = 15;
    private static final int COOLDOWN_TICKS_STAB = 10;
    private static final int COOLDOWN_TICKS_BLAST = 40;
    private static final float[] STAB_DAMAGE_MULTIPLIERS = {1.0f, 1.3f};
    private static final float BLAST_DAMAGE_MULTIPLIER = 3.0f;
    private static final float STAB_OVERSHOOT = 2.5f;
    private static final int RETURN_TICKS = 10;

    //旋转动作时序
    private static final int DEFAULT_SPIN_TICKS = 50;
    private static final float SPIN_RADIUS = 1.5f;
    private static final float SPIN_HEIGHT = 1.2f;
    private static final float SPIN_CENTER_DISTANCE = 2.0f;
    private static final int BLAST_TO_SPIN_DELAY = 4;
    private int spinTicks = DEFAULT_SPIN_TICKS;

    //运行时状态 
    @Nullable private UUID ownerUUID;
    @Nullable private LivingEntity ownerCached;
    private int slotIndex;
    private float baseDamage;
    private float range = 20.0f;

    // 目标追踪
    @Nullable private Entity currentTarget;
    @Nullable private Entity pendingTarget;
    private int lostSightTick;

    private Phase phase = Phase.IDLE;
    private int phaseTick = 0;
    private int attackIndex = 0;

    // 位置缓存
    private Vec3 formationPos = Vec3.ZERO;
    private Vec3 lastComputedFormationPos = Vec3.ZERO;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private float lastOwnerYaw = 0f;
    private double lastCosYaw = 1.0, lastSinYaw = 0.0;
    private int lastFormationComputeTick = -1;

    private Vec3 thrustTarget = Vec3.ZERO;
    private int thrustTick = 0;
    @Nullable private Vec3 thrustDirection;
    private Vec3 thrustStartPos = Vec3.ZERO;
    private Vec3 thrustEndPos = Vec3.ZERO;
    private boolean hasDealtDamage = false;

    private Vec3 returnStartPos = Vec3.ZERO;
    private int returnTick = 0;

    @Nullable private Vec3 blastLockedDirection;

    // 方向缓存
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private Vec3 lastAimDir = Vec3.ZERO;

    public LizhiYanEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public LizhiYanEntity(EntityType<?> entityType, Level level,
                          LivingEntity owner, int slot, int maxSlot) {
        this(entityType, level);
        this.ownerUUID = owner.getUUID();
        this.ownerCached = owner;
        this.slotIndex = slot;
        this.baseDamage = 6.0f;

        Vec3 aim = DEFAULT_AIM[slot];
        this.currentYaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        this.currentPitch = (float) -Math.toDegrees(
            Math.atan2(aim.y, Math.sqrt(aim.x*aim.x + aim.z*aim.z)));

        setInitialPosition(owner);
    }

    @Override public void onUnSummon() {
        if (!level().isClientSide) {
            SummonManager.removeSummon(this);
            SummonManager.stopTrackingExpiration(this);
        }
        discard();
    }
    @Override public LivingEntity getSummoner() { return getOwner(); }
    @Override public void onAntiMagic(MagicData magicData) { onUnSummon(); }
    @Override public void onRemovedHelper(Entity entity) {}
    @Override public boolean shouldIgnoreDamage(DamageSource source) { return true; }

    public void setBaseDamage(float damage) { this.baseDamage = damage; }
    public void setRange(float range) { this.range = range; }

    @Nullable
    public LivingEntity getOwner() {
        if (ownerCached != null && ownerCached.isAlive()) return ownerCached;
        if (ownerUUID != null && level() instanceof ServerLevel sl) {
            var e = sl.getEntity(ownerUUID);
            if (e instanceof LivingEntity living) {
                ownerCached = living;
                return living;
            }
        }
        return null;
    }

    //数据同步
    @Override protected void defineSynchedData() {
        entityData.define(ATTACK_PHASE, 0);
        entityData.define(CHARGE_SCALE, 1.0f);
        entityData.define(IS_THRUSTING, false);
        entityData.define(AIM_YAW, 0.0f);
        entityData.define(AIM_PITCH, 0.0f);
    }
    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUUID = tag.getUUID("Owner");
        if (tag.contains("Slot")) slotIndex = tag.getInt("Slot");
        if (tag.contains("BaseDamage")) baseDamage = tag.getFloat("BaseDamage");
        if (tag.contains("Range")) range = tag.getFloat("Range");
        if (tag.contains("AttackIndex")) attackIndex = tag.getInt("AttackIndex");
    }
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Slot", slotIndex);
        tag.putFloat("BaseDamage", baseDamage);
        tag.putFloat("Range", range);
        tag.putInt("AttackIndex", attackIndex);
    }

    //公共 Getter 
    public int getAttackPhase() { return entityData.get(ATTACK_PHASE); }
    public float getChargeScale() { return entityData.get(CHARGE_SCALE); }
    public boolean isThrusting() { return entityData.get(IS_THRUSTING); }
    public float getAimYaw() { return entityData.get(AIM_YAW); }
    public float getAimPitch() { return entityData.get(AIM_PITCH); }
    public int getSlotIndex() { return slotIndex; }
    public void setSpinTicks(int ticks) { this.spinTicks = ticks; }

    //主循环 
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) tickClient();
        else tickServer();
    }

    private void tickClient() {
        if (firstTick) spawnSummonParticles();

        Phase currentPhase = Phase.values()[entityData.get(ATTACK_PHASE)];
        float chargeScale = entityData.get(CHARGE_SCALE);
        boolean isThrusting = entityData.get(IS_THRUSTING);

        // 蓄力粒子环
        if (currentPhase == Phase.CHARGE && tickCount % 5 == 0) {
            float angle = tickCount * 0.3f + slotIndex * 2.1f;
            for (int i = 0; i < 2; i++) {
                float a = angle + (float) (Math.PI * i);
                double px = getX() + Math.cos(a) * chargeScale * 0.4;
                double pz = getZ() + Math.sin(a) * chargeScale * 0.4;
                level().addParticle(ParticleTypes.ENCHANT, px, getY(), pz, 0, 0.03, 0);
            }
        }

        // 旋转粒子
        if (currentPhase == Phase.SPIN && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.ENCHANT,
                getX() + (random.nextDouble()-0.5)*0.3,
                getY() + (random.nextDouble()-0.5)*0.3,
                getZ() + (random.nextDouble()-0.5)*0.3,
                (random.nextDouble()-0.5)*0.1,
                (random.nextDouble()-0.5)*0.1,
                (random.nextDouble()-0.5)*0.1);
        }

        // 冲刺拖尾
        if (isThrusting && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.CRIT,
                getX() + (random.nextDouble()-0.5)*0.2,
                getY() + (random.nextDouble()-0.5)*0.2,
                getZ() + (random.nextDouble()-0.5)*0.2,
                0, 0, 0);
        }

        // 常驻粒子
        if (tickCount % 10 == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY(), getZ(), 0, 0.03, 0);
        }
    }

    private void tickServer() {
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { onUnSummon(); return; }

        if (returnTick > 0) { updateReturnToFormation(owner); syncStateToClient(); return; }
        if (thrustTick > 0) { updateThrust(); }
        else if (phase == Phase.SPIN) { updateSpin(owner); }
        else { updateFormationPosition(owner); }

        updateTarget(owner);
        updateAttackState(owner);
        syncStateToClient();
    }

    // 位置
    private void updateFormationPosition(LivingEntity owner) {
        // 缓存 owner 的 yaw 三角函数，避免每 tick 重复计算
        float ownerYaw = owner.getYRot();
        if (ownerYaw != lastOwnerYaw || lastOwnerPos.distanceToSqr(owner.position()) > 0.01) {
            lastOwnerYaw = ownerYaw;
            lastOwnerPos = owner.position();
            double yawRad = ownerYaw * 0.0174533f;
            lastCosYaw = Math.cos(yawRad);
            lastSinYaw = Math.sin(yawRad);
        }

        // 每 2 tick 计算一次目标位置，中间用 lerp 插值
        Vec3 targetPos;
        if (tickCount % POSITION_UPDATE_INTERVAL == 0 || lastFormationComputeTick < 0) {
            // 完整计算
            Vec3 offset = FORMATION_OFFSETS[slotIndex];
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            targetPos = owner.position().add(rx, offset.y, rz);

            double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
            targetPos = targetPos.add(0, swayY, 0);

            lastComputedFormationPos = targetPos;
            lastFormationComputeTick = tickCount;
        } else {
            // 使用上次计算的位置
            Vec3 offset = FORMATION_OFFSETS[slotIndex];
            double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
            double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
            targetPos = owner.position().add(rx, offset.y, rz);
            double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
            targetPos = targetPos.add(0, swayY, 0);
        }

        Vec3 newPos = position().lerp(targetPos, FORMATION_LERP);
        setPos(newPos.x, newPos.y, newPos.z);
        hasImpulse = true;
        formationPos = newPos;
        updateAimDirection(owner);
    }

    //瞄准方向 
    private void updateAimDirection(LivingEntity owner) {
        Entity targetToAim = currentTarget != null ? currentTarget : pendingTarget;

        Vec3 aimDir;
        if (targetToAim != null && targetToAim.isAlive()) {
            Vec3 tc = targetToAim.position().add(0, targetToAim.getBbHeight()/2, 0);
            aimDir = tc.subtract(position()).normalize();
        } else {
            Vec3 def = DEFAULT_AIM[slotIndex];
            double rx = def.x * lastCosYaw - def.z * lastSinYaw;
            double rz = def.x * lastSinYaw + def.z * lastCosYaw;
            aimDir = new Vec3(rx, def.y, rz).normalize();
        }

        // 方向变化很小时跳过三角函数计算
        if (aimDir.distanceToSqr(lastAimDir) < AIM_DIR_THRESHOLD_SQ) {
            // 使用缓存的 yaw/pitch，只更新 entity 的 rotation
            setYRot(currentYaw);
            setXRot(currentPitch);
            yRotO = currentYaw;
            xRotO = currentPitch;
            return;
        }
        lastAimDir = aimDir;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-aimDir.x, aimDir.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(aimDir.y, Math.sqrt(aimDir.x*aimDir.x + aimDir.z*aimDir.z)));
        currentYaw = lerpAngle(currentYaw, targetYaw, 0.15f);
        currentPitch = lerpAngle(currentPitch, targetPitch, 0.15f);
        setYRot(currentYaw);
        setXRot(currentPitch);
        yRotO = currentYaw;
        xRotO = currentPitch;
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return from + diff * t;
    }

    // 目标追踪
    private void updateTarget(LivingEntity owner) {
        // 1. 获取玩家当前攻击目标
        Entity newPlayerTarget = getPlayerCurrentTarget(owner);

        // 2. 如果玩家有目标，优先使用
        if (newPlayerTarget != null && newPlayerTarget.isAlive()) {
            UUID currentUUID = currentTarget != null ? currentTarget.getUUID() : null;
            UUID bestUUID = newPlayerTarget.getUUID();

            if (!bestUUID.equals(currentUUID)) {
                boolean inRange = distanceTo(newPlayerTarget) <= range;
                boolean hasLos = hasLineOfSight(newPlayerTarget);

                if (inRange && hasLos) {
                    pendingTarget = newPlayerTarget;
                }
            }
        }

        // 3. 检查当前目标是否有效
        if (pendingTarget == null && currentTarget != null) {
            if (!currentTarget.isAlive() || currentTarget.isRemoved()) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (distanceTo(currentTarget) > range * 1.5) {
                currentTarget = null;
                lostSightTick = 0;
            } else if (tickCount % LOS_CHECK_INTERVAL == 0 && !hasLineOfSight(currentTarget)) {
                lostSightTick++;
                if (lostSightTick >= LOST_SIGHT_TIMEOUT) {
                    currentTarget = null;
                    lostSightTick = 0;
                }
            } else if (tickCount % LOS_CHECK_INTERVAL == 0) {
                // 有视线时重置计时
                lostSightTick = 0;
            }
        }

        // 4. 自动扫描
        if (currentTarget == null && pendingTarget == null) {
            int scanInterval = (currentTarget == null && pendingTarget == null)
                ? SCAN_INTERVAL_NO_TARGET
                : SCAN_INTERVAL_HAS_TARGET;
            if (tickCount % scanInterval == 0) {
                currentTarget = findNearestEnemy(owner);
            }
        }
    }

    /**
     * 获取玩家当前攻击目标
     */
    @Nullable
    private Entity getPlayerCurrentTarget(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer serverPlayer)) return null;

        // 优先级1: GunHitTargetTracker（最近5秒内）
        LivingEntity hitTarget = GunHitTargetTracker.peekLastHitTarget(serverPlayer);
        if (hitTarget != null && hitTarget.isAlive()) {
            return hitTarget;
        }

        // 优先级2: 原版 lastHurtMob（100tick内）
        LivingEntity lastHurt = serverPlayer.getLastHurtMob();
        if (lastHurt != null && lastHurt.isAlive()) {
            int lastHurtTimestamp = serverPlayer.getLastHurtMobTimestamp();
            if (serverPlayer.tickCount - lastHurtTimestamp < 100) {
                return lastHurt;
            }
        }

        return null;
    }

    @Nullable
    private Entity findNearestEnemy(LivingEntity owner) {
        AABB aabb = new AABB(getX()-range, getY()-range, getZ()-range,
                             getX()+range, getY()+range, getZ()+range);
        var candidates = level().getEntities(owner, aabb, e -> {
            if (e == this || e == owner) return false;
            if (!(e instanceof LivingEntity living)) return false;
            if (e instanceof net.minecraft.world.entity.player.Player) return false;
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        return candidates.stream()
            .filter(e -> hasLineOfSight(e))
            .min((a, b) -> Double.compare(distanceTo(a), distanceTo(b)))
            .orElse(null);
    }

    private boolean hasLineOfSight(Entity target) {
        Vec3 start = position().add(0, 0.3, 0);
        Vec3 end = target.position().add(0, target.getBbHeight()/2, 0);
        return level().clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // 攻击状态机
    private void updateAttackState(LivingEntity owner) {
        switch (phase) {
            case IDLE:
                if (pendingTarget != null && pendingTarget.isAlive()) {
                    currentTarget = pendingTarget;
                    pendingTarget = null;
                    attackIndex = 0;
                    lostSightTick = 0;
                }

                if (currentTarget != null && currentTarget.isAlive()) {
                    phase = Phase.CHARGE;
                    phaseTick = 0;
                    blastLockedDirection = null;
                    hasDealtDamage = false;
                }
                break;

            case CHARGE:
                phaseTick++;
                int chargeThreshold = CHARGE_TICKS_STAB;

                if (currentTarget != null) updateAimDirection(owner);

                if (attackIndex >= 2 && currentTarget != null) {
                    Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
                    blastLockedDirection = tc.subtract(position()).normalize();
                }

                float chargeProgress = phaseTick / (float) chargeThreshold;
                float pulse = 1.0f + (float) Math.sin(chargeProgress * Math.PI) * 0.25f;
                entityData.set(CHARGE_SCALE, pulse);

                if (phaseTick >= chargeThreshold) {
                    entityData.set(CHARGE_SCALE, 1.0f);
                    if (attackIndex < 2) startStab();
                    else startBlast(owner);
                }
                break;

            case STAB:
                phaseTick++;
                if (phaseTick >= STAB_TICKS) {
                    endStab();
                    attackIndex++;
                    phase = Phase.COOLDOWN;
                    phaseTick = 0;
                }
                break;

            case BLAST:
                phase = Phase.BLAST_DELAY;
                phaseTick = 0;
                break;

            case BLAST_DELAY:
                phaseTick++;
                if (phaseTick >= BLAST_TO_SPIN_DELAY) {
                    phase = Phase.SPIN;
                    phaseTick = 0;
                }
                break;

            case SPIN:
                phaseTick++;
                if (phaseTick >= spinTicks) {
                    attackIndex = 0;
                    phase = Phase.COOLDOWN;
                    phaseTick = 0;
                }
                break;

            case COOLDOWN:
                phaseTick++;
                int cooldown = (attackIndex == 0) ? COOLDOWN_TICKS_BLAST : COOLDOWN_TICKS_STAB;

                if (phaseTick >= cooldown) {
                    if (pendingTarget != null && pendingTarget.isAlive()) {
                        currentTarget = pendingTarget;
                        pendingTarget = null;
                        attackIndex = 0;
                        phase = Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        hasDealtDamage = false;
                    } else if (currentTarget != null && currentTarget.isAlive()) {
                        phase = Phase.CHARGE;
                        phaseTick = 0;
                        blastLockedDirection = null;
                        hasDealtDamage = false;
                    } else {
                        phase = Phase.IDLE;
                        phaseTick = 0;
                        attackIndex = 0;
                    }
                }
                break;

            
        }
    }

    // 戳刺
    private void startStab() {
        if (currentTarget == null) { phase = Phase.IDLE; phaseTick = 0; return; }
        phase = Phase.STAB; phaseTick = 0; thrustTick = 1; hasDealtDamage = false;
        formationPos = position(); thrustStartPos = formationPos;
        Vec3 targetCenter = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
        Vec3 toTarget = targetCenter.subtract(formationPos);
        thrustDirection = toTarget.normalize();
        thrustTarget = targetCenter;
        thrustEndPos = targetCenter.add(thrustDirection.scale(STAB_OVERSHOOT));
        entityData.set(IS_THRUSTING, true);
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3f, 1.5f);
    }

    private void updateThrust() {
        if (thrustTick <= 0) return;
        double progress = thrustTick / (double) STAB_TICKS;
        Vec3 currentPos; Vec3 motionDir;
        if (progress < 0.45) {
            double t = progress / 0.45;
            double eased = easeOutCubic(t);
            currentPos = thrustStartPos.lerp(thrustTarget, eased);
            motionDir = thrustDirection != null ? thrustDirection : Vec3.ZERO;
        } else if (progress < 0.65) {
            double t = (progress - 0.45) / 0.2;
            double shake = Math.sin(t * Math.PI * 4) * 0.12;
            currentPos = thrustTarget.add(thrustDirection.scale(shake));
            motionDir = thrustDirection != null ? thrustDirection : Vec3.ZERO;
            if (!hasDealtDamage && currentTarget instanceof LivingEntity living) {
                applyStabDamage(living); hasDealtDamage = true;
                //spawnHitParticles(thrustTarget);
            }
        } else {
            double t = (progress - 0.65) / 0.35;
            double eased = easeInCubic(t);
            currentPos = thrustTarget.lerp(formationPos, eased);
            motionDir = formationPos.subtract(thrustTarget).normalize();
        }
        setPos(currentPos.x, currentPos.y, currentPos.z); hasImpulse = true;
        if (motionDir.lengthSqr() > 0.0001 && progress < 0.65) {
            float yaw = (float) Math.toDegrees(Math.atan2(-motionDir.x, motionDir.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(motionDir.y,
                Math.sqrt(motionDir.x*motionDir.x + motionDir.z*motionDir.z)));
            currentYaw = yaw; currentPitch = pitch;
            setYRot(yaw); setXRot(pitch);
        }
        thrustTick++;
        if (thrustTick > STAB_TICKS) {
            thrustTick = 0;
            entityData.set(IS_THRUSTING, false);
            returnStartPos = position();
            returnTick = RETURN_TICKS;
        }
    }

    private void updateReturnToFormation(LivingEntity owner) {
        returnTick--;
        double progress = 1.0 - (returnTick / (double) RETURN_TICKS);
        double eased = easeOutCubic(progress);

        // 使用缓存的三角函数
        Vec3 offset = FORMATION_OFFSETS[slotIndex];
        double rx = offset.x * lastCosYaw - offset.z * lastSinYaw;
        double rz = offset.x * lastSinYaw + offset.z * lastCosYaw;
        Vec3 formationTarget = owner.position().add(rx, offset.y, rz);
        double swayY = Math.sin(tickCount * IDLE_SWAY_FREQ + slotIndex * 2.1) * IDLE_SWAY_AMP;
        formationTarget = formationTarget.add(0, swayY, 0);

        Vec3 currentPos = returnStartPos.lerp(formationTarget, eased);
        setPos(currentPos.x, currentPos.y, currentPos.z);
        hasImpulse = true;
        if (returnTick <= 0) returnTick = 0;
    }

    private void endStab() {
        thrustTick = 0;
        thrustDirection = null;
        hasDealtDamage = false;
        entityData.set(IS_THRUSTING, false);
    }

    private void updateSpin(LivingEntity owner) {
        double progress = phaseTick / (double) spinTicks;

        double baseAngle = slotIndex * (2 * Math.PI / 3);
        double spinAngle = baseAngle - progress * 2 * Math.PI;

        float yawRad = owner.getYRot() * 0.0174533f;

        double behindX = Math.sin(yawRad) * SPIN_CENTER_DISTANCE;
        double behindZ = -Math.cos(yawRad) * SPIN_CENTER_DISTANCE;

        double centerX = owner.getX() + behindX;
        double centerY = owner.getY() + SPIN_HEIGHT;
        double centerZ = owner.getZ() + behindZ;

        double offsetRight = Math.cos(spinAngle) * SPIN_RADIUS;
        double offsetUp = Math.sin(spinAngle) * SPIN_RADIUS;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double x = centerX + offsetRight * rightX;
        double y = centerY + offsetUp;
        double z = centerZ + offsetRight * rightZ;

        Vec3 targetPos = new Vec3(x, y, z);

        Vec3 newPos = position().lerp(targetPos, 0.2f);
        setPos(newPos.x, newPos.y, newPos.z);
        hasImpulse = true;

        Vec3 center = new Vec3(centerX, centerY, centerZ);
        Vec3 fromCenter = newPos.subtract(center).normalize();

        currentYaw = (float) Math.toDegrees(Math.atan2(-fromCenter.x, fromCenter.z));
        currentPitch = (float) -Math.toDegrees(Math.asin(fromCenter.y));
        setYRot(currentYaw);
        setXRot(currentPitch);
    }

    private void applyStabDamage(LivingEntity target) {
        LivingEntity owner = getOwner();
        if (owner == null) return;
        float multiplier = STAB_DAMAGE_MULTIPLIERS[
            Math.min(attackIndex, STAB_DAMAGE_MULTIPLIERS.length - 1)];
        float damage = baseDamage * multiplier;
        var spell = SpellRegistry.getSpell(
            new net.minecraft.resources.ResourceLocation("arcane_mag", "lizhi_yan"));
        SpellDamageSource source = SpellDamageSource.source(this, owner, spell).setIFrames(5);
        DamageSources.applyDamage(target, damage, source);
    }

    // 冲击波
    private void startBlast(LivingEntity owner) {
        phase = Phase.BLAST;
        phaseTick = 0;

        Vec3 lookDir = getTargetDirection();
        if (lookDir.lengthSqr() < 0.0001) {
            lookDir = Vec3.directionFromRotation(getXRot(), getYRot());
        }
        if (blastLockedDirection == null && currentTarget != null) {
            Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
            lookDir = tc.subtract(position()).normalize();
        }

        Vec3 startPos = position().add(0, 0.3, 0);
        Vec3 endPos = startPos.add(lookDir.scale(range));

        var blockHit = level().clip(new ClipContext(startPos, endPos,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 hitLoc = blockHit.getType() == HitResult.Type.MISS
            ? endPos : blockHit.getLocation();
        float beamDistance = (float) startPos.distanceTo(hitLoc);

        float[] tint = BLAST_TINTS[slotIndex];
        LizhiYanBlastVisualEntity blastEntity = new LizhiYanBlastVisualEntity(
            EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
            level(), startPos, hitLoc, lookDir, tint
        );
        level().addFreshEntity(blastEntity);

        LivingEntity hitEntity = findEntityOnPath(owner, startPos, hitLoc);
        if (hitEntity != null) {
            float damage = baseDamage * BLAST_DAMAGE_MULTIPLIER;
            var spell = SpellRegistry.getSpell(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("arcane_mag", "lizhi_yan"));
            SpellDamageSource source = SpellDamageSource.source(this, owner, spell).setIFrames(0);
            DamageSources.applyDamage(hitEntity, damage, source);
            //spawnBlastHitParticles(hitEntity.position().add(0, hitEntity.getBbHeight()/2, 0));
        }

        spawnBeamParticles(startPos, hitLoc, lookDir);
        level().playSound(null, getX(), getY(), getZ(),
            SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    @Nullable
    private LivingEntity findEntityOnPath(LivingEntity owner, Vec3 start, Vec3 end) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        AABB scanBox = new AABB(start, end).inflate(1.0);
        var entities = level().getEntities(this, scanBox, e -> {
            if (e == this || e == owner) return false;
            if (!(e instanceof LivingEntity living)) return false;
            if (e instanceof net.minecraft.world.entity.player.Player player
                && (currentTarget == null || !player.getUUID().equals(currentTarget.getUUID()))) {
                return false;
            }
            return living.isAlive() && living.canBeSeenAsEnemy();
        });
        for (Entity e : entities) {
            AABB entityBox = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> intercept = entityBox.clip(start, end);
            if (intercept.isPresent()) {
                double dist = start.distanceTo(intercept.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = (LivingEntity) e;
                }
            }
        }
        return closest;
    }

    private void spawnBeamParticles(Vec3 start, Vec3 end, Vec3 direction) {
    }

    private Vec3 getTargetDirection() {
        if (blastLockedDirection != null) return blastLockedDirection;
        if (currentTarget != null) {
            Vec3 tc = currentTarget.position().add(0, currentTarget.getBbHeight()/2, 0);
            return tc.subtract(position()).normalize();
        }
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    // 状态同步
    private void syncStateToClient() {
        int phaseId = phase.ordinal();
        if (phaseId != entityData.get(ATTACK_PHASE)) {
            entityData.set(ATTACK_PHASE, phaseId);
        }
        entityData.set(AIM_YAW, currentYaw);
        entityData.set(AIM_PITCH, currentPitch);
    }

    // 工具方法
    private static double easeOutCubic(double t) { return 1 - Math.pow(1 - t, 3); }
    private static double easeInCubic(double t) { return t * t * t; }

    private void spawnSummonParticles() {
        for (int i = 0; i < 10; i++) {
            level().addParticle(ParticleTypes.END_ROD,
                getX() + (random.nextDouble()-0.5)*0.4,
                getY() + (random.nextDouble()-0.5)*0.4,
                getZ() + (random.nextDouble()-0.5)*0.4,
                0, 0.05, 0);
        }
    }

    private void setInitialPosition(LivingEntity owner) {
        Vec3 offset = FORMATION_OFFSETS[slotIndex];
        float ownerYaw = owner.getYRot();
        double yawRad = ownerYaw * 0.0174533f;
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        double rx = offset.x * cos - offset.z * sin;
        double rz = offset.x * sin + offset.z * cos;
        setPos(owner.getX() + rx, owner.getY() + offset.y, owner.getZ() + rz);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            onAntiMagic(null); return true;
        }
        return false;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) onRemovedHelper(this);
        if (level().isClientSide) {
            for (int i = 0; i < 12; i++) {
                level().addParticle(ParticleTypes.END_ROD,
                    getX() + (random.nextDouble()-0.5),
                    getY() + (random.nextDouble()-0.5),
                    getZ() + (random.nextDouble()-0.5),
                    (random.nextDouble()-0.5)*0.1, 0.05, (random.nextDouble()-0.5)*0.1);
            }
        }
        super.remove(reason);
    }
}