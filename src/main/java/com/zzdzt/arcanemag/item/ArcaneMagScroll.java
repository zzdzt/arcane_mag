package com.zzdzt.arcanemag.item;

import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ArcaneMagScroll extends Scroll {

    public ArcaneMagScroll(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack createScrollStack(io.redspace.ironsspellbooks.api.spells.AbstractSpell spell,
                                               int level,
                                               io.redspace.ironsspellbooks.api.spells.SpellRarity rarity) {
        Item.Properties properties = new Item.Properties().rarity(convertRarity(rarity));
        ItemStack stack = new ItemStack(new ArcaneMagScroll(properties));
        ISpellContainer.createScrollContainer(spell, level, stack);
        return stack;
    }

    private static net.minecraft.world.item.Rarity convertRarity(io.redspace.ironsspellbooks.api.spells.SpellRarity rarity) {
        return switch (rarity) {
            case COMMON -> net.minecraft.world.item.Rarity.COMMON;
            case UNCOMMON -> net.minecraft.world.item.Rarity.UNCOMMON;
            case RARE -> net.minecraft.world.item.Rarity.RARE;
            case EPIC -> net.minecraft.world.item.Rarity.EPIC;
            case LEGENDARY -> net.minecraft.world.item.Rarity.EPIC;
        };
    }
}