package com.zzdzt.arcanemag.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 客户端通知服务端停止持续施法的消息
 */
public class StopCastMessage {
    public StopCastMessage() {}
    public static void encode(StopCastMessage msg, FriendlyByteBuf buf) {}
    public static StopCastMessage decode(FriendlyByteBuf buf) { return new StopCastMessage(); }

    public static void handle(StopCastMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                SpellCastHandler.abortCastForPlayer(ctx.get().getSender());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendToServer() {
        ArcaneMagNetworking.CHANNEL.sendToServer(new StopCastMessage());
    }
}
