package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 奥术魔法创造模式物品栏注册
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeTabRegistry {

    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcaneMag.MODID);

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }

    // 装备/武器/护甲物品栏
    public static final RegistryObject<CreativeModeTab> EQUIPMENT_TAB = TABS.register("arcane_mag_equipment", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ArcaneMag.MODID + ".equipment_tab"))
            .icon(() -> new ItemStack(ItemRegistry.ARCANE_STAFF.get()))
            .displayItems((enabledFeatures, entries) -> {
                entries.accept(ItemRegistry.ARCANE_STAFF.get());
                entries.accept(ItemRegistry.BLANK_HEART.get());
            })
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .build()
    );
}
