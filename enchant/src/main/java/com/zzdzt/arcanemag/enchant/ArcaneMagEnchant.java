package com.zzdzt.arcanemag.enchant;

import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.event.enchant.EnchantModuleRegistration;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 附魔模块入口（arcane_mag_enchant）。
 *
 * 依赖 core（arcane_mag），ordering=AFTER 确保 core 先加载。
 * DeferredRegister 命名空间保持 {@code "arcane_mag"}，存档兼容。
 * config 物理独立：arcane_mag_enchant-server.toml。
 */
@Mod("arcane_mag_enchant")
public class ArcaneMagEnchant {

    public static final String MODID = "arcane_mag_enchant";

    public ArcaneMagEnchant() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置（enchant 专属，物理独立于 core）
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EnchantConfig.SERVER_SPEC);

        // 注册附魔（命名空间 arcane_mag，存档兼容）
        EnchantmentRegistry.register(modEventBus);
    }
}
