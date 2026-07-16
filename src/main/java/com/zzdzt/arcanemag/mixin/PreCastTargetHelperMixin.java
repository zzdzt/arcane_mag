package com.zzdzt.arcanemag.mixin;

import com.zzdzt.arcanemag.spell.SpellTargetDetector;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 Utils.preCastTargetHelper —— 自学习目标检测系统的信号入口。
 *
 * 法术的 checkPreCastConditions 调用 preCastTargetHelper 时，
 * 此 Mixin 通过 SpellTargetDetector.markCalled() 设置跨调用帧旗标。
 * SpellCastHandler 读取此旗标判断法术是否有目标依赖。
 *
 * 拦截最外层 6 参数重载；Iron's Spells 三条重载链均转发至此入口。
 */
@Mixin(value = Utils.class, remap = false)
public class PreCastTargetHelperMixin {

    @Inject(
        method = "preCastTargetHelper",
        at = @At("HEAD"),
        remap = false
    )
    private static void onPreCastTargetHelper(Level level, LivingEntity caster,
                                               MagicData playerMagicData, AbstractSpell spell,
                                               int range, float aimAssist,
                                               CallbackInfoReturnable<Boolean> cir) {
        SpellTargetDetector.markCalled();
    }
}
