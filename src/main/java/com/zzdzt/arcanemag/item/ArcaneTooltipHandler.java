package com.zzdzt.arcanemag.item;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.keybind.CastKeyHandler;
import com.zzdzt.arcanemag.utils.AttachmentDataUtils;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ArcaneTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof IGun) {
            renderGunTooltip(stack, event);
        } else if (stack.getItem() instanceof IAttachment) {
            renderAttachmentTooltip(stack, event);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static String getCastKeyName() {
        return CastKeyHandler.CAST_KEY != null 
            ? CastKeyHandler.CAST_KEY.getTranslatedKeyMessage().getString()
            : "F";
    }

    private static void renderGunTooltip(ItemStack gunStack, ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();

        // 法术信息
        SpellData spellData = MagazineSpellHelper.extractSpell(gunStack);
        if (spellData != null) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.translatable("tooltip.arcane_mag.gun_spell_header")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

            AbstractSpell spell = spellData.getSpell();
            int level = spellData.getLevel();

            tooltip.add(Component.translatable("tooltip.arcane_mag.spell_name")
                .append(": ")
                .append(spell.getDisplayName(event.getEntity()).copy().withStyle(ChatFormatting.GREEN)));

            tooltip.add(Component.translatable("tooltip.arcane_mag.spell_level")
                .append(": ")
                .append(Component.literal(String.valueOf(level)).withStyle(ChatFormatting.GOLD)));

            float manaCost = spell.getManaCost(level) 
                * ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue();
            tooltip.add(Component.translatable("tooltip.arcane_mag.mana_cost")
                .append(": ")
                .append(Component.literal(String.format("%.0f", manaCost)).withStyle(ChatFormatting.AQUA)));

            tooltip.add(Component.translatable("tooltip.arcane_mag.cast_hint", getCastKeyName())
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }

        // 升级法球信息
        Map<UpgradeOrbType, Integer> orbs = MagazineSpellHelper.getAllUpgradeOrbs(gunStack);
        if (!orbs.isEmpty()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.translatable("tooltip.arcane_mag.gun_upgrades_header")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

            orbs.forEach((type, count) -> {
                int percent = (int)(count * ArcaneMagConfig.ORB_SPELL_POWER_BONUS.get() * 100);
                String typeName = Component.translatable(type.getTranslationKey()).getString();
                tooltip.add(Component.literal("  • ")
                    .append(Component.literal(typeName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" +" + percent + "%").withStyle(ChatFormatting.GREEN)));
            });

            int total = MagazineSpellHelper.getTotalUpgradeOrbCount(gunStack);
            tooltip.add(Component.literal("  [Total: " + total + " orbs]")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void renderAttachmentTooltip(ItemStack stack, ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        AttachmentType type = AttachmentDataUtils.getAttachmentType(stack);

        tooltip.add(Component.literal(""));
        if (type == AttachmentType.EXTENDED_MAG) {
            tooltip.add(Component.translatable("tooltip.arcane_mag.magazine_type")
                .withStyle(ChatFormatting.GREEN));
        } else if (type != null) {
            tooltip.add(Component.translatable("tooltip.arcane_mag.attachment_type",
                    Component.translatable("attachment_type.tacz." + type.name().toLowerCase()))
                .withStyle(ChatFormatting.GRAY));
        }


        // 配件升级法球
        Map<UpgradeOrbType, Integer> orbs = AttachmentDataUtils.getUpgradeOrbs(stack);
        if (!orbs.isEmpty()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.translatable("tooltip.arcane_mag.upgrades_title")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

            orbs.forEach((orbType, count) -> {
                int percent = (int)(count * ArcaneMagConfig.ORB_SPELL_POWER_BONUS.get() * 100);
                String typeName = Component.translatable(orbType.getTranslationKey()).getString();
                tooltip.add(Component.literal("  • ")
                    .append(Component.literal(typeName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" +" + percent + "%").withStyle(ChatFormatting.GREEN)));
            });
        }
    }
}