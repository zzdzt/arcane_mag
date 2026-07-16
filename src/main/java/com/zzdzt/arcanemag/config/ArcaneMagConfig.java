package com.zzdzt.arcanemag.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

/**
 * ArcaneMag 模组配置
 * 支持通过配置文件调整施法冷却、法力消耗、升级法球等核心系统参数。
 */
public class ArcaneMagConfig {

    public static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.DoubleValue MANA_COST_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SNEAK_TO_CAST;

    public static final ForgeConfigSpec.BooleanValue ALLOW_UPGRADE_ORBS_ON_ATTACHMENTS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_UPGRADE_ORBS_ON_GUNS;
    public static final ForgeConfigSpec.IntValue MAX_UPGRADE_ORBS_PER_ITEM;

    public static final ForgeConfigSpec.DoubleValue ORB_SPELL_POWER_BONUS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NO_TARGET_SPELLS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQUIRES_TARGET_SPELLS;

    public static final ForgeConfigSpec.DoubleValue CHARGE_BASE_MAX;
    public static final ForgeConfigSpec.DoubleValue CHARGE_CD_REFERENCE;
    public static final ForgeConfigSpec.DoubleValue CHARGE_PER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CHARGE_KILL_BONUS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_OVERDRIVE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue CHARGE_OVERDRIVE_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK;
    public static final ForgeConfigSpec.IntValue CHARGE_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_MAX_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHARGE_WEAPON_TYPE_COEFFICIENTS;

    

    static {
        SERVER_BUILDER.push("spell");
        MANA_COST_MULTIPLIER = SERVER_BUILDER
                .comment("Multiplier for spell mana cost when cast via gun.",
                         "1.0 = normal cost, 0.5 = half cost, 2.0 = double cost.")
                .defineInRange("mana_cost_multiplier", 0.4, 0.1, 10.0);
        REQUIRE_SNEAK_TO_CAST = SERVER_BUILDER
                .comment("Require player to be sneaking to cast spell with gun.")
                .define("require_sneak", false);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.push("upgrade");
        ALLOW_UPGRADE_ORBS_ON_ATTACHMENTS = SERVER_BUILDER
                .comment("Allow upgrade orbs on gun attachments (magazines, scopes, grips, etc.).")
                .define("allow_on_attachments", true);
        ALLOW_UPGRADE_ORBS_ON_GUNS = SERVER_BUILDER
                .comment("Allow upgrade orbs on gun items themselves.",
                         "If false, only attachments can receive upgrades.")
                .define("allow_on_guns", false);
        MAX_UPGRADE_ORBS_PER_ITEM = SERVER_BUILDER
                .comment("Maximum total upgrade orbs per item (attachments and guns).",
                         "Each orb provides +5% spell power to its school.")
                .defineInRange("max_orbs_per_item", 5, 1, 100);

        ORB_SPELL_POWER_BONUS = SERVER_BUILDER
                .comment("Spell power bonus per upgrade orb.",
                         "0.05 = +5% per orb. Applied as MULTIPLY_BASE attribute modifier.")
                .defineInRange("orb_spell_power_bonus", 0.05, 0.01, 1.0);
        SERVER_BUILDER.pop();

        // 法术分类配置
        SERVER_BUILDER.push("spell_classification");
        NO_TARGET_SPELLS = SERVER_BUILDER
                .comment("Spells that should NEVER use preCastTargetHelper.",
                         "These are typically self-buffs, AoE effects, or spells with their own targeting.",
                         "Examples: spider_aspect, oakskin, echoing_strikes, heartstop",
                         "Wildcards: *_aspect matches all aspect spells, @nature matches all nature spells.")
                .defineList("no_target_spells",
                    Arrays.asList(
                        "spider_aspect",          
                        "oakskin",               
                        "echoing_strikes",       
                        "heartstop",             
                        "true_invisibility",     
                        "haste",                 
                        "slow",                  
                        "charge",                
                        "ascension",             
                        "frost_step",           
                        "abyssal_shroud",        
                        "*_aspect",
                        // 学派标记：自然学派通常自身增益较多
                        "@nature"
                    ),
                    obj -> obj instanceof String);

        REQUIRES_TARGET_SPELLS = SERVER_BUILDER
                .comment("Spells that MUST use preCastTargetHelper.",
                         "Explicitly override automatic classification.",
                         "Use when a spell is misclassified by the inference engine.")
                .defineList("requires_target_spells",
                    Arrays.asList(
                        "guiding_bolt"
                    ),
                    obj -> obj instanceof String);

        SERVER_BUILDER.pop();

        // Mod charge settings (Remnant 2 style)
        SERVER_BUILDER.push("mod_charge");
        CHARGE_BASE_MAX = SERVER_BUILDER
                .comment("Base charge max for reference cooldown (10s).",
                         "chargeMax = baseChargeMax * (spellCooldown / cdReferenceSeconds)")
                .defineInRange("base_charge_max", 100.0, 1.0, 10000.0);
        CHARGE_CD_REFERENCE = SERVER_BUILDER
                .comment("Reference cooldown time in seconds for charge max calculation.")
                .defineInRange("cd_reference_seconds", 10.0, 1.0, 1000.0);
        CHARGE_PER_DAMAGE = SERVER_BUILDER
                .comment("Charge gained per point of damage.",
                         "Final charge gain = damage * weaponCoef * cdrAttribute.")
                .defineInRange("charge_per_damage", 1.0, 0.01, 100.0);
        CHARGE_KILL_BONUS = SERVER_BUILDER
                .comment("Bonus charge on kill (not affected by efficiency).")
                .defineInRange("kill_bonus", 60.0, 0.0, 1000.0);
        CHARGE_OVERDRIVE_THRESHOLD = SERVER_BUILDER
                .comment("Overdrive trigger threshold multiplier.",
                         "Overdrive triggers when charge > chargeMax * threshold.")
                .defineInRange("overdrive_threshold", 1.3, 1.0, 5.0);
        CHARGE_OVERDRIVE_MAX_STACKS = SERVER_BUILDER
                .comment("Maximum overdrive stacks (1 = binary on/off).")
                .defineInRange("overdrive_max_stacks", 1, 1, 10);
        CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK = SERVER_BUILDER
                .comment("Spell power bonus per overdrive stack (multiplier).",
                         "1.0 = no bonus, 1.5 = +50% damage.")
                .defineInRange("overdrive_spell_power_per_stack", 1.5, 1.0, 3.0);
        CHARGE_MAX_STACKS = SERVER_BUILDER
                .comment("Maximum charge stacks.")
                .defineInRange("max_stacks", 2, 1, 10);
        CHARGE_MAX_MULTIPLIER = SERVER_BUILDER
                .comment("Multiplier for charge max calculation.",
                         "chargeMax = baseChargeMax * (spellCooldown / cdReference) * chargeMaxMultiplier.",
                         "Higher values mean more charge is needed to cast the spell.")
                .defineInRange("charge_max_multiplier", 3.5, 0.1, 10.0);
        CHARGE_WEAPON_TYPE_COEFFICIENTS = SERVER_BUILDER
                .comment("Weapon type coefficients for charge gain.",
                         "Format: \"type:coefficient\" (e.g. \"pistol:1.2\").",
                         "Keys must match GunTabType enum names (lowercase).",
                         "Higher DPS weapons get lower coefficients to balance charge rate.")
                .defineList("weapon_type_coefficients",
                        java.util.Arrays.asList(
                            "pistol:1.8",
                            "smg:1.1",
                            "rifle:1.0",
                            "shotgun:1.4",
                            "sniper:0.9",
                            "mg:0.8",
                            "rpg:2.0"
                        ),
                        obj -> obj instanceof String && ((String) obj).contains(":"));
        SERVER_BUILDER.pop();

        SERVER_SPEC = SERVER_BUILDER.build();

        CLIENT_SPEC = CLIENT_BUILDER.build();
    }

    /**
     * 解析武器类型系数配置列表为 Map
     */
    
    public static Map<String, Double> getWeaponTypeCoefMap() {
        Map<String, Double> map = new java.util.HashMap<>();
        for (String entry : CHARGE_WEAPON_TYPE_COEFFICIENTS.get()) {
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                try {
                    map.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }
}