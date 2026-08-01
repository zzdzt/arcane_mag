package com.zzdzt.arcanemag.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 统一的奥术铁砧配方数据
 * 
 * @param baseInput     基础物品（弹匣/配件/枪械）
 * @param modifierInputs 所有修饰物品（卷轴或法球）
 * @param output        输出物品
 * @param recipeType    配方类型：SPELL_INSCRIBE 或 UPGRADE
 */
public record ArcaneAnvilRecipe(
    ItemStack baseInput,
    List<ItemStack> modifierInputs,
    ItemStack output,
    RecipeType recipeType
) {
    public enum RecipeType {
        SPELL_INSCRIBE,
        UPGRADE
    }
}