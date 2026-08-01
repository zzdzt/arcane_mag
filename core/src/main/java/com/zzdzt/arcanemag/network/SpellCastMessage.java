package com.zzdzt.arcanemag.network;

import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：施法请求消息
 */
public class SpellCastMessage {

    private final ResourceLocation spellId;
    private final int spellLevel;

    public SpellCastMessage(ResourceLocation spellId, int spellLevel) {
        this.spellId = spellId;
        this.spellLevel = spellLevel;
    }

    public static void encode(SpellCastMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.spellId);
        buf.writeVarInt(msg.spellLevel);
    }

    public static SpellCastMessage decode(FriendlyByteBuf buf) {
        return new SpellCastMessage(buf.readResourceLocation(), buf.readVarInt());
    }

    public static void handle(SpellCastMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SpellCastHandler.handleCastRequest(msg.spellId, msg.spellLevel, ctx.get().getSender());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendToServer(ResourceLocation spellId, int level) {
        ArcaneMagNetworking.CHANNEL.sendToServer(new SpellCastMessage(spellId, level));
    }

    public static void sendToServer(SpellData spellData) {
        sendToServer(spellData.getSpell().getSpellResource(), spellData.getLevel());
    }
}