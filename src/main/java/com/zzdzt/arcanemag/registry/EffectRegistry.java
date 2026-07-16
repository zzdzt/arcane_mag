package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EffectRegistry {
    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ArcaneMag.MODID);

    // 现有效果
    public static final RegistryObject<MobEffect> FORCED_MARCH_MOBILITY =
        EFFECTS.register("forced_march_mobility", ForcedMarchMobility::new);

    public static final RegistryObject<MobEffect> GUN_ENHANCE =
        EFFECTS.register("gun_enhance", GunEnhanceEffect::new);

    public static final RegistryObject<MobEffect> TACTICAL_DASH =
        EFFECTS.register("tactical_dash", TacticalDashEffect::new);

    public static final RegistryObject<MobEffect> JING_TING_CHARGE =
        EFFECTS.register("jing_ting_charge", JingTingChargeEffect::new);

    public static final RegistryObject<MobEffect> ABSOLUTE_ZERO = 
        EFFECTS.register("absolute_zero", AbsoluteZeroEffect::new);

    public static final RegistryObject<MobEffect> ARCANE_AIM = 
        EFFECTS.register("arcane_aim", ArcaneAimEffect::new);

    public static final RegistryObject<MobEffect> BLOOD_MARKED =
        EFFECTS.register("blood_marked", BloodMarkedEffect::new);    

    public static final RegistryObject<MobEffect> VULNERABILITY =
        EFFECTS.register("vulnerability", VulnerabilityEffect::new);

    public static final RegistryObject<MobEffect> LIQUID_NITROGEN_MARKED =
        EFFECTS.register("liquid_nitrogen_marked", LiquidNitrogenMarkedEffect::new);
    

    // ========== 注魔子弹效果（9个学派）==========
    public static final RegistryObject<MobEffect> IMBUED_FIRE =
        EFFECTS.register("imbued_fire", () -> new ImbuedBulletMobEffect(0xFF4500)); // 橙红

    public static final RegistryObject<MobEffect> IMBUED_ICE =
        EFFECTS.register("imbued_ice", () -> new ImbuedBulletMobEffect(0x00BFFF)); // 深天蓝

    public static final RegistryObject<MobEffect> IMBUED_LIGHTNING =
        EFFECTS.register("imbued_lightning", () -> new ImbuedBulletMobEffect(0xFFD700)); // 金色

    public static final RegistryObject<MobEffect> IMBUED_HOLY =
        EFFECTS.register("imbued_holy", () -> new ImbuedBulletMobEffect(0xFFFACD)); // 柠檬绸

    public static final RegistryObject<MobEffect> IMBUED_ENDER =
        EFFECTS.register("imbued_ender", () -> new ImbuedBulletMobEffect(0x9932CC)); // 深兰花紫

    public static final RegistryObject<MobEffect> IMBUED_BLOOD =
        EFFECTS.register("imbued_blood", () -> new ImbuedBulletMobEffect(0x8B0000)); // 深红

    public static final RegistryObject<MobEffect> IMBUED_EVOCATION =
        EFFECTS.register("imbued_evocation", () -> new ImbuedBulletMobEffect(0x7CFC00)); // 草坪绿

    public static final RegistryObject<MobEffect> IMBUED_NATURE =
        EFFECTS.register("imbued_nature", () -> new ImbuedBulletMobEffect(0x228B22)); // 森林绿

    public static final RegistryObject<MobEffect> IMBUED_ELDRITCH =
        EFFECTS.register("imbued_eldritch", () -> new ImbuedBulletMobEffect(0x4B0082)); // 靛青


    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}