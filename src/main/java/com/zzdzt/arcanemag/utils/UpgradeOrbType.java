package com.zzdzt.arcanemag.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum UpgradeOrbType {
    FIRE("irons_spellbooks:fire_spell_power"),
    ICE("irons_spellbooks:ice_spell_power"),
    LIGHTNING("irons_spellbooks:lightning_spell_power"),
    HOLY("irons_spellbooks:holy_spell_power"),
    ENDER("irons_spellbooks:ender_spell_power"),
    BLOOD("irons_spellbooks:blood_spell_power"),
    EVOCATION("irons_spellbooks:evocation_spell_power"),
    NATURE("irons_spellbooks:nature_spell_power"),
    ELDRITCH("irons_spellbooks:eldritch_spell_power"),
    MANA("irons_spellbooks:max_mana"),
    COOLDOWN("irons_spellbooks:cooldown_reduction"),
    PROTECTION("irons_spellbooks:spell_resist");

    private final ResourceLocation attributeId;
    private final String translationKey;
    
    private static final Map<String, UpgradeOrbType> BY_NAME = new HashMap<>();

    static {
        for (UpgradeOrbType type : values()) {
            BY_NAME.put(type.name(), type);
        }
    }

    UpgradeOrbType(String attributeIdString) {
        this.attributeId = ResourceLocation.fromNamespaceAndPath(attributeIdString.split(":")[0], attributeIdString.split(":")[1]);
        this.translationKey = "item.irons_spellbooks." + name().toLowerCase() + "_upgrade_orb";
    }

    @Nullable
    public Attribute getAttribute() {
        return BuiltInRegistries.ATTRIBUTE.get(attributeId);
    }

    public ResourceLocation getAttributeId() {
        return attributeId;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    @Nullable
    public static UpgradeOrbType fromName(String name) {
        return BY_NAME.get(name);
    }

    @Nullable
    public static UpgradeOrbType fromItemId(String itemId) {
        for (UpgradeOrbType type : values()) {
            String typeName = type.name().toLowerCase();
            if (itemId.contains(typeName)) return type;
        }
        return null;
    }
}