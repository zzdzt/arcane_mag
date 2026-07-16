package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.item.ArcaneMagScroll;
import com.zzdzt.arcanemag.item.ArcaneStaffItem;
import com.zzdzt.arcanemag.item.BlankHeartItem;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.util.ItemPropertiesHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneMag.MODID);

    // 急行军卷轴
    public static final RegistryObject<Scroll> SCROLL_FORCED_MARCH = ITEMS.register(
        "scroll_forced_march",
        () -> new ArcaneMagScroll(new Item.Properties())
    );

    //雷霆激流卷轴
    public static final RegistryObject<Scroll> SCROLL_THUNDER_STREAM = ITEMS.register(
        "scroll_thunder_stream",
        () -> new ArcaneMagScroll(new Item.Properties())
    );

    //干扰波纹卷轴
    public static final RegistryObject<Scroll> SCROLL_JAMMING_WAVES = ITEMS.register(
        "scroll_jamming_waves", () -> new ArcaneMagScroll(new Item.Properties())
    );

    //卷轴
    public static final RegistryObject<Scroll> SCROLL_SMOULDERING_FIRE = ITEMS.register(
        "scroll_smouldering_fire", () -> new ArcaneMagScroll(new Item.Properties())
    );

    //卷轴
    public static final RegistryObject<Scroll> SCROLL_JING_TING_JUE = ITEMS.register(
        "scroll_jing_ting_jue", () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 卷轴 
    public static final RegistryObject<Scroll> SCROLL_LIGHTNING_IMBUED = ITEMS.register(
        "scroll_lightning_imbued", () -> new ArcaneMagScroll(new Item.Properties()));

    // 卷轴 
    public static final RegistryObject<Scroll> SCROLL_ARCANE_AIM = ITEMS.register(
        "scroll_arcane_aim", () -> new ArcaneMagScroll(new Item.Properties()));

    // 液氮大炮卷轴 
    public static final RegistryObject<Scroll> SCROLL_LIQUID_NITROGEN_CANNON = ITEMS.register(
        "scroll_liquid_nitrogen_cannon", () -> new ArcaneMagScroll(new Item.Properties()));

    // ========== 奥术法杖 ==========
    public static final RegistryObject<Item> ARCANE_STAFF = ITEMS.register("arcane_staff",
        () -> new ArcaneStaffItem(
            ItemPropertiesHelper.equipment(1).rarity(net.minecraft.world.item.Rarity.EPIC),
            5.0f,    // 攻击伤害
            -3.0f,   // 攻击速度（标准法杖速度）
            // 额外属性
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.20, AttributeModifier.Operation.MULTIPLY_BASE),
            //new AttributeContainer(AttributeRegistry.MAX_MANA, 300, AttributeModifier.Operation.ADDITION),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.15, AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10, AttributeModifier.Operation.MULTIPLY_BASE)
        )
    );

    // ========== 空白之心 ==========
    public static final RegistryObject<Item> BLANK_HEART = ITEMS.register("blank_heart",
        () -> new BlankHeartItem(
            new Item.Properties()
                .stacksTo(1)
                .rarity(net.minecraft.world.item.Rarity.RARE)
        )
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    // ===== 辅助方法 =====

    public static ItemStack getForcedMarchScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.FORCED_MARCH.get(),
            1,
            SpellRarity.UNCOMMON
        );
    }

    public static ItemStack getThunderStreamScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.THUNDER_STREAM.get(),
            1,
            SpellRarity.UNCOMMON
        );
    }

    public static ItemStack getJammingWavesScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.JAMMING_WAVES.get(), 1, SpellRarity.RARE
        );
    }

    public static ItemStack getSmoulderingFireScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.SMOULDERING_FIRE.get(), 1, SpellRarity.UNCOMMON
        );
    }

    public static ItemStack getJingTingJueScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.JING_TING_JUE.get(), 1, SpellRarity.RARE
        );
    }

    public static ItemStack getLightningImbuedScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.LIGHTNING_IMBUED_BULLET.get(),1,SpellRarity.UNCOMMON
        );
    }

    public static ItemStack getArcaneAimScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.ARCANE_AIM.get(), 1, SpellRarity.RARE);
    }

    public static ItemStack getLiquidNitrogenCannonScroll() {
        return ArcaneMagScroll.createScrollStack(
            SpellRegistry.LIQUID_NITROGEN_CANNON.get(), 1, SpellRarity.RARE);
    }
}