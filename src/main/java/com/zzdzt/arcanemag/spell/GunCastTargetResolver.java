package com.zzdzt.arcanemag.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.AntiMagicSusceptible;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 枪械施法目标解析器
 */
public final class GunCastTargetResolver {

    private GunCastTargetResolver() {}

    // 默认参数，与铁魔法 preCastTargetHelper 一致
    private static final double DEFAULT_RANGE = 64.0;
    private static final float DEFAULT_AIMING_MARGIN = 0.35f;

    /**
     * 为法术解析目标，完全模拟 preCastTargetHelper 的行为
     */
    @Nullable
    public static LivingEntity resolveTarget(ServerPlayer player, AbstractSpell spell,
                                            double range, float margin) {
        return resolveTarget(player, range, margin, true, false);
    }

    /**
     * 完整版目标解析
     */
    @Nullable
    public static LivingEntity resolveTarget(ServerPlayer player, double range, float margin,
                                              boolean checkLineOfSight, boolean targetPlayers) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookDir = player.getLookAngle();

        //第一步：射线检测（准星直接对准的实体）
        LivingEntity directTarget = findDirectRaycastTarget(player, eyePos, lookDir, range,
            checkLineOfSight, targetPlayers, margin);  // ← 传入 margin
        if (directTarget != null) {
            return directTarget;
        }

        //第二步：扇形扫描（准星附近的最近实体）
        LivingEntity nearbyTarget = findNearestInCone(player, eyePos, lookDir, range, margin,
            checkLineOfSight, targetPlayers);
        if (nearbyTarget != null) {
            return nearbyTarget;
        }

        return null;
    }

    /**
     * 射线检测：找准星直接对准的实体 
     */
    @Nullable
    private static LivingEntity findDirectRaycastTarget(ServerPlayer player, Vec3 eyePos,
                                                        Vec3 lookDir, double range,
                                                        boolean checkLineOfSight,
                                                        boolean targetPlayers,
                                                        float margin) {  // ← 添加 margin 参数
        // 先检测方块，确定射线终点
        Vec3 rayEnd = eyePos.add(lookDir.scale(range));
        HitResult blockHit = player.level().clip(new ClipContext(
            eyePos, rayEnd,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        double actualRange = blockHit.getType() != HitResult.Type.MISS
            ? blockHit.getLocation().distanceTo(eyePos)
            : range;

        // AABB 检测射线上的实体
        AABB searchBox = new AABB(eyePos, eyePos).inflate(actualRange);
        Vec3 rayEndClamped = eyePos.add(lookDir.scale(actualRange));

        // 找射线上的最近实体
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : player.level().getEntities(player, searchBox,
            e -> e instanceof LivingEntity && isValidTarget(player, (LivingEntity) e, targetPlayers))) {

            // 计算实体与射线的最近距离
            Optional<Vec3> intercept = entity.getBoundingBox().clip(eyePos, rayEndClamped);
            if (intercept.isPresent()) {
                double dist = intercept.get().distanceToSqr(eyePos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            } else {
                // 实体不在射线上，但可能在射线附近（bbInflation 效果）
                double dist = entity.distanceToSqr(eyePos);
                if (dist < closestDist && isInAimingCone(eyePos, lookDir, entity, margin)) {  // ← 现在 margin 可用了
                    closestDist = dist;
                    closest = entity;
                }
            }
        }

        if (closest instanceof LivingEntity living) {
            // 检查视线遮挡
            if (checkLineOfSight && !hasLineOfSight(player, living)) {
                return null;
            }
            return living;
        }

        return null;
    }

    /**
     * 扇形扫描：找准星方向锥形范围内的最近实体
     */
    @Nullable
    private static LivingEntity findNearestInCone(ServerPlayer player, Vec3 eyePos, Vec3 lookDir,
                                                   double range, float margin,
                                                   boolean checkLineOfSight,
                                                   boolean targetPlayers) {
        // 构建搜索 AABB
        AABB searchBox = new AABB(eyePos, eyePos).inflate(range);

        // 筛选有效目标
        Predicate<LivingEntity> filter = living -> {
            if (living == player) return false;
            if (!living.isAlive()) return false;
            if (!targetPlayers && living instanceof Player) return false;
            if (living instanceof TamableAnimal tamable
                && tamable.isTame()
                && tamable.getOwner() == player) return false;
            return true;
        };

        // 获取候选目标
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
            LivingEntity.class, searchBox, filter);

        // 优先选择角度最小的，其次距离最近的
        return candidates.stream()
            .filter(e -> isInAimingCone(eyePos, lookDir, e, margin))
            .filter(e -> !checkLineOfSight || hasLineOfSight(player, e))
            .min(Comparator.comparingDouble(e -> {
                Vec3 toTarget = e.getEyePosition(1.0f).subtract(eyePos).normalize();
                double angle = Math.acos(Mth.clamp(lookDir.dot(toTarget), -1.0, 1.0));
                return angle * 100 + eyePos.distanceTo(e.getEyePosition(1.0f)) * 0.1;
            }))
            .orElse(null);
    }

    /**
     * 检查实体是否在准星的瞄准锥内 
     */
    private static boolean isInAimingCone(Vec3 eyePos, Vec3 lookDir, Entity target, float margin) {
        Vec3 toTarget = target.getEyePosition(1.0f).subtract(eyePos);
        double dist = toTarget.length();
        if (dist < 0.001) return true;

        toTarget = toTarget.scale(1.0 / dist);
        double dot = lookDir.dot(toTarget);
        return dot >= Math.cos(margin);
    }

    /**
     * 检查玩家到目标是否有视线（无方块遮挡） 
     */
    private static boolean hasLineOfSight(ServerPlayer player, LivingEntity target) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 targetPos = target.getEyePosition(1.0f);

        HitResult result = player.level().clip(new ClipContext(
            eyePos, targetPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        return result.getType() == HitResult.Type.MISS
            || result.getLocation().distanceToSqr(targetPos) < 1.0;
    }

    /**
     * 检查目标是否有效（可被选取） 
     */
    private static boolean isValidTarget(ServerPlayer player, LivingEntity target,
                                         boolean targetPlayers) {
        if (target == player) return false;
        if (!target.isAlive()) return false;
        if (!targetPlayers && target instanceof Player) return false;

        if (target instanceof TamableAnimal tamable
            && tamable.isTame()
            && player.getUUID().equals(tamable.getOwnerUUID())) {
            return false;
        }

        return true;
    }

    /**
     * 快捷方法：使用法术的默认参数解析目标 
     */
    @Nullable
    public static LivingEntity resolveTargetForSpell(ServerPlayer player, AbstractSpell spell) {
        double range = DEFAULT_RANGE;
        float margin = DEFAULT_AIMING_MARGIN;

        String path = spell.getSpellResource().getPath().toLowerCase();
        if (path.equals("arcane_aim")) {
            range = 48.0;
            margin = 0.35f;
        } else if (path.equals("guiding_bolt")) {
            range = 64.0;
            margin = 0.35f;
        }

        return resolveTarget(player, range, margin, true, false);
    }

    /**
     * 创建 TargetEntityCastData 并设置到 MagicData 
     */
    public static void setTargetCastData(ServerPlayer player, LivingEntity target) {
        var magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
        magicData.setAdditionalCastData(new TargetEntityCastData(target));
    }
}