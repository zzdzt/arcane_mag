package com.zzdzt.arcanemag.mixin.manamagazine;

import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.zzdzt.arcanemag.enchant.ManaMagazineLogic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 魔力弹匣（mana_magazine Perk）服务端触发点。
 */
@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class ManaMagazineMixin {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ShooterDataHolder data;

    @ModifyVariable(
        method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
        at = @At("STORE"),
        name = "noAmmo"
    )
    private boolean arcanemag$manaMagazine(boolean noAmmo) {
        if (!noAmmo) return false;
        if (!(shooter instanceof ServerPlayer)) return true;
        ItemStack gun = (data != null && data.currentGunItem != null) ? data.currentGunItem.get() : null;
        MagicData magicData = MagicData.getPlayerMagicData(shooter);
        float mana = magicData == null ? 0f : magicData.getMana();
        // tryConjure 成功 → noAmmo 翻转为 false；失败 → 保持 true（走换弹）
        return !ManaMagazineLogic.tryConjure(shooter, gun, mana, true);
    }
}
