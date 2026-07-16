package com.zzdzt.arcanemag.jei;

import com.tacz.guns.api.item.IAttachment;
import com.zzdzt.arcanemag.utils.AttachmentDataUtils;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;

public class ArcaneMagAttachmentSubtype implements IIngredientSubtypeInterpreter<ItemStack> {

    @Override
    public String apply(ItemStack stack, UidContext context) {
        StringBuilder sb = new StringBuilder();

        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            sb.append(attachment.getAttachmentId(stack).toString());
        }

        // 法术
        if (AttachmentDataUtils.hasInscribedSpell(stack)) {
            var spell = AttachmentDataUtils.getInscribedSpell(stack);
            if (spell != null) {
                sb.append("|spell=").append(spell.getSpell().getSpellId())
                  .append(":").append(spell.getLevel());
            }
        }

        // 法球
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