package com.zzdzt.arcanemag.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class RaycastTools {
    private RaycastTools() {}
    /**
     * 采样光束路径上的实体
     */
    public static Set<LivingEntity> sampleBeamHits(
            Level level,
            Vec3 start,
            Vec3 end,
            double radius,
            double step,
            Predicate<Entity> filter
    ) {
        var delta = end.subtract(start);
        var len = delta.length();
        if (len < 1e-6) return Set.of();

        var dir = delta.scale(1.0 / len);
        var broad = new AABB(start, end).inflate(radius + 0.5);
        var candidates = level.getEntities((Entity) null, broad, filter);
        var hits = new HashSet<LivingEntity>();

        int steps = Math.max(1, (int) Math.ceil(len / step));
        for (Entity e : candidates) {
            if (!(e instanceof LivingEntity living)) continue;
            var box = e.getBoundingBox().inflate(radius);

            for (int i = 0; i <= steps; ++i) {
                var t = i / (double) steps;
                var p = start.add(dir.scale(len * t));
                if (box.contains(p)) {
                    hits.add(living);
                    break;
                }
            }
        }
        return hits;
    }
}