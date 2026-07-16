package com.zzdzt.arcanemag.network;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.tacticaldash.TacticalDashSpell;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：发送 TacticalDash 的冲刺方向。
 * 
 * 使用 float 代替 double 传输方向向量（12字节 vs 24字节），
 * 并在服务端对客户端数据做严格验证（NaN/无穷/范围/归一化），
 * 防止恶意客户端写入异常值。
 */
public class TacticalDashDirectionPacket {

    // 方向向量各分量的最大绝对值限制（归一化向量各分量不超过 1.0）
    private static final float MAX_COMPONENT_ABS = 1.5f;

    private final float x, y, z;

    public TacticalDashDirectionPacket(Vec3 direction) {
        this.x = (float) direction.x;
        this.y = (float) direction.y;
        this.z = (float) direction.z;
    }

    public TacticalDashDirectionPacket(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(TacticalDashDirectionPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.x);
        buf.writeFloat(msg.y);
        buf.writeFloat(msg.z);
    }

    public static TacticalDashDirectionPacket decode(FriendlyByteBuf buf) {
        return new TacticalDashDirectionPacket(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(TacticalDashDirectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // 验证客户端数据：拒绝 NaN、无穷大、超范围值
            if (!isValidDirection(msg.x, msg.y, msg.z)) {
                ArcaneMag.LOGGER.warn("Received invalid TacticalDash direction from player {}: ({}, {}, {})",
                    player.getName().getString(), msg.x, msg.y, msg.z);
                return;
            }

            // 归一化方向向量，确保单位长度
            double lenSq = (double) msg.x * msg.x + (double) msg.y * msg.y + (double) msg.z * msg.z;
            if (lenSq < 1e-6) {
                // 零向量，视为无效
                return;
            }
            double len = Math.sqrt(lenSq);
            float nx = (float) ((double) msg.x / len);
            float ny = (float) ((double) msg.y / len);
            float nz = (float) ((double) msg.z / len);

            Vec3 validatedDirection = new Vec3(nx, ny, nz);
            TacticalDashSpell.receiveDashDirection(player, validatedDirection);
        });
        ctx.get().setPacketHandled(true);
    }

    // 验证方向向量分量是否合法（非 NaN、非无穷大、绝对值在合理范围内）
    private static boolean isValidDirection(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
            && Math.abs(x) <= MAX_COMPONENT_ABS
            && Math.abs(y) <= MAX_COMPONENT_ABS
            && Math.abs(z) <= MAX_COMPONENT_ABS;
    }

    public static void sendToServer(Vec3 direction) {
        ArcaneMagNetworking.CHANNEL.sendToServer(new TacticalDashDirectionPacket(direction));
    }
}
