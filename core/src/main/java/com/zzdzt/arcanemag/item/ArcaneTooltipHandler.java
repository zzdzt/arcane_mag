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
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        if (spellData != null && event.getEntity() instanceof LocalPlayer player) {
            AbstractSpell spell = spellData.getSpell();
            int spellLevel = spell.getLevelFor(spellData.getLevel(), player);

            // 标题行：法术名 + 等级（学派颜色）
            tooltip.add(Component.empty());
            tooltip.add(TooltipsUtils.getTitleComponent(spellData, player));

            // 法术专属描述
            spell.getUniqueInfo(spellLevel, player).forEach(line ->
                tooltip.add(Component.literal(" ").append(
                    line.withStyle(TooltipsUtils.getStyleFor(player, spell)))));

            // 施法时间（非瞬发时显示）
            if (spell.getCastType() != io.redspace.ironsspellbooks.api.spells.CastType.INSTANT) {
                String castTime = io.redspace.ironsspellbooks.api.util.Utils.timeFromTicks(
                    spell.getEffectiveCastTime(spellLevel, player), 2);
                tooltip.add(Component.literal(" ").append(
                    TooltipsUtils.getCastTimeComponent(spell.getCastType(), castTime)
                        .withStyle(ChatFormatting.BLUE)));
            }

            // 蓝耗（乘以 ArcaneMag 折扣系数）
            int rawCost = spell.getManaCost(spellLevel);
            if (rawCost > 0) {
                int actualCost = Math.round(rawCost
                    * ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue());
                tooltip.add(TooltipsUtils.getManaCostComponent(spell.getCastType(), actualCost)
                    .withStyle(ChatFormatting.BLUE));
            }

            // ArcaneMag 专属：施法按键提示
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
                tooltip.add(Component.translatable("tooltip.arcane_mag.orb_entry",
                    Component.literal(typeName).withStyle(ChatFormatting.YELLOW),
                    Component.literal(String.valueOf(percent)).withStyle(ChatFormatting.GREEN)));
            });

            int total = MagazineSpellHelper.getTotalUpgradeOrbCount(gunStack);
            tooltip.add(Component.translatable("tooltip.arcane_mag.orb_total", total)
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
                    Component.translatable("tooltip.tacz.attachment." + type.name().toLowerCase()))
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
                tooltip.add(Component.translatable("tooltip.arcane_mag.orb_entry",
                    Component.literal(typeName).withStyle(ChatFormatting.YELLOW),
                    Component.literal(String.valueOf(percent)).withStyle(ChatFormatting.GREEN)));
            });
        }
    }
}