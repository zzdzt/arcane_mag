package com.zzdzt.arcanemag.jei;

import com.zzdzt.arcanemag.ArcaneMag;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ArcaneAnvilRecipeCategory implements IRecipeCategory<ArcaneAnvilRecipe> {

    // 独立的 RecipeType，不覆盖铁魔法原版
    public static final RecipeType<ArcaneAnvilRecipe> ARCANE_MAG_ANVIL_TYPE =
        RecipeType.create(ArcaneMag.MODID, "arcane_mag_anvil", ArcaneAnvilRecipe.class);

    private static final ResourceLocation RECIPE_GUI_VANILLA =
        ResourceLocation.fromNamespaceAndPath(IronsSpellbooks.MODID, "textures/gui/gui_vanilla.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotDraw;

    public ArcaneAnvilRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.drawableBuilder(RECIPE_GUI_VANILLA, 0, 168, 125, 18)
            .addPadding(0, 15, 0, 0)
            .build();
        this.icon = guiHelper.createDrawableItemStack(
            new ItemStack(BlockRegistry.ARCANE_ANVIL_BLOCK.get()));
        this.slotDraw = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<ArcaneAnvilRecipe> getRecipeType() {
        return ARCANE_MAG_ANVIL_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.arcane_mag.arcane_anvil.title");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ArcaneAnvilRecipe recipe, IFocusGroup focuses) {
        IRecipeSlotBuilder baseSlot = builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
            .addItemStack(recipe.baseInput())
            .setBackground(slotDraw, -1, -1);

        builder.addSlot(RecipeIngredientRole.INPUT, 50, 1)
            .addItemStacks(recipe.modifierInputs())
            .setBackground(slotDraw, -1, -1);

        IRecipeSlotBuilder outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, 108, 1)
            .addItemStack(recipe.output())
            .setBackground(slotDraw, -1, -1);

        builder.createFocusLink(baseSlot, outputSlot);
    }

    @Override
    public void draw(ArcaneAnvilRecipe recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        
        String hintKey = recipe.recipeType() == ArcaneAnvilRecipe.RecipeType.SPELL_INSCRIBE
            ? "jei.arcane_mag.spell_inscribe.hint"
            : "jei.arcane_mag.upgrade.hint";
        
        String hint = Component.translatable(hintKey).getString();
        int width = mc.font.width(hint);
        guiGraphics.drawString(mc.font, hint, (getWidth() - width) / 2, 20, 0xAAAAAA, false);
    }

    @Override
    public int getWidth() {
        return 125;
    }

    @Override
    public int getHeight() {
        return 33;
    }
}