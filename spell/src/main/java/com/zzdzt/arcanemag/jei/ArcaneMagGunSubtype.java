package com.zzdzt.arcanemag.jei;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.utils.AttachmentDataUtils;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;

public class ArcaneMagGunSubtype implements IIngredientSubtypeInterpreter<ItemStack> {

    @Override
    public String apply(ItemStack stack, UidContext context) {
        StringBuilder sb = new StringBuilder();

        if (stack.getItem() instanceof IGun gun) {
            sb.append(gun.getGunId(stack).toString());
        }

        var orbs = AttachmentDataUtils.getUpgradeOrbs(stack);
        if (!orbs.isEmpty()) {
            sb.append("|orbs=");
            orbs.forEach((type, count) -> 
                sb.append(type.name()).append("x").append(count).append(";")
            );
        }

        return sb.length() > 0 ? sb.toString() : NONE;
    }
}