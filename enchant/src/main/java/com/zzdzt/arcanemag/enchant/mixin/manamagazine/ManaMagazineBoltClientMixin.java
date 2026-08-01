package com.zzdzt.arcanemag.enchant.mixin.manamagazine;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerBolt;
import com.zzdzt.arcanemag.enchant.ManaMagazineLogic;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 魔力弹匣（mana_magazine Perk）栓动枪客户端拉栓触发点。
 *
 * 服务端拉栓到位时烧蓝补 1 发入膛。
 * 栓动枪 tickAutoBolt 每 tick 自动请求拉栓，空仓狙击枪会自动烧蓝上膛
 */
@Mixin(value = LocalPlayerBolt.class, remap = false)
public abstract class ManaMagazineBoltClientMixin {

    @Shadow
    private LocalPlayer player;

    @WrapMethod(method = "bolt")
    private void arcanemag$manaMagazineBoltClient(Operation<Void> original) {
        ItemStack gun = player.getMainHandItem();
        boolean tempAmmo = false;
        // 仅空仓栓动枪且蓝够：临时写 1 发让 bolt() 内部 noAmmo 检查通过
        if (ManaMagazineLogic.canConjureForBolt(player, gun, ClientMagicData.getPlayerMana())) {
            IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 1);
            tempAmmo = true;
        }
        original.call();
        if (tempAmmo) {
            IGun.getIGunOrNull(gun).setCurrentAmmoCount(gun, 0);
        }
    }
}
