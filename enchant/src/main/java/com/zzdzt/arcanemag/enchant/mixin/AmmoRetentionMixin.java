package com.zzdzt.arcanemag.enchant.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 节约风气（ammo_retention Perk）触发点。
 *
 * 拦截 TACZ {@code ModernKineticGunScriptAPI.shootOnce(boolean consumeAmmo)}：
 * 当本次射击本应消耗弹药时，按枪上 ammo_retention 附魔等级 roll 概率，
 * 命中则将 consumeAmmo 翻转为 false，从源头阻止弹药消耗（而非扣后返还）。
 *
 * 附魔刻在配件上，故用 levelOnGun 遍历配件取最高等级。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class AmmoRetentionMixin {

    @Shadow
    private ItemStack itemStack;

    @WrapMethod(method = "shootOnce")
    private void arcanemag$ammoRetention(boolean consumeAmmo, Operation<Void> original) {
        // 本次射击本就不消耗弹药（技能/特殊模式），直接放行
        if (!consumeAmmo) {
            original.call(false);
            return;
        }

        int level = EnchantmentRegistry.AMMO_RETENTION.get().levelOnGun(itemStack);
        if (level > 0) {
            double chance = Math.min(1.0, level * EnchantConfig.AMMO_RETENTION_CHANCE_PER_LEVEL.get());
            if (Math.random() < chance) {
                original.call(false); // 保留弹药
                return;
            }
        }

        original.call(true);
    }
}
