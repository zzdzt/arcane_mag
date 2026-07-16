package com.zzdzt.arcanemag.network;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 通用实体状态更新包 —— 用于高频或大数据量的实体状态同步
 */
public class EntityStateUpdatePacket {
    
    public static final byte TYPE_THUNDER_BEAM = 1;      
    public static final byte TYPE_INK_STRIKE = 2;        
    
    private final int entityId;
    private final byte packetType;
    private final byte[] payload;

    public EntityStateUpdatePacket(int entityId, byte packetType, byte[] payload) {
        this.entityId = entityId;
        this.packetType = packetType;
        this.payload = payload;
    }

    public static void encode(EntityStateUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.packetType);
        buf.writeByteArray(msg.payload);
    }

    public static EntityStateUpdatePacket decode(FriendlyByteBuf buf) {
        return new EntityStateUpdatePacket(
            buf.readVarInt(),
            buf.readByte(),
            buf.readByteArray()
        );
    }

    public static void handle(EntityStateUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            
            Entity entity = level.getEntity(msg.entityId);
            if (entity == null) return;
            
            switch (msg.packetType) {
                case TYPE_THUNDER_BEAM -> handleThunderBeam(entity, msg.payload);
                case TYPE_INK_STRIKE -> handleInkStrike(entity, msg.payload);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleThunderBeam(Entity entity, byte[] payload) {
        if (payload.length < 5) return;
        if (entity instanceof com.zzdzt.arcanemag.spell.thunderstream.ThunderStreamEntity beam) {
            beam.handleClientBeamUpdate(payload);
        }
    }

    private static void handleInkStrike(Entity entity, byte[] payload) {
        if (payload.length < 2) return;
        if (entity instanceof com.zzdzt.arcanemag.spell.jingtingjue.InkThunderStrikeEntity strike) {
            strike.handleClientStrikeTrigger(payload);
        }
    }

    // 便捷发送方法
    public static void sendThunderBeamUpdate(Entity beam, int tier, float length) {
        byte[] payload = new byte[5];
        payload[0] = (byte) tier;
        writeFloat(payload, 1, length);
        
        ArcaneMagNetworking.CHANNEL.send(
            PacketDistributor.TRACKING_ENTITY.with(() -> beam),
            new EntityStateUpdatePacket(beam.getId(), TYPE_THUNDER_BEAM, payload)
        );
    }

    public static void sendInkStrikeTrigger(Entity strike, int strikeCount, int colorTier) {
        byte[] payload = new byte[2];
        payload[0] = (byte) strikeCount;
        payload[1] = (byte) colorTier;
        
        ArcaneMagNetworking.CHANNEL.send(
            PacketDistributor.TRACKING_ENTITY.with(() -> strike),
            new EntityStateUpdatePacket(strike.getId(), TYPE_INK_STRIKE, payload)
        );
    }

    // 工具方法
    public static void writeFloat(byte[] arr, int offset, float val) {
        int bits = Float.floatToIntBits(val);
        arr[offset] = (byte)(bits);
        arr[offset+1] = (byte)(bits >> 8);
        arr[offset+2] = (byte)(bits >> 16);
        arr[offset+3] = (byte)(bits >> 24);
    }

    public static float readFloat(byte[] arr, int offset) {
        int bits = (arr[offset] & 0xFF) | ((arr[offset+1] & 0xFF) << 8) 
                 | ((arr[offset+2] & 0xFF) << 16) | ((arr[offset+3] & 0xFF) << 24);
        return Float.intBitsToFloat(bits);
    }
}