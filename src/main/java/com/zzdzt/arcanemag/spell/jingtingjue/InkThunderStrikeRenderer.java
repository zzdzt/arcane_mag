package com.zzdzt.arcanemag.spell.jingtingjue;

import com.zzdzt.arcanemag.ArcaneMag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 水墨雷击渲染器 —— 空实现
 * 
 * 实际渲染由 InkZapParticle 在客户端完成，此渲染器仅作为实体占位
 */
public class InkThunderStrikeRenderer extends EntityRenderer<InkThunderStrikeEntity> {

    public InkThunderStrikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull InkThunderStrikeEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "textures/entity/empty");
    }
}