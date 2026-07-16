package com.zzdzt.arcanemag.spell.jammingwaves;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.renderer.ArcaneMagRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Jamming Waves（干扰波纹）客户端渲染系统。
 *
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID, value = Dist.CLIENT)
public final class JammingWavesDefenseEffectRenderEvent {

    private static final ResourceLocation SHIELD_OVERLAY_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "textures/spell/jamming_waves_wall.png");
    private static final ResourceLocation SHIELD_TRIM_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "textures/spell/jamming_waves_wall_trim.png");
    private static final ResourceLocation FAILED_SHIELD_OVERLAY_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "textures/spell/jamming_waves_failed_wall.png");
    private static final ResourceLocation SHOCKWAVE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "textures/spell/jamming_waves_wave.png");

    private static final RenderType SHIELD_OVERLAY_RENDER_TYPE =
        ArcaneMagRenderTypes.entityAdditiveGlowNoCull("jamming_waves_overlay", SHIELD_OVERLAY_TEXTURE);
    private static final RenderType SHIELD_TRIM_RENDER_TYPE =
        ArcaneMagRenderTypes.entityAdditiveGlowNoCull("jamming_waves_trim", SHIELD_TRIM_TEXTURE);
    private static final RenderType FAILED_SHIELD_OVERLAY_RENDER_TYPE =
        ArcaneMagRenderTypes.entityAdditiveGlowNoCull("jamming_waves_failed_overlay", FAILED_SHIELD_OVERLAY_TEXTURE);
    private static final RenderType RIPPLE_RENDER_TYPE =
        ArcaneMagRenderTypes.entityAdditiveGlowNoCull("jamming_waves_ripple", SHOCKWAVE_TEXTURE);

    // 常量
    private static final int MAX_ACTIVE_EFFECTS = 64;
    private static final float MIN_EFFECT_SCALE = 0.1f;
    private static final int HOLD_TICKS = 10;
    private static final int FADE_TICKS = 10;
    private static final int TOTAL_TICKS = HOLD_TICKS + FADE_TICKS;
    private static final float SHIELD_ALPHA = 0.95f;
    private static final float WALL_THICKNESS = 0.1f;
    private static final float OUTER_RADIUS = 0.25f;
    private static final float INNER_RADIUS = 0.205f;
    private static final float RIPPLE_LIFETIME_TICKS = 8.0f;
    private static final float RIPPLE_MIN_RADIUS = 0.10f;
    private static final float RIPPLE_MAX_RADIUS = 0.64f;
    private static final float SHIELD_TINT = 0.65f;

    private static final Vec2[] OUTER_HEX_VERTICES = buildHexVertices(OUTER_RADIUS);
    private static final Vec2[] INNER_HEX_VERTICES = buildHexVertices(INNER_RADIUS);
    private static final Deque<ActiveEffect> ACTIVE_EFFECTS = new ArrayDeque<>();

    private JammingWavesDefenseEffectRenderEvent() {}

    // 公共 API 

    public static void enqueueEffect(Vec3 position, Vec3 normal, float sizeScale, float lifetimeScale,
                                      boolean renderWave, boolean failed) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ArcaneMag.LOGGER.warn("JammingWaves enqueueEffect: level is null, dropping effect");
            return;
        }

        ACTIVE_EFFECTS.addLast(new ActiveEffect(
            position,
            normalizeOrFallback(normal, new Vec3(0, 0, 1)),
            minecraft.level.getGameTime(),
            sanitizeScale(sizeScale),
            sanitizeScale(lifetimeScale),
            renderWave,
            failed
        ));
        while (ACTIVE_EFFECTS.size() > MAX_ACTIVE_EFFECTS) {
            ACTIVE_EFFECTS.removeFirst();
        }
    }

    // 事件 

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var minecraft = Minecraft.getInstance();
            // 仅在 level 为 null 时才清空（玩家退出或切换维度）
            if (minecraft.level == null && !ACTIVE_EFFECTS.isEmpty()) {
                ACTIVE_EFFECTS.clear();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 改回 AFTER_PARTICLES，和 ForceField 一致
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            if (!ACTIVE_EFFECTS.isEmpty()) {
                ACTIVE_EFFECTS.clear();
            }
            return;
        }

        if (ACTIVE_EFFECTS.isEmpty()) {
            return;
        }

        var gameTime = level.getGameTime();
        var partialTick = event.getPartialTick();
        var poseStack = event.getPoseStack();
        var cameraPosition = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();

        // 防御性检查
        if (buffers == null) {
            ArcaneMag.LOGGER.warn("JammingWaves: buffers is null, skipping render");
            return;
        }

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        int rendered = 0;
        int removed = 0;
        var iterator = ACTIVE_EFFECTS.iterator();
        while (iterator.hasNext()) {
            var effect = iterator.next();
            var age = (float)(gameTime - effect.startGameTime) + partialTick;
            var normalizedAge = age / effect.lifetimeScale;
            
            // 简化移除逻辑：达到总生命周期即移除，与 ForceField 一致
            if (normalizedAge >= TOTAL_TICKS) { 
                iterator.remove();
                removed++;
                continue; 
            }
            
            renderEffect(poseStack, buffers, effect, normalizedAge);
            rendered++;
        }

        poseStack.popPose();

        // 始终提交批处理，即使没有渲染任何特效也要确保管道清洁
        buffers.endBatch(SHIELD_OVERLAY_RENDER_TYPE);
        buffers.endBatch(SHIELD_TRIM_RENDER_TYPE);
        buffers.endBatch(FAILED_SHIELD_OVERLAY_RENDER_TYPE);
        buffers.endBatch(RIPPLE_RENDER_TYPE);

        if ((rendered > 0 || removed > 0) && gameTime % 40 == 0) {
        }
    }

    // 渲染核心 

    private static void renderEffect(PoseStack poseStack,
                                      net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                      ActiveEffect effect, float normalizedAge) {
        var alpha = getWallAlpha(normalizedAge);
        if (alpha <= 0.0f) return;

        var center = effect.position;
        var normal = normalizeOrFallback(effect.normal, new Vec3(0, 0, 1));
        var tangent = buildTangent(normal);
        var bitangent = normalizeOrFallback(normal.cross(tangent), new Vec3(0, 1, 0));
        var effectSize = effect.sizeScale;
        var halfThickness = WALL_THICKNESS * 0.5f * effectSize;

        var shieldOverlayRenderType = effect.failed
            ? FAILED_SHIELD_OVERLAY_RENDER_TYPE : SHIELD_OVERLAY_RENDER_TYPE;

        var overlayBuffer = buffers.getBuffer(shieldOverlayRenderType);
        drawFilledHex(poseStack, overlayBuffer, center, tangent, bitangent, normal,
            halfThickness, effectSize, alpha * 0.75f);
        drawOuterSide(poseStack, overlayBuffer, center, tangent, bitangent, normal,
            halfThickness, effectSize, alpha * 0.45f);

        var trimBuffer = buffers.getBuffer(SHIELD_TRIM_RENDER_TYPE);
        drawTrimRing(poseStack, trimBuffer, center, normal, tangent, bitangent,
            halfThickness, effectSize, alpha);

        if (!effect.failed && effect.renderWave) {
            var rippleBuffer = buffers.getBuffer(RIPPLE_RENDER_TYPE);
            drawRipple(poseStack, rippleBuffer, center, normal, tangent, bitangent,
                halfThickness, effectSize, normalizedAge, alpha);
        }
    }

    // 六边形填充 

    private static void drawFilledHex(PoseStack poseStack, VertexConsumer buffer, Vec3 center,
                                       Vec3 tangent, Vec3 bitangent, Vec3 normal,
                                       float halfThickness, float sizeScale, float alpha) {
        var frontCenter = toWorld(center, tangent, bitangent, normal, 0f, 0f, halfThickness);
        var backCenter = toWorld(center, tangent, bitangent, normal, 0f, 0f, -halfThickness);

        for (int i = 0; i < OUTER_HEX_VERTICES.length; i++) {
            var next = (i + 1) % OUTER_HEX_VERTICES.length;
            var co = OUTER_HEX_VERTICES[i];
            var no = OUTER_HEX_VERTICES[next];
            var cx = co.x * sizeScale; var cy = co.y * sizeScale;
            var nx = no.x * sizeScale; var ny = no.y * sizeScale;

            // 前面
            var fa = toWorld(center, tangent, bitangent, normal, cx, cy, halfThickness);
            var fb = toWorld(center, tangent, bitangent, normal, nx, ny, halfThickness);
            addTriangleAsQuad(poseStack, buffer, frontCenter, fa, fb,
                0.5f, 0.5f, uvH(co.x), uvH(co.y), uvH(no.x), uvH(no.y),
                SHIELD_TINT, SHIELD_TINT, SHIELD_TINT, alpha, normal);

            // 后面
            var ba = toWorld(center, tangent, bitangent, normal, cx, cy, -halfThickness);
            var bb = toWorld(center, tangent, bitangent, normal, nx, ny, -halfThickness);
            addTriangleAsQuad(poseStack, buffer, backCenter, bb, ba,
                0.5f, 0.5f, uvH(no.x), uvH(no.y), uvH(co.x), uvH(co.y),
                SHIELD_TINT, SHIELD_TINT, SHIELD_TINT, alpha, normal.reverse());
        }
    }

    // 六边形侧面（厚度可见） 

    private static void drawOuterSide(PoseStack poseStack, VertexConsumer buffer, Vec3 center,
                                       Vec3 tangent, Vec3 bitangent, Vec3 normal,
                                       float halfThickness, float sizeScale, float alpha) {
        for (int i = 0; i < OUTER_HEX_VERTICES.length; i++) {
            var next = (i + 1) % OUTER_HEX_VERTICES.length;
            var co = OUTER_HEX_VERTICES[i];
            var no = OUTER_HEX_VERTICES[next];
            var cx = co.x * sizeScale; var cy = co.y * sizeScale;
            var nx = no.x * sizeScale; var ny = no.y * sizeScale;

            var fa = toWorld(center, tangent, bitangent, normal, cx, cy, halfThickness);
            var fb = toWorld(center, tangent, bitangent, normal, nx, ny, halfThickness);
            var bb = toWorld(center, tangent, bitangent, normal, nx, ny, -halfThickness);
            var ba = toWorld(center, tangent, bitangent, normal, cx, cy, -halfThickness);

            var sideNormal = normalizeOrFallback(
                tangent.scale((cx + nx) * 0.5f).add(bitangent.scale((cy + ny) * 0.5f)), tangent);

            addQuad(poseStack, buffer, fa, fb, bb, ba,
                uvH(co.x), uvH(co.y), uvH(no.x), uvH(no.y),
                uvH(no.x), uvH(no.y) + 0.12f, uvH(co.x), uvH(co.y) + 0.12f,
                SHIELD_TINT, SHIELD_TINT, SHIELD_TINT, alpha, sideNormal);
        }
    }

    // 边框环 

    private static void drawTrimRing(PoseStack poseStack, VertexConsumer buffer, Vec3 center,
                                      Vec3 normal, Vec3 tangent, Vec3 bitangent,
                                      float halfThickness, float sizeScale, float alpha) {
        for (int i = 0; i < OUTER_HEX_VERTICES.length; i++) {
            var next = (i + 1) % OUTER_HEX_VERTICES.length;
            var oc = OUTER_HEX_VERTICES[i]; var on = OUTER_HEX_VERTICES[next];
            var ic = INNER_HEX_VERTICES[i]; var in_ = INNER_HEX_VERTICES[next];

            var ocx = oc.x * sizeScale; var ocy = oc.y * sizeScale;
            var onx = on.x * sizeScale; var ony = on.y * sizeScale;
            var icx = ic.x * sizeScale; var icy = ic.y * sizeScale;
            var inx = in_.x * sizeScale; var iny = in_.y * sizeScale;

            // 前面
            var foa = toWorld(center, tangent, bitangent, normal, ocx, ocy, halfThickness);
            var fob = toWorld(center, tangent, bitangent, normal, onx, ony, halfThickness);
            var fib = toWorld(center, tangent, bitangent, normal, inx, iny, halfThickness);
            var fia = toWorld(center, tangent, bitangent, normal, icx, icy, halfThickness);
            addQuad(poseStack, buffer, foa, fob, fib, fia,
                uvH(oc.x), uvH(oc.y), uvH(on.x), uvH(on.y),
                uvH(in_.x), uvH(in_.y), uvH(ic.x), uvH(ic.y),
                SHIELD_TINT, SHIELD_TINT, SHIELD_TINT, alpha, normal);

            // 后面
            var boa = toWorld(center, tangent, bitangent, normal, ocx, ocy, -halfThickness);
            var bob = toWorld(center, tangent, bitangent, normal, onx, ony, -halfThickness);
            var bib = toWorld(center, tangent, bitangent, normal, inx, iny, -halfThickness);
            var bia = toWorld(center, tangent, bitangent, normal, icx, icy, -halfThickness);
            addQuad(poseStack, buffer, boa, bia, bib, bob,
                uvH(oc.x), uvH(oc.y), uvH(ic.x), uvH(ic.y),
                uvH(in_.x), uvH(in_.y), uvH(on.x), uvH(on.y),
                SHIELD_TINT, SHIELD_TINT, SHIELD_TINT, alpha, normal.reverse());
        }
    }

    // 涟漪 

    private static void drawRipple(PoseStack poseStack, VertexConsumer buffer, Vec3 center,
                                    Vec3 normal, Vec3 tangent, Vec3 bitangent,
                                    float halfThickness, float sizeScale, float normalizedAge, float wallAlpha) {
        if (normalizedAge >= RIPPLE_LIFETIME_TICKS) return;
        var progress = Mth.clamp(normalizedAge / RIPPLE_LIFETIME_TICKS, 0f, 1f);
        var rippleAlpha = wallAlpha * (1.0f - progress);
        if (rippleAlpha <= 0.0f) return;

        var eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        var radius = Mth.lerp(eased, RIPPLE_MIN_RADIUS * sizeScale, RIPPLE_MAX_RADIUS * sizeScale);
        var offset = halfThickness + 0.0015f;

        drawRippleOnSurface(poseStack, buffer, center.add(normal.scale(offset)),
            normal, tangent, bitangent, radius, rippleAlpha);
        drawRippleOnSurface(poseStack, buffer, center.subtract(normal.scale(offset)),
            normal.reverse(), tangent, bitangent, radius, rippleAlpha);
    }

    private static void drawRippleOnSurface(PoseStack poseStack, VertexConsumer buffer,
                                             Vec3 center, Vec3 normal, Vec3 tangent, Vec3 bitangent,
                                             float radius, float alpha) {
        var p0 = toWorld(center, tangent, bitangent, normal, -radius, -radius, 0f);
        var p1 = toWorld(center, tangent, bitangent, normal, -radius, radius, 0f);
        var p2 = toWorld(center, tangent, bitangent, normal, radius, radius, 0f);
        var p3 = toWorld(center, tangent, bitangent, normal, radius, -radius, 0f);
        addDoubleSidedQuad(poseStack, buffer, p0, p1, p2, p3,
            0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 0.95f, 0.95f, 0.95f, alpha, normal);
    }

    // 顶点辅助 

    private static void addTriangleAsQuad(PoseStack ps, VertexConsumer buf,
                                           Vec3 p0, Vec3 p1, Vec3 p2,
                                           float u0, float v0, float u1, float v1, float u2, float v2,
                                           float r, float g, float b, float a, Vec3 n) {
        addQuad(ps, buf, p0, p1, p2, p2, u0, v0, u1, v1, u2, v2, u2, v2, r, g, b, a, n);
    }

    private static void addDoubleSidedQuad(PoseStack ps, VertexConsumer buf,
                                            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                            float u0, float v0, float u1, float v1,
                                            float u2, float v2, float u3, float v3,
                                            float r, float g, float b, float a, Vec3 n) {
        addQuad(ps, buf, p0, p1, p2, p3, u0, v0, u1, v1, u2, v2, u3, v3, r, g, b, a, n);
        addQuad(ps, buf, p3, p2, p1, p0, u3, v3, u2, v2, u1, v1, u0, v0, r, g, b, a, n.reverse());
    }

    private static void addQuad(PoseStack ps, VertexConsumer buf,
                                 Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                 float u0, float v0, float u1, float v1,
                                 float u2, float v2, float u3, float v3,
                                 float r, float g, float b, float a, Vec3 n) {
        var pose = ps.last();
        vertex(buf, pose.pose(), pose.normal(), p0, u0, v0, r, g, b, a, n);
        vertex(buf, pose.pose(), pose.normal(), p1, u1, v1, r, g, b, a, n);
        vertex(buf, pose.pose(), pose.normal(), p2, u2, v2, r, g, b, a, n);
        vertex(buf, pose.pose(), pose.normal(), p3, u3, v3, r, g, b, a, n);
    }

    private static void vertex(VertexConsumer buf, Matrix4f pose, Matrix3f normalMat,
                                Vec3 pos, float u, float v, float r, float g, float b, float a, Vec3 n) {
        // 加算合成中 alpha 对颜色影响较弱，将 alpha 预乘入 RGB 以确保淡出效果
        var sr = r * a;
        var sg = g * a;
        var sb = b * a;
        buf.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
            .color(sr, sg, sb, a)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normalMat, (float) n.x, (float) n.y, (float) n.z)
            .endVertex();
    }

    // 工具 

    private static Vec3 toWorld(Vec3 center, Vec3 t, Vec3 b, Vec3 n, float x, float y, float z) {
        return center.add(t.scale(x)).add(b.scale(y)).add(n.scale(z));
    }

    private static Vec3 buildTangent(Vec3 normal) {
        var ref = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        var tangent = ref.cross(normal);
        if (tangent.lengthSqr() <= 1.0e-4) tangent = new Vec3(0, 0, 1).cross(normal);
        return normalizeOrFallback(tangent, new Vec3(1, 0, 0));
    }

    private static float getWallAlpha(float normalizedAge) {
        if (normalizedAge < HOLD_TICKS) return SHIELD_ALPHA;
        var progress = Mth.clamp((normalizedAge - HOLD_TICKS) / FADE_TICKS, 0f, 1f);
        return SHIELD_ALPHA * (1.0f - easeOutCubic(progress));
    }

    private static float easeOutCubic(float t) {
        var c = Mth.clamp(t, 0f, 1f);
        var inv = 1.0f - c;
        return 1.0f - inv * inv * inv;
    }

    private static float uvH(float value) { return 0.5f + (value / (OUTER_RADIUS * 2f)); }

    private static Vec3 normalizeOrFallback(Vec3 v, Vec3 fallback) {
        return v.lengthSqr() > 1.0e-4 ? v.normalize() : fallback.normalize();
    }

    private static float sanitizeScale(float value) {
        return Float.isFinite(value) ? Math.max(MIN_EFFECT_SCALE, value) : 1.0f;
    }

    private static Vec2[] buildHexVertices(float radius) {
        var verts = new Vec2[6];
        for (int i = 0; i < verts.length; i++) {
            var angle = Math.toRadians(i * 60.0 - 90.0);
            verts[i] = new Vec2((float)(Math.cos(angle) * radius), (float)(Math.sin(angle) * radius));
        }
        return verts;
    }

    // 数据类 

    private static final class ActiveEffect {
        final Vec3 position;
        final Vec3 normal;
        final long startGameTime;
        final float sizeScale;
        final float lifetimeScale;
        final boolean renderWave;
        final boolean failed;

        ActiveEffect(Vec3 position, Vec3 normal, long startGameTime, float sizeScale,
                     float lifetimeScale, boolean renderWave, boolean failed) {
            this.position = position;
            this.normal = normal;
            this.startGameTime = startGameTime;
            this.sizeScale = sizeScale;
            this.lifetimeScale = lifetimeScale;
            this.renderWave = renderWave;
            this.failed = failed;
        }
    }
}