package com.zzdzt.arcanemag.utils;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.tacz.guns.entity.EntityKineticBullet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 精确命中坐标捕获器。
 *
 * 通过 Mixin 在 EntityKineticBullet.onHitEntity 的 HEAD 注入，
 * 将 TacHitResult.getLocation()（射线与实体碰撞盒的精确交点）暂存于此。
 * 后续事件处理（如 EntityHurtByGunEvent.Post）通过 consume() 取回坐标。
 *
 * 使用 WeakHashMap 避免子弹实体被回收后内存泄漏。
 */
public final class BulletImpactCapture {
    private static final Map<EntityKineticBullet, Vec3> EXACT_HITS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BulletImpactCapture() {}

    /**
     * 由 Mixin 调用：捕获子弹命中实体的精确坐标。
     */
    public static void capture(EntityKineticBullet bullet, Vec3 exactHit) {
        if (bullet != null && exactHit != null) {
            EXACT_HITS.put(bullet, exactHit);
        }
    }

    /**
     * 由事件处理调用：取回并移除精确命中坐标。
     * 如果 Mixin 未捕获到（事件顺序异常等），回退到射线裁剪 boundingBox。
     *
     * @param bullet 子弹实体
     * @param target 被命中的实体
     * @return 精确命中坐标（世界空间）
     */
    public static Vec3 consume(EntityKineticBullet bullet, LivingEntity target) {
        Vec3 exact = EXACT_HITS.remove(bullet);
        if (exact != null) return exact;

        // 回退：用子弹当前位置到下一 tick 位置的射线裁剪目标碰撞盒
        Vec3 start = bullet.position();
        Vec3 end = start.add(bullet.getDeltaMovement());
        return target.getBoundingBox().inflate(0.05D).clip(start, end)
                .orElse(target.getBoundingBox().getCenter());
    }

    /**
     * 查询但不移除（用于不需要消费坐标的场景）。
     */
    public static Vec3 peek(EntityKineticBullet bullet) {
        return EXACT_HITS.get(bullet);
    }
}
