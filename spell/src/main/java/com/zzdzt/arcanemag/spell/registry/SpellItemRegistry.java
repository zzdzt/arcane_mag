package com.zzdzt.arcanemag.spell.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.item.ArcaneMagScroll;

import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 法术卷轴物品注册（spell 模块）。
 *
 * 从 ItemRegistry 拆分而来，避免 core（ItemRegistry）反向依赖 spell（SpellRegistry）。
 * 命名空间保持 {@link ArcaneMag#MODID}，存档兼容。
 */
public final class SpellItemRegistry {
    private SpellItemRegistry() {}

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneMag.MODID);

    // 急行军卷轴
    public static final RegistryObject<Scroll> SCROLL_FORCED_MARCH = ITEMS.register(
        "scroll_forced_march",
        () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 雷霆激流卷轴
    public static final RegistryObject<Scroll> SCROLL_THUNDER_STREAM = ITEMS.register(
        "scroll_thunder_stream",
        () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 干扰波纹卷轴
    public static final RegistryObject<Scroll> SCROLL_JAMMING_WAVES = ITEMS.register(
        "scroll_jamming_waves", () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 阴燃之火卷轴
    public static final RegistryObject<Scroll> SCROLL_SMOULDERING_FIRE = ITEMS.register(
        "scroll_smouldering_fire", () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 静霆决卷轴
    public static final RegistryObject<Scroll> SCROLL_JING_TING_JUE = ITEMS.register(
        "scroll_jing_ting_jue", () -> new ArcaneMagScroll(new Item.Properties())
    );

    // 闪电注魔卷轴
    public static final RegistryObject<Scroll> SCROLL_LIGHTNING_IMBUED = ITEMS.register(
        "scroll_lightning_imbued", () -> new ArcaneMagScroll(new Item.Properties()));

    // 奥术瞄准卷轴
    public static final RegistryObject<Scroll> SCROLL_ARCANE_AIM = ITEMS.register(
        "scroll_arcane_aim", () -> new ArcaneMagScroll(new Item.Properties()));

    // 液氮大炮卷轴
    public static final RegistryObject<Scroll> SCROLL_LIQUID_NITROGEN_CANNON = ITEMS.register(
        "scroll_liquid_nitrogen_cannon", () -> new ArcaneMagScroll(new Item.Properties()));

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
