package com.zzdzt.arcanemag.jei;

import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.utils.AttachmentDataUtils;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.UpgradeOrbItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ArcaneMagRecipeMaker {

    private ArcaneMagRecipeMaker() {}

    /**
     * 生成所有奥术铁砧配方
     */
    static List<ArcaneAnvilRecipe> generateAllRecipes() {
        List<ArcaneAnvilRecipe> recipes = new ArrayList<>();
        recipes.addAll(generateSpellInscribeRecipes());
        recipes.addAll(generateUpgradeRecipes());
        return recipes;
    }

    // 法术铭刻配方

    private static List<ArcaneAnvilRecipe> generateSpellInscribeRecipes() {
        List<ArcaneAnvilRecipe> recipes = new ArrayList<>();
        List<ItemStack> allScrolls = collectAllScrolls();

        TimelessAPI.getAllCommonAttachmentIndex().forEach(entry -> {
            if (entry.getValue().getPojo().getType() != AttachmentType.EXTENDED_MAG) return;

            ItemStack magazine = AttachmentItemBuilder.create()
                .setId(entry.getKey()).build();

            ItemStack exampleOutput = magazine.copy();
            SpellRegistry.getEnabledSpells().stream().findFirst().ifPresent(firstSpell -> {
                AttachmentDataUtils.inscribeSpell(exampleOutput, firstSpell, firstSpell.getMinLevel());
            });

            recipes.add(new ArcaneAnvilRecipe(
                magazine.copy(),
                allScrolls,
                exampleOutput,
                ArcaneAnvilRecipe.RecipeType.SPELL_INSCRIBE
            ));
        });

        return recipes;
    }

    private static List<ItemStack> collectAllScrolls() {
        List<ItemStack> scrolls = new ArrayList<>();
        SpellRegistry.getEnabledSpells().forEach(spell -> {
            for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
                ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                ISpellContainer.createScrollContainer(spell, level, scroll);
                scrolls.add(scroll);
            }
        });
        return scrolls;
    }

    // 升级配方

    private static List<ArcaneAnvilRecipe> generateUpgradeRecipes() {
        List<ArcaneAnvilRecipe> recipes = new ArrayList<>();
        List<ItemStack> allOrbs = collectAllUpgradeOrbs();
        List<ItemStack> upgradeableItems = collectUpgradeableItems();

        for (ItemStack baseItem : upgradeableItems) {
            ItemStack exampleOutput = baseItem.copy();
            allOrbs.stream().findFirst().ifPresent(firstOrb -> {
                if (firstOrb.getItem() instanceof UpgradeOrbItem orbItem) {
                    int maxOrbs = ArcaneMagConfig.MAX_UPGRADE_ORBS_PER_ITEM.get();
                    AttachmentDataUtils.addUpgradeOrb(exampleOutput, orbItem, maxOrbs);
                }
            });

            recipes.add(new ArcaneAnvilRecipe(
                baseItem.copy(),
                allOrbs,
                exampleOutput,
                ArcaneAnvilRecipe.RecipeType.UPGRADE
            ));
        }

        return recipes;
    }

    private static List<ItemStack> collectUpgradeableItems() {
        List<ItemStack> items = Lists.newArrayList();

        if (ArcaneMagConfig.ALLOW_UPGRADE_ORBS_ON_GUNS.get()) {
            TimelessAPI.getAllCommonGunIndex().forEach(entry -> {
                items.add(GunItemBuilder.create().setId(entry.getKey()).build());
            });
        }

        if (ArcaneMagConfig.ALLOW_UPGRADE_ORBS_ON_ATTACHMENTS.get()) {
            TimelessAPI.getAllCommonAttachmentIndex().forEach(entry -> {
                if (entry.getValue().getPojo().getType() != AttachmentType.NONE) {
                    items.add(AttachmentItemBuilder.create().setId(entry.getKey()).build());
                }
            });
        }

        return items;
    }

    private static List<ItemStack> collectAllUpgradeOrbs() {
        List<ItemStack> orbs = new ArrayList<>();
        for (UpgradeOrbType type : UpgradeOrbType.values()) {
            BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof UpgradeOrbItem)
                .filter(item -> matchesOrbType(item, type))
                .findFirst()
                .ifPresent(orbItem -> orbs.add(new ItemStack(orbItem)));
        }
        return orbs;
    }

    private static boolean matchesOrbType(Item item, UpgradeOrbType type) {
        String descId = item.getDescriptionId();
        String typeName = type.name().toLowerCase();
        return descId.contains(typeName);
    }
}