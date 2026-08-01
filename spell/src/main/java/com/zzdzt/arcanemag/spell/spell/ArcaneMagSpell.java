package com.zzdzt.arcanemag.spell;

import com.mojang.logging.LogUtils;
import com.zzdzt.arcanemag.spell.registry.EffectRegistry;
import com.zzdzt.arcanemag.spell.registry.EntityRegistry;
import com.zzdzt.arcanemag.spell.registry.ParticleRegistry;
import com.zzdzt.arcanemag.spell.registry.SpellItemRegistry;
import com.zzdzt.arcanemag.spell.registry.SpellRegistry;
import com.zzdzt.arcanemag.spell.network.SpellNetworking;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 法术模块入口（arcane_mag_spell）。
 *
 * 依赖 core（arcane_mag），ordering=AFTER 确保 core 先加载。
 * DeferredRegister 命名空间保持 {@code "arcane_mag"}，存档兼容。
 * 网络通道独立（arcane_mag_spell:main），与 core 隔离。
 */
@Mod("arcane_mag_spell")
public class ArcaneMagSpell {

    public static final String MODID = "arcane_mag_spell";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcaneMagSpell() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册网络（spell 自有通道）
        SpellNetworking.register();

        // 注册法术 / 效果 / 实体 / 粒子 / 卷轴物品（命名空间 arcane_mag，存档兼容）
        SpellRegistry.register(modEventBus);
        EffectRegistry.register(modEventBus);
        EntityRegistry.register(modEventBus);
        SpellItemRegistry.register(modEventBus);
        ParticleRegistry.register(modEventBus);

        LOGGER.info("ArcaneMagSpell initialized.");
    }
}
