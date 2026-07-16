package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.arcaneaim.ArcaneAimSpell;
import com.zzdzt.arcanemag.spell.forcedmarch.ForcedMarchSpell;
import com.zzdzt.arcanemag.spell.gunenhance.GunEnhanceSpell;
import com.zzdzt.arcanemag.spell.imbuedbullet.*;
import com.zzdzt.arcanemag.spell.jammingwaves.JammingWavesSpell;
import com.zzdzt.arcanemag.spell.jingtingjue.JingTingJueSpell;
import com.zzdzt.arcanemag.spell.liquidnitrogencannon.LiquidNitrogenCannonSpell;
import com.zzdzt.arcanemag.spell.smoulderingfire.SmoulderingFireSpell;
import com.zzdzt.arcanemag.spell.tacticaldash.TacticalDashSpell;

import com.zzdzt.arcanemag.spell.lizhiyan.LizhiYanSpell;
import com.zzdzt.arcanemag.spell.thunderstream.ThunderStream;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class SpellRegistry {
    public static final DeferredRegister<AbstractSpell> SPELLS = 
        DeferredRegister.create(
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.SPELL_REGISTRY_KEY,
            ArcaneMag.MODID
        );

    // ========== 急行军法术 ==========
    public static final RegistryObject<AbstractSpell> FORCED_MARCH = 
        SPELLS.register("forced_march", ForcedMarchSpell::new);

    // ========== 雷霆激流法术 ==========
    public static final RegistryObject<AbstractSpell> THUNDER_STREAM = 
    SPELLS.register("thunder_stream", ThunderStream::new);

    // ========== 法术 ==========
    public static final RegistryObject<AbstractSpell> JAMMING_WAVES = 
    SPELLS.register("jamming_waves", JammingWavesSpell::new);

    //========== 法术 ==========
    public static final RegistryObject<AbstractSpell> GUN_ENHANCE = 
        SPELLS.register("gun_enhance", GunEnhanceSpell::new);

    // ========== 战术冲刺法术 ==========
    public static final RegistryObject<AbstractSpell> TACTICAL_DASH = 
        SPELLS.register("tactical_dash", TacticalDashSpell::new);

    
    // ========== 法术==========
    public static final RegistryObject<AbstractSpell> SMOULDERING_FIRE = 
        SPELLS.register("smouldering_fire", SmoulderingFireSpell::new);

    // ========== 法术==========
    public static final RegistryObject<AbstractSpell> JING_TING_JUE =
        SPELLS.register("jing_ting_jue", JingTingJueSpell::new);

    public static final RegistryObject<AbstractSpell> ARCANE_AIM = 
        SPELLS.register("arcane_aim", ArcaneAimSpell::new);

    

    // ========== 注魔子弹法术 ==========
    public static final RegistryObject<AbstractSpell> LIGHTNING_IMBUED_BULLET = 
        SPELLS.register("lightning_imbued_bullet", LightningImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> FROST_IMBUED_BULLET = 
        SPELLS.register("frost_imbued_bullet", FrostImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> ENDER_IMBUED_BULLET = 
        SPELLS.register("ender_imbued_bullet", EnderImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> BLOOD_IMBUED_BULLET = 
        SPELLS.register("blood_imbued_bullet", BloodImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> HOLY_IMBUED_BULLET = 
        SPELLS.register("holy_imbued_bullet", HolyImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> NATURE_IMBUED_BULLET = 
        SPELLS.register("nature_imbued_bullet", NatureImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> FIRE_IMBUED_BULLET = 
        SPELLS.register("fire_imbued_bullet", FireImbuedBulletSpell::new);

    public static final RegistryObject<AbstractSpell> EVOCATION_IMBUED_BULLET = 
        SPELLS.register("evocation_imbued_bullet", EvocationImbuedBulletSpell::new);    

    // ========== 法术 ==========
    public static final RegistryObject<AbstractSpell> LIZHI_YAN = 
        SPELLS.register("lizhi_yan", LizhiYanSpell::new);

    // ========== 液氮大炮法术 ==========
    public static final RegistryObject<AbstractSpell> LIQUID_NITROGEN_CANNON =
        SPELLS.register("liquid_nitrogen_cannon", LiquidNitrogenCannonSpell::new);

    public static void register(IEventBus eventBus) {
        SPELLS.register(eventBus);
    }
    
}