package com.zzdzt.arcanemag.mixin;

import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.utils.GunCastManaContext;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 canBeCastedBy 的返回值上注入，当检测到枪械施法法力系数旗标时，
 * 将因法力不足而失败的检查结果覆盖为成功（如果折扣后法力足够）。
 *
 * 原理：
 * canBeCastedBy 内部使用 getManaCost() 做全额法力检查。
 * 此 Mixin 在 RETURN 时拦截结果：如果原始检查失败（mana < fullCost），
 * 但按折扣后费用（fullCost * coeff）检查通过，则覆写为 SUCCESS。
 *
 * SpellOnCastEvent（SpellManaEventHandler）负责在 castSpell 时实际扣除折扣后费用。
 */
@Mixin(value = AbstractSpell.class, remap = false)
public class SpellManaCostMixin {

    @Inject(method = "canBeCastedBy", at = @At("RETURN"), cancellable = true, remap = false)
    private void arcaneMag_overrideGunCastResult(int spellLevel, CastSource castSource,
                                                  MagicData playerMagicData, Player player,
                                                  CallbackInfoReturnable<CastResult> cir) {
        float coeff = GunCastManaContext.get(player.getUUID());
        if (coeff >= 1.0f) return;

        CastResult result = cir.getReturnValue();
        if (result.isSuccess()) return; 

        int baseCost = ((AbstractSpell) (Object) this).getManaCost(spellLevel);
        int reducedCost = Math.max(1, (int) (baseCost * coeff));

        if (playerMagicData.getMana() < reducedCost) return; // 折扣后也不够，保持原失败

        // 排除其他非魔力原因导致的失败
        if (playerMagicData.getPlayerCooldowns().isOnCooldown((AbstractSpell) (Object) this)) return;
        if (((AbstractSpell) (Object) this).requiresLearning()
                && !((AbstractSpell) (Object) this).isLearned(player)) return;
        if (castSource == CastSource.SCROLL) return; // 卷轴限制
        if (player.isCreative() && !ServerConfigs.CREATIVE_MANA_COST.get()) return;

        cir.setReturnValue(new CastResult(CastResult.Type.SUCCESS));
    }
}
