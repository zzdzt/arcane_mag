package com.zzdzt.arcanemag.network;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ArcaneMagNetworking {

    public static final String PROTOCOL_VERSION = "2.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SpellCastMessage.class,
                SpellCastMessage::encode, SpellCastMessage::decode, SpellCastMessage::handle);
        CHANNEL.registerMessage(id++, StopCastMessage.class,
                StopCastMessage::encode, StopCastMessage::decode, StopCastMessage::handle);
        CHANNEL.registerMessage(id++, ModChargeSyncPacket.class,
                ModChargeSyncPacket::encode, ModChargeSyncPacket::decode, ModChargeSyncPacket::handle);

        ArcaneMag.LOGGER.info("ArcaneMag networking registered with {} packets.", id);
    }
}