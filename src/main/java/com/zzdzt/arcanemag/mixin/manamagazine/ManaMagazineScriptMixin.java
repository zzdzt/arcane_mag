package com.zzdzt.arcanemag.mixin.manamagazine;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.zzdzt.arcanemag.enchant.ManaMagazineLogic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 魔力弹匣（mana_magazine Perk）服务端 {@link ModernKineticGunScriptAPI} 触发点。
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class ManaMagazineScriptMixin {

    @Shadow
    private ItemStack itemStack;

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ShooterDataHolder dataHolder;

    @WrapMethod(method = "reduceAmmoOnce")
    private boolean arcanemag$manaMagazineReduce(Operation<Boolean> original) {
        boolean result = original.call();
        if (result) return true;

        // 中途空仓，仅玩家射手可烧蓝续弹
        if (!(shooter instanceof ServerPlayer)) return false;
        MagicData magicData = MagicData.getPlayerMagicData(shooter);
        float mana = magicData == null ? 0f : magicData.getMana();
        // tryConjure 成功（已写 1 发并扣蓝）→ 再调原方法消耗这发；失败 → 保持 false
        if (ManaMagazineLogic.tryConjure(shooter, itemStack, mana, true)) {
            return original.call();
        }
        return false;
    }

    @WrapMethod(method = "removeAmmoFromMagazine")
    private int arcanemag$manaMagazineBolt(int amount, Operation<Integer> original) {
        int result = original.call(amount);
        if (result != 0) return result; // 弹匣有弹，正常取出

        // 换弹流程中不介入（正常换弹会先填满弹匣，此处仅兜底）
        if (dataHolder != null && dataHolder.reloadStateType.isReloading()) return 0;
        if (!(shooter instanceof ServerPlayer)) return 0;

        MagicData magicData = MagicData.getPlayerMagicData(shooter);
        float mana = magicData == null ? 0f : magicData.getMana();
        // tryConjureForBolt 仅对栓动枪生效；成功（已写 1 发并扣蓝）→ 再调原方法取出入膛
        if (ManaMagazineLogic.tryConjureForBolt(shooter, itemStack, mana, true)) {
            return original.call(amount);
        }
        return 0;
    }
}
