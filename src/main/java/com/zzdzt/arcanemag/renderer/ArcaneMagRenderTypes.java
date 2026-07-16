package com.zzdzt.arcanemag.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * ArcaneMag 自定义 RenderType 工厂。
 *
 * 核心渲染类型：entityAdditiveGlowNoCull
 * - 加算合成（GL_ONE, GL_ONE），适合光效/法术护盾等发光特效
 * - 无背面剔除，薄片从任意角度可见
 * - 使用 FULL_BRIGHT lightmap 即可实现自发光
 */
public final class ArcaneMagRenderTypes extends RenderStateShard {

    private ArcaneMagRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /**
     * 加算合成 + 无背面剔除 + 自发光渲染类型。
     * 调用方需在 vertex 中传入 LightTexture.FULL_BRIGHT 以实现自发光。
     */
    public static RenderType entityAdditiveGlowNoCull(String name, ResourceLocation texture) {
        return RenderType.create(
            name,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,  // affectsOutline
            true,  // isTranslucent
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)
        );
    }

    /**
     * 加算合成 + 无背面剔除 + 仅写颜色（不写深度）的渲染类型。
     *
     * 与 entityAdditiveGlowNoCull 的区别：
     * - 关闭深度写入（COLOR_WRITE 仅写颜色通道）
     * - 保留深度测试（LEQUAL），确保墙壁遮挡仍生效
     * - 同一特效内的多个加算层不会因深度写入而互相覆盖
     */
    public static RenderType entityAdditiveGlowNoCullColorOnly(String name, ResourceLocation texture) {
        return RenderType.create(
            name,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)
        );
    }

    /**
     * 加算合成 + 无背面剔除 + 无深度测试 + 仅写颜色。
     *
     * 完全忽略深度缓冲：始终可见，不写入深度。
     * 适合必须始终覆盖在所有几何体之上的光效（如护盾格挡特效）。
     */
    public static RenderType entityAdditiveGlowNoDepth(String name, ResourceLocation texture) {
        return RenderType.create(
            name,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true)
        );
    }
}
