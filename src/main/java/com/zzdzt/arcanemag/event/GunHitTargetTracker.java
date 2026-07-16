package com.zzdzt.arcanemag.event;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.zzdzt.arcanemag.ArcaneMag;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 追踪玩家最近一次攻击目标，并同步附近召唤物的攻击目标。
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class GunHitTargetTracker {

    // 默认目标过期时间：5秒（更短，更灵敏）
    public static final long TARGET_EXPIRY_MS = 5000L;

    // 召唤物目标同步范围
    public static final double SUMMON_TARGET_SYNC_RANGE = 64.0;

    // 存储每个玩家最后一次攻击的目标信息（统一：枪械+原版）
    private static final Map<UUID, TargetRecord> LAST_HIT_TARGETS = new ConcurrentHashMap<>();

    // 反射方法缓存：避免每次 isPlayerSummon 调用都执行 getMethod() 搜索
    // Optional.empty() 表示该类没有此方法（缓存"不存在"的结果，避免反复查找失败）
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> OWNER_UUID_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> SUMMONER_METHOD_CACHE = new ConcurrentHashMap<>();

    private static class TargetRecord {
        final UUID targetId;
        final long timestamp;
        final boolean isGunDamage;

        TargetRecord(UUID targetId, long timestamp, boolean isGunDamage) {
            this.targetId = targetId;
            this.timestamp = timestamp;
            this.isGunDamage = isGunDamage;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TARGET_EXPIRY_MS;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null) return;

        if (!(source.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) event.getEntity();

        String damageType = source.getMsgId();
        boolean isGun = isGunDamage(damageType);

        // 统一记录所有玩家攻击（枪械+原版近战/弓箭等）
        LAST_HIT_TARGETS.put(
            player.getUUID(),
            new TargetRecord(target.getUUID(), System.currentTimeMillis(), isGun)
        );

        player.setLastHurtMob(target);
        syncSummonTargets(player, target);

        ArcaneMag.LOGGER.debug("Player {} hit entity {} with {} (type: {}), synced summon targets", 
            player.getName().getString(), target.getName().getString(), 
            isGun ? "gun" : "vanilla", damageType);
    }

    private static void syncSummonTargets(ServerPlayer player, LivingEntity newTarget) {
        if (player.level() instanceof ServerLevel serverLevel) {
            AABB searchArea = player.getBoundingBox().inflate(SUMMON_TARGET_SYNC_RANGE);
            List<Mob> nearbyMobs = serverLevel.getEntitiesOfClass(
                Mob.class, 
                searchArea,
                mob -> isPlayerSummon(mob, player)
            );

            for (Mob summon : nearbyMobs) {
                if (summon.getTarget() != newTarget) {
                    if (canTarget(summon, newTarget)) {
                        summon.setTarget(newTarget);
                        ArcaneMag.LOGGER.debug("Synced summon {} target to {} for player {}",
                            summon.getName().getString(), 
                            newTarget.getName().getString(),
                            player.getName().getString());
                    }
                }
            }
        }
    }

    private static boolean isPlayerSummon(Mob mob, ServerPlayer player) {
        if (mob.getUUID().equals(player.getUUID())) return false;

        if (mob instanceof TamableAnimal tamable) {
            return player.getUUID().equals(tamable.getOwnerUUID());
        }

        // 使用缓存的 getOwnerUUID 方法，避免每次 getMethod() 搜索类方法列表
        Optional<Method> ownerUUIDMethod = OWNER_UUID_METHOD_CACHE.computeIfAbsent(
            mob.getClass(),
            clazz -> {
                try {
                    return Optional.of(clazz.getMethod("getOwnerUUID"));
                } catch (NoSuchMethodException e) {
                    return Optional.empty();
                }
            }
        );
        if (ownerUUIDMethod.isPresent()) {
            try {
                UUID ownerUUID = (UUID) ownerUUIDMethod.get().invoke(mob);
                if (player.getUUID().equals(ownerUUID)) {
                    return true;
                }
            } catch (Exception e) {
                // 调用失败（如返回 null），忽略
            }
        }

        // 使用缓存的 getSummoner 方法
        Optional<Method> summonerMethod = SUMMONER_METHOD_CACHE.computeIfAbsent(
            mob.getClass(),
            clazz -> {
                try {
                    return Optional.of(clazz.getMethod("getSummoner"));
                } catch (NoSuchMethodException e) {
                    return Optional.empty();
                }
            }
        );
        if (summonerMethod.isPresent()) {
            try {
                LivingEntity summoner = (LivingEntity) summonerMethod.get().invoke(mob);
                if (summoner == player) {
                    return true;
                }
            } catch (Exception e) {
                // 调用失败，忽略
            }
        }

        return false;
    }

    private static boolean canTarget(Mob summon, LivingEntity target) {
        if (summon.getUUID().equals(target.getUUID())) return false;

        TargetingConditions conditions = TargetingConditions.forCombat()
            .range(SUMMON_TARGET_SYNC_RANGE)
            .ignoreLineOfSight();

        return conditions.test(summon, target);
    }

    /**
     * 获取并消耗玩家最后一次攻击的目标。
     * 用于一次性使用场景（如施法时获取目标）。
     */
    @Nullable
    public static LivingEntity getAndApplyLastHitTarget(ServerPlayer player) {
        TargetRecord record = LAST_HIT_TARGETS.remove(player.getUUID());
        if (record == null) return null;

        if (record.isExpired()) {
            ArcaneMag.LOGGER.debug("Hit target expired for player {}", player.getName().getString());
            return null;
        }

        Level level = player.level();
        if (level instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(record.targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                player.setLastHurtMob(living);
                ArcaneMag.LOGGER.debug("Applied hit target {} to player {} lastHurtMob",
                    living.getName().getString(), player.getName().getString());
                return living;
            }
        }

        return null;
    }

    /**
     * 查看（但不消耗）玩家最后一次攻击的目标。
     * 用于浮游炮台等需要持续追踪玩家目标的场景。
     */
    @Nullable
    public static LivingEntity peekLastHitTarget(ServerPlayer player) {
        TargetRecord record = LAST_HIT_TARGETS.get(player.getUUID());
        if (record == null) return null;

        if (record.isExpired()) {
            LAST_HIT_TARGETS.remove(player.getUUID());
            return null;
        }

        Level level = player.level();
        if (level instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(record.targetId);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }

        LAST_HIT_TARGETS.remove(player.getUUID());
        return null;
    }

    /**
     * 获取玩家最后一次攻击目标的时间戳。
     */
    public static long getLastHitTimestamp(ServerPlayer player) {
        TargetRecord record = LAST_HIT_TARGETS.get(player.getUUID());
        return record != null ? record.timestamp : -1;
    }

    public static void clearTarget(ServerPlayer player) {
        LAST_HIT_TARGETS.remove(player.getUUID());
    }

    /**
     * 玩家下线时清理目标记录，防止内存泄漏。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HIT_TARGETS.remove(event.getEntity().getUUID());
    }

    private static boolean isGunDamage(String damageType) {
        if (damageType == null) return false;
        String lower = damageType.toLowerCase();
        return lower.contains("tacz") || 
               lower.contains("bullet") || 
               lower.contains("gunfire") || 
               lower.contains("gun_melee") ||
               lower.contains("bayonet") ||
               (lower.contains("melee") && lower.contains("gun"));
    }
}