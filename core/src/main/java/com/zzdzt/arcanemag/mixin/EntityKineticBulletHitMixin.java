package com.zzdzt.arcanemag.mixin;

import com.zzdzt.arcanemag.utils.BulletImpactCapture;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获 TaCZ 子弹命中实体的精确坐标。
 *
 * 在 EntityKineticBullet.onHitEntity 的 HEAD 注入，
 * 将 TacHitResult.getLocation() 存入 BulletImpactCapture。
 * 公开的 EntityHurtByGunEvent 不携带精确命中坐标，
 * 此 Mixin 弥补该信息缺失。
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletHitMixin {

    @Inject(
        method = "onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void arcanemag$captureExactImpact(
            TacHitResult hitResult,
            Vec3 segmentStart,
            Vec3 segmentEnd,
            CallbackInfo callbackInfo
    ) {
        BulletImpactCapture.capture((EntityKineticBullet) (Object) this, hitResult.getLocation());
    }
}
