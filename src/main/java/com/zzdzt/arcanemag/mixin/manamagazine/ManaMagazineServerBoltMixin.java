package com.zzdzt.arcanemag.mixin.manamagazine;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.zzdzt.arcanemag.enchant.ManaMagazineLogic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 魔力弹匣（mana_magazine Perk）栓动枪服务端拉栓启动触发点。
 */
@Mixin(value = LivingEntityBolt.class, remap = false)
public abstract class ManaMagazineServerBoltMixin {

    @Shadow
    private ShooterDataHolder data;

    @Shadow
    private LivingEntity shooter;

    @WrapMethod(method = "bolt")
    private void arcanemag$manaMagazineServerBolt(Operation<Void> original) {
        ItemStack gun = (data != null && data.currentGunItem != null) ? data.currentGunItem.get() : null;
        boolean tempAmmo = false;
        if (gun != null && shooter instanceof ServerPlayer) {
            MagicData magicData = MagicData.getPlayerMagicData(shooter);
            float mana = magicData == null ? 0f : magicData.getMana();
            if (ManaMagazineLogic.canConjureForBolt(shooter, gun, mana)) {
                IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 1);
                tempAmmo = true;
            }
        }
        original.call();
        if (tempAmmo) {
            IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 0);
        }
    }
}
