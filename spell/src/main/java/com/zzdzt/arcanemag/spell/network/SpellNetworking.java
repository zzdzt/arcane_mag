package com.zzdzt.arcanemag.spell.network;

import com.zzdzt.arcanemag.spell.ArcaneMagSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * spell 模块独立网络通道。
 *
 * 与 core 的 ArcaneMagNetworking 物理隔离，channel namespace 使用 {@code arcane_mag_spell}，
 * 避免与 core 的 SimpleChannel 冲突。仅注册 spell 自有的数据包。
 */
public class SpellNetworking {

    public static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ArcaneMagSpell.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, JammingWavesEffectPacket.class,
                JammingWavesEffectPacket::encode, JammingWavesEffectPacket::decode, JammingWavesEffectPacket::handle);
        CHANNEL.registerMessage(id++, TacticalDashDirectionPacket.class,
                TacticalDashDirectionPacket::encode, TacticalDashDirectionPacket::decode, TacticalDashDirectionPacket::handle);
        CHANNEL.registerMessage(id++, EntityStateUpdatePacket.class,
                EntityStateUpdatePacket::encode, EntityStateUpdatePacket::decode, EntityStateUpdatePacket::handle);

        ArcaneMagSpell.LOGGER.info("ArcaneMagSpell networking registered with {} packets.", id);
    }
}
