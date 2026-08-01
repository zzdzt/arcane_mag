package com.zzdzt.arcanemag.jei;

import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.init.ModItems;
import com.zzdzt.arcanemag.ArcaneMag;
import io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilScreen;
import io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeCategory;  // 铁魔法原版
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class ArcaneMagJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(
            VanillaTypes.ITEM_STACK,
            ModItems.ATTACHMENT.get(),
            new ArcaneMagAttachmentSubtype()
        );

        GunItemManager.getAllGunItems().forEach(itemSupplier -> {
            registration.registerSubtypeInterpreter(
                VanillaTypes.ITEM_STACK,
                itemSupplier.get(),
                new ArcaneMagGunSubtype()
            );
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();

        registration.addRecipeCategories(new com.zzdzt.arcanemag.jei.ArcaneAnvilRecipeCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
            com.zzdzt.arcanemag.jei.ArcaneAnvilRecipeCategory.ARCANE_MAG_ANVIL_TYPE,
            ArcaneMagRecipeMaker.generateAllRecipes()
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack anvil = new ItemStack(BlockRegistry.ARCANE_ANVIL_BLOCK.get());
        
        registration.addRecipeCatalyst(anvil, 
            com.zzdzt.arcanemag.jei.ArcaneAnvilRecipeCategory.ARCANE_MAG_ANVIL_TYPE);
        
        registration.addRecipeCatalyst(anvil, 
            ArcaneAnvilRecipeCategory.ARCANE_ANVIL_RECIPE_RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(
            ArcaneAnvilScreen.class,
            102, 48, 22, 15,

            ArcaneAnvilRecipeCategory.ARCANE_ANVIL_RECIPE_RECIPE_TYPE,

            com.zzdzt.arcanemag.jei.ArcaneAnvilRecipeCategory.ARCANE_MAG_ANVIL_TYPE
        );
    }
}