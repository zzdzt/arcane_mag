package com.zzdzt.arcanemag.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 水墨闪电粒子
 */
public class InkZapParticle extends TextureSheetParticle {

    Vec3 destination;
    float tubeWidth;
    int colorType;

    
    // 颜色定义
    private static final float[] INK_JIAO = {0.02f, 0.02f, 0.02f, 0.92f};
    private static final float[] CANG_QING = {0.35f, 0.72f, 0.68f, 0.75f};
    private static final float[] LIANG_BAI = {0.85f, 0.96f, 0.94f, 0.95f};

    private static final float[][] COLORS = {
        INK_JIAO,
        CANG_QING,
        LIANG_BAI
    };

    InkZapParticle(ClientLevel level, double x, double y, double z,
                   double xd, double yd, double zd, InkZapParticleOption options) {
        super(level, x, y, z, 0, 0, 0);
        this.setSize(1, 1);
        this.quadSize = 1f;
        this.destination = options.getDestination();
        this.tubeWidth = options.getWidth();
        this.colorType = options.getColorType();
        this.lifetime = Utils.random.nextIntBetweenInclusive(3, 8);
        this.hasPhysics = false;
        
        // 根据 colorType 设置颜色
        float[] c = COLORS[colorType];
        this.rCol = c[0];
        this.gCol = c[1];
        this.bCol = c[2];
        this.alpha = c[3];
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public Vec3 randomOffset(RandomSource random, float scale) {
        return new Vec3(
            (2f * random.nextFloat() - 1f) * scale,
            (2f * random.nextFloat() - 1f) * scale,
            (2f * random.nextFloat() - 1f) * scale
        );
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTick) {
        Vec3 vec3 = camera.getPosition();
        float f = (float) (Mth.lerp((double) partialTick, this.xo, this.x) - vec3.x());
        float f1 = (float) (Mth.lerp((double) partialTick, this.yo, this.y) - vec3.y());
        float f2 = (float) (Mth.lerp((double) partialTick, this.zo, this.z) - vec3.z());
        PoseStack poseStack = new PoseStack();
        poseStack.translate(f, f1, f2);
        float quadScale = this.getQuadSize(partialTick);
        poseStack.scale(quadScale, quadScale, quadScale);

        // 只渲染一层，颜色和粗细由构造参数决定
        renderLightningPass(consumer, poseStack, partialTick, tubeWidth, 0.25f);
    }

    private void renderLightningPass(VertexConsumer consumer, PoseStack poseStack,
                                      float partialTick, float width, float chanceToBranch) {
        RandomSource randomSource = RandomSource.create((age + lifetime) * 3456798L);
        Vec3 start = Vec3.ZERO;
        Vec3 end = destination.subtract(this.getPos());
        double distance = end.length();
        
        if (distance < 0.1) return;
        
        // 更多 segments，更曲折
        int segments = (int) (distance / 2.5 + randomSource.nextIntBetweenInclusive(2, 4));
        double distancePerSegment = distance / segments;
        Vec3 direction = end.normalize();

        for (int i = 0; i < segments; i++) {
            // 水平摆动更大，覆盖范围感
            float wiggleScale = 0.4f;
            Vec3 wiggle = randomOffset(randomSource, wiggleScale);
            
            // 水平方向的闪电（end.y 较小）增加更多水平偏移
            if (Math.abs(end.y) < 5.0) {
                wiggle = wiggle.add(
                    (2f * randomSource.nextFloat() - 1f) * 1.5,
                    0,
                    (2f * randomSource.nextFloat() - 1f) * 1.5
                );
            }
            
            Vec3 segmentEnd = start.add(direction.scale(distancePerSegment)).add(wiggle);
            drawLightningBeam(consumer, poseStack, partialTick, start, segmentEnd, width, chanceToBranch, randomSource);
            start = segmentEnd;
        }
    }

    private void drawLightningBeam(VertexConsumer consumer, PoseStack poseStack, float partialTick,
                                    Vec3 start, Vec3 end, float width, float chanceToBranch,
                                    RandomSource randomSource) {
        drawTube(consumer, poseStack, partialTick, start, end, width);

        // 分叉
        if (randomSource.nextFloat() < chanceToBranch) {
            Vec3 branch = randomOffset(randomSource, 1.2f).add(end);
            drawLightningBeam(consumer, poseStack, partialTick, end, branch, width * 0.7f, chanceToBranch * 0.5f, randomSource);
        }
    }

    private void drawTube(VertexConsumer consumer, PoseStack poseStack, float partialTick,
                          Vec3 start, Vec3 end, float width) {
        Vec3 delta = end.subtract(start);
        float length = (float) delta.length();
        if (length <= 1e-6f) return;

        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        Vec2 rotation = Utils.rotationFromDirection(delta.normalize());
        poseStack.mulPose(Axis.YP.rotation(rotation.y));
        poseStack.mulPose(Axis.XP.rotation(-rotation.x));
        drawHull(Vec3.ZERO, new Vec3(0, 0, length), width, width, poseStack, consumer, partialTick);
        poseStack.popPose();
    }

    private void drawHull(Vec3 from, Vec3 to, float width, float height,
                          PoseStack poseStack, VertexConsumer consumer, float partialTick) {
        poseStack.pushPose();
        for (int i = 0; i < 4; i++) {
            drawQuad(from.subtract(0, height * .5f, 0), to.subtract(0, height * .5f, 0),
                width, 0, poseStack.last(), consumer, partialTick);
            poseStack.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
        }
        poseStack.popPose();
    }

    private void drawQuad(Vec3 from, Vec3 to, float width, float height,
                          PoseStack.Pose pose, VertexConsumer consumer, float partialTick) {
        Matrix4f poseMatrix = pose.pose();
        float halfWidth = width * .5f;
        int light = getLightColor(partialTick);
        int r = (int) (this.rCol * 255);
        int g = (int) (this.gCol * 255);
        int b = (int) (this.bCol * 255);
        int a = (int) (this.alpha * 255);
        
        consumer.vertex(poseMatrix, (float) from.x - halfWidth, (float) from.y, (float) from.z).uv(getU1(), getV1()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) from.x + halfWidth, (float) from.y, (float) from.z).uv(getU1(), getV0()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) to.x + halfWidth, (float) to.y, (float) to.z).uv(getU0(), getV0()).color(r, g, b, a).uv2(light).endVertex();
        consumer.vertex(poseMatrix, (float) to.x - halfWidth, (float) to.y, (float) to.z).uv(getU0(), getV1()).color(r, g, b, a).uv2(light).endVertex();
    }

    @NotNull
    @Override
    public ParticleRenderType getRenderType() {
        return INK_PARTICLE_BLEND;
    }

    public static ParticleRenderType INK_PARTICLE_BLEND = new ParticleRenderType() {
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        public void end(Tesselator tesselator) {
            tesselator.end();
        }
        public String toString() {
            return "INK_PARTICLE_BLEND";
        }
    };

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<InkZapParticleOption> {
        private final SpriteSet sprite;
        public Provider(SpriteSet pSprite) {
            this.sprite = pSprite;
        }
        public Particle createParticle(@NotNull InkZapParticleOption options, @NotNull ClientLevel pLevel,
                                      double pX, double pY, double pZ, double pXSpeed, double pYSpeed, double pZSpeed) {
            var particle = new InkZapParticle(pLevel, pX, pY, pZ, pXSpeed, pYSpeed, pZSpeed, options);
            particle.pickSprite(this.sprite);
            particle.setAlpha(1.0F);
            return particle;
        }
    }
}