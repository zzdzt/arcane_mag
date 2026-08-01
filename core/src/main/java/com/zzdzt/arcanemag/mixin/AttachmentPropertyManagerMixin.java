package com.zzdzt.arcanemag.mixin;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.zzdzt.arcanemag.gun.GunPropertyContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin 注入到 TACZ 的 {@link AttachmentPropertyManager#postChangeEvent} 方法，
 * 在属性计算前将 shooter 写入 {@link GunPropertyContext}，计算完成后清理。
 */
@Mixin(value = AttachmentPropertyManager.class, remap = false)
public class AttachmentPropertyManagerMixin {

    @Inject(method = "postChangeEvent", at = @At("HEAD"), remap = false)
    private static void arcaneMag$onPostChangeEventHead(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        //如果上次调用因异常未能执行 RETURN 注入的 clear()，
        GunPropertyContext.clear();
        GunPropertyContext.setShooter(shooter);
    }

    @Inject(method = "postChangeEvent", at = @At("RETURN"), remap = false)
    private static void arcaneMag$onPostChangeEventReturn(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        GunPropertyContext.clear();
    }
}
