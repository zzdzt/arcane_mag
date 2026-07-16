package com.zzdzt.arcanemag.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.minecraft.Util;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.stream.IntStream;

/**
 * 水墨闪电粒子参数扩展
 */
public class InkZapParticleOption implements ParticleOptions {

    // 编码：x*10, y*10, z*10, width*100, colorType
    public static final Codec<InkZapParticleOption> CODEC = Codec.INT_STREAM.comapFlatMap(
        (stream) -> Util.fixedSize(stream, 5).map((arr) -> 
            new InkZapParticleOption(
                new Vec3(arr[0] / 10f, arr[1] / 10f, arr[2] / 10f),
                arr[3] / 100f,
                arr[4]
            )
        ),
        (option) -> IntStream.of(
            (int) (option.destination.x * 10f), 
            (int) (option.destination.y * 10f), 
            (int) (option.destination.z * 10f),
            (int) (option.width * 100),
            option.colorType
        )
    );

    public static final Deserializer<InkZapParticleOption> DESERIALIZER = 
        new Deserializer<InkZapParticleOption>() {
        
        @Override
        public InkZapParticleOption fromCommand(ParticleType<InkZapParticleOption> type, 
                                                 StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float x = (float) reader.readDouble();
            reader.expect(' ');
            float y = (float) reader.readDouble();
            reader.expect(' ');
            float z = (float) reader.readDouble();
            reader.expect(' ');
            float width = (float) reader.readDouble();
            reader.expect(' ');
            int colorType = reader.readInt();
            return new InkZapParticleOption(new Vec3(x, y, z), width, colorType);
        }

        @Override
        public InkZapParticleOption fromNetwork(ParticleType<InkZapParticleOption> type, 
                                                  FriendlyByteBuf buf) {
            return new InkZapParticleOption(
                readVec3FromNetwork(buf),
                buf.readFloat(),
                buf.readInt()
            );
        }
    };

    private final Vec3 destination;
    private final float width;
    private final int colorType; // 0=焦墨, 1=苍青, 2=亮白

    // 兼容旧构造器（默认苍青中层）
    public InkZapParticleOption(Vec3 destination) {
        this(destination, 0.18f, 1);
    }

    public InkZapParticleOption(Vec3 destination, float width, int colorType) {
        this.destination = destination;
        this.width = width;
        this.colorType = Math.max(0, Math.min(2, colorType));
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        writeVec3ToNetwork(this.destination, buf);
        buf.writeFloat(this.width);
        buf.writeInt(this.colorType);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.2f %.2f %.2f %.2f %d",
            BuiltInRegistries.PARTICLE_TYPE.getKey(this.getType()),
            destination.x, destination.y, destination.z, width, colorType);
    }

    @Override
    public ParticleType<InkZapParticleOption> getType() {
        return com.zzdzt.arcanemag.registry.ParticleRegistry.INK_ZAP_PARTICLE.get();
    }

    public Vec3 getDestination() {
        return this.destination;
    }

    public float getWidth() {
        return this.width;
    }

    public int getColorType() {
        return this.colorType;
    }

    private static Vec3 readVec3FromNetwork(FriendlyByteBuf buf) {
        return new Vec3(buf.readInt() / 10f, buf.readInt() / 10f, buf.readInt() / 10f);
    }

    private static void writeVec3ToNetwork(Vec3 vec, FriendlyByteBuf buf) {
        buf.writeInt((int) (vec.x * 10));
        buf.writeInt((int) (vec.y * 10));
        buf.writeInt((int) (vec.z * 10));
    }
}