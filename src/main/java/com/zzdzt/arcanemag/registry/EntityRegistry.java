package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.jingtingjue.InkThunderStrikeEntity;
import com.zzdzt.arcanemag.spell.thunderstream.ThunderStreamEntity;
import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanBlastVisualEntity;
import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = 
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ArcaneMag.MODID);

    // ========== 飞剑实体 ==========
    public static final RegistryObject<EntityType<LizhiYanEntity>> LIZHI_YAN = 
        ENTITY_TYPES.register("lizhi_yan", () -> 
            EntityType.Builder.<LizhiYanEntity>of(LizhiYanEntity::new, MobCategory.MISC)
                .sized(0.4f, 0.4f)
                .clientTrackingRange(64)
                .updateInterval(2)
                .build("lizhi_yan")
        );
    public static final RegistryObject<EntityType<LizhiYanBlastVisualEntity>> LIZHI_YAN_BLAST_VISUAL =
        ENTITY_TYPES.register("lizhi_yan_blast_visual", () ->
            EntityType.Builder.<LizhiYanBlastVisualEntity>of(LizhiYanBlastVisualEntity::new, MobCategory.MISC)
                .sized(0.1f, 0.1f)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build("lizhi_yan_blast_visual")
        );
        
    // ========== 雷霆激流实体 ==========
    public static final RegistryObject<EntityType<ThunderStreamEntity>> THUNDER_STREAM = 
        ENTITY_TYPES.register("thunder_stream", () -> 
            EntityType.Builder.<ThunderStreamEntity>of(ThunderStreamEntity::new, MobCategory.MISC)
                .sized(0.3f, 0.3f)
                .clientTrackingRange(64)
                .updateInterval(2)
                .build("thunder_stream")
        );

    public static final RegistryObject<EntityType<InkThunderStrikeEntity>> INK_THUNDER_STRIKE = 
    ENTITY_TYPES.register("ink_thunder_strike", () -> 
        EntityType.Builder.<InkThunderStrikeEntity>of(InkThunderStrikeEntity::new, MobCategory.MISC)
            .sized(0.1f, 30.0f)          // 细长碰撞箱
            .clientTrackingRange(128)     // 较远渲染距离
            .updateInterval(1)            // 每tick同步
            .build("ink_thunder_strike")
    );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}