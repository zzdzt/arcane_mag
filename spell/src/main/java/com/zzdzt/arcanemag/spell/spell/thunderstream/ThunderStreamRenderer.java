package com.zzdzt.arcanemag.spell.thunderstream;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ThunderStreamRenderer extends EntityRenderer<ThunderStreamEntity> {
 
    private static final float[] INK_HEAVY  = {0.15f, 0.16f, 0.18f, 0.95f};

    private static final float[] PALE_CYAN  = {0.69f, 0.86f, 0.83f, 0.70f};

    private static final float[] INK_JIAO   = {0.06f, 0.07f, 0.09f, 0.92f};

    private static final float[] CORE_WHITE = {1.00f, 1.00f, 1.00f, 0.95f};

    private static final float[] INK_LIGHT  = {0.25f, 0.28f, 0.32f, 0.40f};

    public ThunderStreamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(@NotNull ThunderStreamEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        var dir = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()).normalize();
        var length = entity.getLength();

        // 种子
        long seed = entity.tickCount * 7919L + entity.getId() * 104729L;
        RandomSource random = RandomSource.create(seed);

        // 对齐到局部Y轴
        var from = new Vector3f(0, 1, 0);
        var to = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        var q = new Quaternionf().rotationTo(from, to);

        poseStack.pushPose();
        poseStack.mulPose(q);

        //直接 BufferBuilder 渲染
        Tesselator tesselator = Tesselator.getInstance();
        var builder = tesselator.getBuilder();

        // 标准半透明混合（深色可见）
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderLightningRibbon(builder, poseStack, length, 0.065f, 0.15f, random, 5, INK_JIAO);
        tesselator.end();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderLightningRibbon(builder, poseStack, length, 0.06f, 0.14f, random, 4, INK_HEAVY);
        tesselator.end();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderLightningRibbon(builder, poseStack, length, 0.05f, 0.10f, random, 3, INK_LIGHT);
        tesselator.end();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderLightningRibbon(builder, poseStack, length, 0.035f, 0.08f, random, 2, PALE_CYAN);
        tesselator.end();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderLightningRibbon(builder, poseStack, length, 0.015f, 0.06f, random, 1, CORE_WHITE);
        tesselator.end();

        // 恢复
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * 渲染带状闪电（主路径 + 少量短分支）
     */
    private void renderLightningRibbon(com.mojang.blaze3d.vertex.BufferBuilder builder, 
                                       PoseStack poseStack, float length, 
                                       float ribbonWidth, float wiggleScale,
                                       RandomSource random, int passIndex, float[] color) {
        
        RandomSource passRandom = RandomSource.create(random.nextLong() + passIndex * 12345L);
        var pose = poseStack.last().pose();

        // 主路径
        var mainPoints = generateLightningPath(
            Vec3.ZERO, new Vec3(0, length, 0),
            wiggleScale, passRandom, length * 0.6f  // 限制半径
        );
        
        for (int i = 0; i < mainPoints.size() - 1; i++) {
            drawRibbonSegment(builder, pose, mainPoints.get(i), mainPoints.get(i + 1), 
                ribbonWidth, color);
        }

        // 少量短分支（从主路径侧面发出，不远离光束） 
        int branchCount = passRandom.nextIntBetweenInclusive(1, 3);
        for (int b = 0; b < branchCount; b++) {
            int startIdx = 1 + passRandom.nextInt(mainPoints.size() - 2);
            Vec3 branchStart = mainPoints.get(startIdx);
            
            // 分支很短，方向偏离不大
            Vec3 branchDir = new Vec3(
                (passRandom.nextFloat() - 0.5f) * 0.6f,
                0.8f + passRandom.nextFloat() * 0.2f,  // 主要沿光束方向
                (passRandom.nextFloat() - 0.5f) * 0.6f
            ).normalize();
            
            float branchLen = length * (0.08f + passRandom.nextFloat() * 0.12f);
            Vec3 branchEnd = branchStart.add(branchDir.scale(branchLen));
            
            var branchPoints = generateLightningPath(branchStart, branchEnd,
                wiggleScale * 1.2f, RandomSource.create(passRandom.nextLong()),
                ribbonWidth * 2);
            
            for (int i = 0; i < branchPoints.size() - 1; i++) {
                drawRibbonSegment(builder, pose, branchPoints.get(i), branchPoints.get(i + 1),
                    ribbonWidth * 0.6f, color);
            }
        }
    }

    /**
     * 生成抖动路径点
     */
    private List<Vec3> generateLightningPath(Vec3 start, Vec3 end, float wiggleScale,
                                               RandomSource random, float maxRadius) {
        var points = new ArrayList<Vec3>();
        double distance = end.distanceTo(start);
        // 更多段数 = 更细致的抖动
        int segments = Math.max(8, (int)(distance / 0.8));
        
        Vec3 direction = end.subtract(start).normalize();
        points.add(start);

        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 base = start.lerp(end, t);
            
            // 抖动：中间大，两端小
            float envelope = (float) Math.sin(t * Math.PI);
            float wiggleIntensity = wiggleScale * envelope;
            
            // 垂直于光束方向的抖动
            Vec3 wiggle = randomPerpOffset(random, direction, wiggleIntensity);
            
            Vec3 point = base.add(wiggle);
            point = clampToCylinder(point, start, end, maxRadius);
            points.add(point);
        }
        
        return points;
    }

    /**
     * 生成垂直于方向的随机偏移
     */
    private Vec3 randomPerpOffset(RandomSource random, Vec3 direction, float scale) {
        // 找一个垂直于 direction 的方向
        Vec3 arbitrary = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = direction.cross(arbitrary).normalize();
        Vec3 perp2 = direction.cross(perp1).normalize();
        
        float r1 = (random.nextFloat() - 0.5f) * 2f * scale;
        float r2 = (random.nextFloat() - 0.5f) * 2f * scale;
        
        return perp1.scale(r1).add(perp2.scale(r2));
    }

    /**
     * 限制在圆柱范围内
     */
    private Vec3 clampToCylinder(Vec3 point, Vec3 cylStart, Vec3 cylEnd, float maxRadius) {
        Vec3 axis = cylEnd.subtract(cylStart).normalize();
        Vec3 toPoint = point.subtract(cylStart);
        double proj = toPoint.dot(axis);
        Vec3 projPoint = cylStart.add(axis.scale(Mth.clamp(proj, 0, cylEnd.distanceTo(cylStart))));
        Vec3 perp = point.subtract(projPoint);
        double dist = perp.length();
        
        if (dist > maxRadius && dist > 0.001) {
            return projPoint.add(perp.normalize().scale(maxRadius));
        }
        return point;
    }

    /**
     * 绘制带状段（面向相机的扁 quad）
     */
    private void drawRibbonSegment(com.mojang.blaze3d.vertex.BufferBuilder builder, Matrix4f pose,
                                   Vec3 from, Vec3 to, float width, float[] color) {
        
        Vec3 dir = to.subtract(from);
        float len = (float) dir.length();
        if (len < 1e-6f) return;
        dir = dir.normalize();

        // 计算垂直于线段且面向相机的方向
        // 使用模型视图矩阵的逆来得到相机方向
        Vec3 cameraRelative = getCameraRelativePerp(dir, width * 0.5f);

        int r = (int)(color[0] * 255);
        int g = (int)(color[1] * 255);
        int b = (int)(color[2] * 255);
        int a = (int)(color[3] * 255);

        Vec3 p1 = from.add(cameraRelative);
        Vec3 p2 = from.subtract(cameraRelative);
        Vec3 p3 = to.subtract(cameraRelative);
        Vec3 p4 = to.add(cameraRelative);

        // Quad: p1-p2-p3-p4
        builder.vertex(pose, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float)p3.x, (float)p3.y, (float)p3.z).color(r, g, b, a).endVertex();
        builder.vertex(pose, (float)p4.x, (float)p4.y, (float)p4.z).color(r, g, b, a).endVertex();
    }

    /**
     * 获取相对于相机方向的垂直偏移
     * 简化版：使用固定上方向计算垂直方向
     */
    private Vec3 getCameraRelativePerp(Vec3 dir, float halfWidth) {
        // 使用 world up 计算一个垂直于 dir 的方向
        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(dir.dot(up)) > 0.95) {
            up = new Vec3(1, 0, 0);
        }
        Vec3 right = dir.cross(up).normalize().scale(halfWidth);
        return right;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ThunderStreamEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
    }
}