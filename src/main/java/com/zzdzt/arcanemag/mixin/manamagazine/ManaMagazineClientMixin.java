package com.zzdzt.arcanemag.mixin.manamagazine;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import com.zzdzt.arcanemag.enchant.ManaMagazineLogic;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 魔力弹匣（mana_magazine Perk）客户端触发点。
 *
 * 客户端 {@code LocalPlayerShoot.preCheck(...)} 空仓时直接 {@code return NO_AMMO}
 * 用 @ModifyVariable 翻转 noAmmo：
 * 空仓且本地法力足够时把 noAmmo 翻转为 false，让客户端继续 doShoot 发包并播放原版射击动画。
 *
 */
@Mixin(value = LocalPlayerShoot.class, remap = false)
public abstract class ManaMagazineClientMixin {

    @Shadow
    private LocalPlayer player;

    @ModifyVariable(method = "preCheck", at = @At("STORE"), name = "noAmmo")
    private boolean arcanemag$manaMagazineClient(boolean noAmmo) {
        if (!noAmmo) return false;
        float mana = ClientMagicData.getPlayerMana();
        // canConjure 通过（闭膛/开膛、真空仓、蓝够）→ 翻转 noAmmo；否则保持 true（原版空仓流程）
        return !ManaMagazineLogic.canConjure(player, player.getMainHandItem(), mana);
    }

    @ModifyVariable(method = "doShoot", at = @At("STORE"), name = "maxCount")
    private int arcanemag$manaMagazineMaxCount(int maxCount) {
        // maxCount<1 即空仓；到达 doShoot 已隐含 preCheck 法力校验通过，补到 1 让客户端发包+播放动画
        if (maxCount >= 1) return maxCount;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return maxCount;
        if (EnchantmentRegistry.MANA_MAGAZINE.get().levelOnGun(gun) > 0 && iGun.getCurrentAmmoCount(gun) < 1) {
            return 1;
        }
        return maxCount;
    }
}
