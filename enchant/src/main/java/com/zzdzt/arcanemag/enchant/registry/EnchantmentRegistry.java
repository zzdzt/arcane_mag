package com.zzdzt.arcanemag.enchant.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.AmmoRetentionEnchantment;
import com.zzdzt.arcanemag.enchant.ArcaneFocusEnchantment;
import com.zzdzt.arcanemag.enchant.ArcaneFrenzyEnchantment;
import com.zzdzt.arcanemag.enchant.AutoReleaseEnchantment;
import com.zzdzt.arcanemag.enchant.BraceEnchantment;
import com.zzdzt.arcanemag.enchant.ChargeOverdriveEnchantment;
import com.zzdzt.arcanemag.enchant.ChargePassiveEnchantment;
import com.zzdzt.arcanemag.enchant.ChargeStacksEnchantment;
import com.zzdzt.arcanemag.enchant.DryManaFrenzyEnchantment;
import com.zzdzt.arcanemag.enchant.FireFrenzyEnchantment;
import com.zzdzt.arcanemag.enchant.ForThePeopleEnchantment;
import com.zzdzt.arcanemag.enchant.GuidanceAmplifyEnchantment;
import com.zzdzt.arcanemag.enchant.KuangYanEnchantment;
import com.zzdzt.arcanemag.enchant.ManaHawkEyeEnchantment;
import com.zzdzt.arcanemag.enchant.ManaMagazineEnchantment;
import com.zzdzt.arcanemag.enchant.MultiTriggerEnchantment;
import com.zzdzt.arcanemag.enchant.OverflowEnchantment;
import com.zzdzt.arcanemag.enchant.PelletBurstEnchantment;
import com.zzdzt.arcanemag.enchant.SpellResonanceEnchantment;
import com.zzdzt.arcanemag.enchant.UnstableChargeEnchantment;
import com.zzdzt.arcanemag.enchant.ShadowReloadEnchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EnchantmentRegistry {
    public static final DeferredRegister<net.minecraft.world.item.enchantment.Enchantment> ENCHANTMENTS =
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ArcaneMag.MODID);

    /** 充能随放：弹匣充能满且命中时，自动以命中实体为目标释放铭刻法术。 */
    public static final RegistryObject<AutoReleaseEnchantment> AUTO_RELEASE =
        ENCHANTMENTS.register("auto_release", AutoReleaseEnchantment::new);

    /** 多重释放：充能溢出转化为额外释放次数。 */
    public static final RegistryObject<ChargeStacksEnchantment> CHARGE_STACKS =
        ENCHANTMENTS.register("charge_stacks", ChargeStacksEnchantment::new);

    /** 被动充能：持枪时自动恢复充能。 */
    public static final RegistryObject<ChargePassiveEnchantment> CHARGE_PASSIVE =
        ENCHANTMENTS.register("charge_passive", ChargePassiveEnchantment::new);

    /** 过载：充能溢出激活过载，施法获得法强加成。 */
    public static final RegistryObject<ChargeOverdriveEnchantment> CHARGE_OVERDRIVE =
        ENCHANTMENTS.register("charge_overdrive", ChargeOverdriveEnchantment::new);

    /** 节约风气：攻击时概率不消耗弹药。 */
    public static final RegistryObject<AmmoRetentionEnchantment> AMMO_RETENTION =
        ENCHANTMENTS.register("ammo_retention", AmmoRetentionEnchantment::new);

    /** 奥术聚焦：施法后接下来数发子弹爆头倍率提升。 */
    public static final RegistryObject<ArcaneFocusEnchantment> ARCANE_FOCUS =
        ENCHANTMENTS.register("arcane_focus", ArcaneFocusEnchantment::new);

    /** 多重扳机：一次扣扳机额外射击 N 次（N=等级），弹药消耗随之翻倍。 */
    public static final RegistryObject<MultiTriggerEnchantment> MULTI_TRIGGER =
        ENCHANTMENTS.register("multi_trigger", MultiTriggerEnchantment::new);

    /** 魔力弹匣：弹匣打空时支付法力凭空补 1 发继续射击。 */
    public static final RegistryObject<ManaMagazineEnchantment> MANA_MAGAZINE =
        ENCHANTMENTS.register("mana_magazine", ManaMagazineEnchantment::new);

    /** 狂宴：爆头击杀回复法力。 */
    public static final RegistryObject<KuangYanEnchantment> KUANG_YAN =
        ENCHANTMENTS.register("kuang_yan", KuangYanEnchantment::new);

    /** 我为人人：6秒内爆头3名不同敌人获得临时 spell_power 加成。等级 = 法强档位。 */
    public static final RegistryObject<ForThePeopleEnchantment> FOR_THE_PEOPLE =
        ENCHANTMENTS.register("for_the_people", ForThePeopleEnchantment::new);

    /** 涸法狂击：法力维持在低区间时枪械获得伤害（随等级）+ 射速（固定）增益。等级 = 伤害档位。 */
    public static final RegistryObject<DryManaFrenzyEnchantment> DRY_MANA_FRENZY =
        ENCHANTMENTS.register("dry_mana_frenzy", DryManaFrenzyEnchantment::new);

    /** 暗隐装填：换弹开始时获得 2s 真隐身。仅弹匣可刻。 */
    public static final RegistryObject<ShadowReloadEnchantment> SHADOW_RELOAD =
        ENCHANTMENTS.register("shadow_reload", ShadowReloadEnchantment::new);

    /** 法力鹰眼：开镜时消耗法力提升射击伤害（上限随等级）。仅瞄准镜可刻。 */
    public static final RegistryObject<ManaHawkEyeEnchantment> MANA_HAWK_EYE =
        ENCHANTMENTS.register("mana_hawk_eye", ManaHawkEyeEnchantment::new);

    /** 引导增幅：释放持续型法术期间枪械获得伤害+射速增益。等级 = 伤害加成档位。 */
    public static final RegistryObject<GuidanceAmplifyEnchantment> GUIDANCE_AMPLIFY =
        ENCHANTMENTS.register("guidance_amplify", GuidanceAmplifyEnchantment::new);

    /** 法术共鸣：仅枪口，base damage 增加法术等级数值，走正常护甲结算。与多重扳机互斥。 */
    public static final RegistryObject<SpellResonanceEnchantment> SPELL_RESONANCE =
        ENCHANTMENTS.register("spell_resonance", SpellResonanceEnchantment::new);

    /** 充盈：弹匣法术完全充满时枪械获得伤害+射速增益，持续10s或法术释放。等级 = 伤害档位。 */
    public static final RegistryObject<OverflowEnchantment> OVERFLOW =
        ENCHANTMENTS.register("overflow", OverflowEnchantment::new);

    /** 火力狂热：持续战斗12s后枪械伤害增加，直到3s内无战斗行为。等级 = 伤害档位。 */
    public static final RegistryObject<FireFrenzyEnchantment> FIRE_FRENZY =
        ENCHANTMENTS.register("fire_frenzy", FireFrenzyEnchantment::new);

    /** 奥术狂热：持续战斗12s后IS SPELL_POWER增加，直到3s内无战斗行为。等级 = 法强档位。 */
    public static final RegistryObject<ArcaneFrenzyEnchantment> ARCANE_FRENZY =
        ENCHANTMENTS.register("arcane_frenzy", ArcaneFrenzyEnchantment::new);

    /** 爆破弹丸：累计命中阈值后消耗一半弹匣充能造成范围伤害。仅枪口，与多重扳机/法术共鸣互斥。 */
    public static final RegistryObject<PelletBurstEnchantment> PELLET_BURST =
        ENCHANTMENTS.register("pellet_burst", PelletBurstEnchantment::new);

    /** 蓄势：非战斗状态积累双蓄势，枪械攻击/施法分别消耗增加伤害。仅枪托。 */
    public static final RegistryObject<BraceEnchantment> BRACE =
        ENCHANTMENTS.register("brace", BraceEnchantment::new);

    /** 不稳定充能：充能值随机化。仅弹匣，与 stacks/passive/overdrive 互斥。 */
    public static final RegistryObject<UnstableChargeEnchantment> UNSTABLE_CHARGE =
        ENCHANTMENTS.register("unstable_charge", UnstableChargeEnchantment::new);

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
