package com.zzdzt.arcanemag.enchant.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 多重扳机（multi_trigger Perk）触发点。
 *
 * 两处注入 TACZ {@code ModernKineticGunScriptAPI}：
 *
 * 1. @ModifyVariable shootOnce 的 cycles 局部变量：
 *    附魔等级叠加到 cycles 上，使一次扣扳机额外多射 N 发。
 *    不触发 applyShotgunDamageSpread 伤害分摊，单发伤害不稀释。
 *
 * 2. @WrapMethod reduceAmmoOnce：
 *    栓动枪（MANUAL_ACTION）射后膛内空，第二个 cycle 会因 hasAmmoInBarrel=false 失败。
 *    此处在原方法失败时，对栓动枪强制从弹匣扣弹绕过膛内机制，让多重射击能继续。
 *    弹药计数：cycle2 强制扣弹匣 1 发 + 后续拉栓上膛扣 1 发 = 射 2 发耗 2 发，正确。
 *
 * 附魔刻在配件上，故用 levelOnGun 遍历配件取最高等级。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class MultiTriggerMixin {

    @Shadow
    private ItemStack itemStack;

    @Shadow
    private AbstractGunItem abstractGunItem;

    @Shadow
    public abstract boolean useInventoryAmmo();

    @Shadow
    public abstract int consumeAmmoFromPlayer(int amount);

    @ModifyVariable(
        method = "shootOnce",
        at = @At("STORE"),
        name = "cycles"
    )
    private int arcanemag$multiTrigger(int original) {
        int level = EnchantmentRegistry.MULTI_TRIGGER.get().levelOnGun(itemStack);
        return level > 0 ? original + level : original;
    }

    @WrapMethod(method = "reduceAmmoOnce")
    private boolean arcanemag$forceChamber(Operation<Boolean> original) {
        boolean result = original.call();
        if (result) return true;

        // 仅在多重扳机附魔存在时干预
        int level = EnchantmentRegistry.MULTI_TRIGGER.get().levelOnGun(itemStack);
        if (level <= 0) return false;

        // 仅对栓动枪（MANUAL_ACTION）干预：射后膛内空导致第二个 cycle 失败
        Bolt boltType = TimelessAPI.getCommonGunIndex(abstractGunItem.getGunId(itemStack))
            .map(index -> index.getGunData().getBolt())
            .orElse(null);
        if (boltType != Bolt.MANUAL_ACTION) return false;

        // 从弹匣/背包强制扣 1 发，绕过膛内机制
        if (useInventoryAmmo()) {
            return consumeAmmoFromPlayer(1) == 1;
        }
        if (abstractGunItem.getCurrentAmmoCount(itemStack) >= 1) {
            abstractGunItem.reduceCurrentAmmoCount(itemStack);
            return true;
        }
        return false;
    }
}
