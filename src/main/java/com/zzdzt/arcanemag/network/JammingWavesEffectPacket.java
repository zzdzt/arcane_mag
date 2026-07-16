package com.zzdzt.arcanemag.network;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesDefenseEffectRenderEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：通知客户端渲染 Jamming Waves 六边形能量墙特效。
 */
public class JammingWavesEffectPacket {

    private final double x, y, z;
    private final float normalX, normalY, normalZ;
    private final float sizeScale;
    private final float lifetimeScale;
    private final boolean renderWave;
    private final boolean failed;

    public JammingWavesEffectPacket(Vec3 position, Vec3 normal, float sizeScale, float lifetimeScale,
                                     boolean renderWave, boolean failed) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.normalX = (float) normal.x;
        this.normalY = (float) normal.y;
        this.normalZ = (float) normal.z;
        this.sizeScale = sizeScale;
        this.lifetimeScale = lifetimeScale;
        this.renderWave = renderWave;
        this.failed = failed;
    }

    public static void encode(JammingWavesEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.normalX);
        buf.writeFloat(msg.normalY);
        buf.writeFloat(msg.normalZ);
        buf.writeFloat(msg.sizeScale);
        buf.writeFloat(msg.lifetimeScale);
        buf.writeBoolean(msg.renderWave);
        buf.writeBoolean(msg.failed);
    }

    public static JammingWavesEffectPacket decode(FriendlyByteBuf buf) {
        return new JammingWavesEffectPacket(
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    public static void handle(JammingWavesEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient(msg))
        );
        ctx.get().setPacketHandled(true);
    }

    private static void handleOnClient(JammingWavesEffectPacket msg) {
        JammingWavesDefenseEffectRenderEvent.enqueueEffect(
            new Vec3(msg.x, msg.y, msg.z),
            new Vec3(msg.normalX, msg.normalY, msg.normalZ),
            msg.sizeScale,
            msg.lifetimeScale,
            msg.renderWave,
            msg.failed
        );
    }
}
