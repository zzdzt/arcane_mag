package com.zzdzt.arcanemag.registry;

import com.mojang.serialization.Codec;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.particle.InkZapParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ParticleRegistry {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ArcaneMag.MODID);

    public static final RegistryObject<ParticleType<InkZapParticleOption>> INK_ZAP_PARTICLE =
        PARTICLES.register("ink_zap", () -> new ParticleType<InkZapParticleOption>(false, InkZapParticleOption.DESERIALIZER) {
            @Override
            public Codec<InkZapParticleOption> codec() {
                return InkZapParticleOption.CODEC;
            }
        });

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}