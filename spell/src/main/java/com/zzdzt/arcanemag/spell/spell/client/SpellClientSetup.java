package com.zzdzt.arcanemag.spell.client;

import com.zzdzt.arcanemag.particle.InkZapParticle;
import com.zzdzt.arcanemag.spell.registry.EntityRegistry;
import com.zzdzt.arcanemag.spell.registry.ParticleRegistry;
import com.zzdzt.arcanemag.spell.ArcaneMagSpell;
import com.zzdzt.arcanemag.spell.jingtingjue.InkThunderStrikeRenderer;
import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanBlastRenderer;
import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanRenderer;
import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanSwordModel;
import com.zzdzt.arcanemag.spell.thunderstream.ThunderStreamRenderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * spell 模块客户端注册（法术实体渲染器 + 粒子）。
 *
 * 从 ClientSetup 拆分而来，避免 core 引用 spell 的渲染类。
 * modid = arcane_mag_spell，spell jar 不装时这些注册不执行。
 */
@Mod.EventBusSubscriber(modid = ArcaneMagSpell.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpellClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            EntityRegistry.LIZHI_YAN.get(),
            LizhiYanRenderer::new
        );

        // 飞剑冲击波渲染器
        event.registerEntityRenderer(
            EntityRegistry.LIZHI_YAN_BLAST_VISUAL.get(),
            LizhiYanBlastRenderer::new
        );

        event.registerEntityRenderer(
            EntityRegistry.THUNDER_STREAM.get(),
            ThunderStreamRenderer::new
        );

        event.registerEntityRenderer(
            EntityRegistry.INK_THUNDER_STRIKE.get(),
            InkThunderStrikeRenderer::new
        );
    }

    /**
     * 注册模型层定义（LayerDefinition）
     */
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
            LizhiYanBlastRenderer.MODEL_LAYER_LOCATION,
            LizhiYanBlastRenderer::createBodyLayer
        );

        // 飞剑实体模型图层
        event.registerLayerDefinition(
            LizhiYanSwordModel.LAYER_LOCATION,
            LizhiYanSwordModel::createBodyLayer
        );
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        var particleType = ParticleRegistry.INK_ZAP_PARTICLE.get();
        event.registerSpriteSet(
            particleType,
            InkZapParticle.Provider::new
        );
    }
}
