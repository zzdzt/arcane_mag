package com.zzdzt.arcanemag;

import com.mojang.logging.LogUtils;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.network.ArcaneMagNetworking;
import com.zzdzt.arcanemag.registry.CreativeTabRegistry;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import com.zzdzt.arcanemag.registry.EntityRegistry;
import com.zzdzt.arcanemag.registry.ItemRegistry;
import com.zzdzt.arcanemag.registry.SpellRegistry;
import com.zzdzt.arcanemag.registry.ParticleRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(ArcaneMag.MODID)
public class ArcaneMag {

    public static final String MODID = "arcane_mag";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcaneMag() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ArcaneMagConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ArcaneMagConfig.CLIENT_SPEC);

        // 注册网络
        ArcaneMagNetworking.register();

        // 注册效果
        EffectRegistry.register(modEventBus);

        // 注册实体
        EntityRegistry.register(modEventBus);

        // 注册法术
        SpellRegistry.register(modEventBus);

        // 注册物品
        ItemRegistry.register(modEventBus);

        // 注册创造模式物品栏
        CreativeTabRegistry.register(modEventBus);

        ParticleRegistry.register(modEventBus);

        generateExampleConfigs();

        LOGGER.info("ArcaneMag initialized.");
    }

    private static void generateExampleConfigs() {
        try {
            Path configDir = FMLPaths.CONFIGDIR.get().resolve(MODID);
            Files.createDirectories(configDir);

            Path chargeDir = configDir.resolve("spell_charge_mechanisms");
            if (!Files.exists(chargeDir)) {
                Files.createDirectories(chargeDir);

                Path exampleDir = chargeDir.resolve("example");
                Files.createDirectories(exampleDir);

                String exampleContent = """
                    {
                      "overdrive": false,
                      "stacks": true,
                      "passive_rate": 0.0
                    }
                    """;
                Files.writeString(exampleDir.resolve("_example_spell.json"), exampleContent);

                LOGGER.info("[ArcaneMag] Generated example config files in config/arcane_mag/spell_charge_mechanisms/");
            }
        } catch (IOException e) {
            LOGGER.warn("[ArcaneMag] Failed to generate example config files: {}", e.getMessage());
        }
    }
}