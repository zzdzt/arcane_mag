package com.zzdzt.arcanemag.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

/**
 * ArcaneMag 模组配置
 */
public class ArcaneMagConfig {

    public static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SERVER_SPEC;

    // ==================== spell：施法 ====================
    public static final ForgeConfigSpec.DoubleValue MANA_COST_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SNEAK_TO_CAST;

    // ==================== upgrade：升级法球 ====================
    public static final ForgeConfigSpec.BooleanValue ALLOW_UPGRADE_ORBS_ON_ATTACHMENTS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_UPGRADE_ORBS_ON_GUNS;
    public static final ForgeConfigSpec.IntValue MAX_UPGRADE_ORBS_PER_ITEM;
    public static final ForgeConfigSpec.DoubleValue ORB_SPELL_POWER_BONUS;

    // ==================== enchantment：附魔 ====================
    public static final ForgeConfigSpec.IntValue MAX_ENCHANTMENTS_PER_ATTACHMENT;
    public static final ForgeConfigSpec.DoubleValue AMMO_RETENTION_CHANCE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ARCANE_FOCUS_HEADSHOT_BONUS;
    public static final ForgeConfigSpec.IntValue MANA_MAGAZINE_BASE_COST;
    public static final ForgeConfigSpec.IntValue MANA_MAGAZINE_COST_REDUCTION_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue MANA_MAGAZINE_MIN_COST;
    public static final ForgeConfigSpec.DoubleValue KUANG_YAN_MANA_PERCENT_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue FOR_THE_PEOPLE_WINDOW_TICKS;
    public static final ForgeConfigSpec.IntValue FOR_THE_PEOPLE_REQUIRED_HEADSHOTS;
    public static final ForgeConfigSpec.IntValue FOR_THE_PEOPLE_BUFF_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue FOR_THE_PEOPLE_SPELL_POWER_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue DRY_MANA_FRENZY_MANA_MIN_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DRY_MANA_FRENZY_MANA_MAX_PERCENT;
    public static final ForgeConfigSpec.DoubleValue DRY_MANA_FRENZY_DAMAGE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue DRY_MANA_FRENZY_FIRE_RATE_BONUS;
    public static final ForgeConfigSpec.IntValue SHADOW_RELOAD_INVIS_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue SHADOW_RELOAD_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue MANA_HAWK_EYE_MANA_COST_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MANA_HAWK_EYE_MANA_PER_BONUS;
    public static final ForgeConfigSpec.DoubleValue MANA_HAWK_EYE_MAX_BONUS_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue GUIDANCE_AMPLIFY_DAMAGE_BONUS_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue GUIDANCE_AMPLIFY_RPM_BONUS;
    public static final ForgeConfigSpec.DoubleValue SPELL_RESONANCE_COEFFICIENT;
    public static final ForgeConfigSpec.DoubleValue OVERFLOW_DAMAGE_BONUS_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue OVERFLOW_RPM_BONUS;
    public static final ForgeConfigSpec.IntValue OVERFLOW_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue FRENZY_COMBAT_WINDOW_TICKS;
    public static final ForgeConfigSpec.IntValue FRENZY_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.DoubleValue FIRE_FRENZY_DAMAGE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue ARCANE_FRENZY_SPELL_POWER_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue PELLET_BURST_THRESHOLD_BASE;
    public static final ForgeConfigSpec.IntValue PELLET_BURST_THRESHOLD_REDUCTION_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue PELLET_BURST_CHARGE_CONSUME_RATIO;
    public static final ForgeConfigSpec.DoubleValue PELLET_BURST_DAMAGE_COEFFICIENT;
    public static final ForgeConfigSpec.DoubleValue PELLET_BURST_RADIUS;
    public static final ForgeConfigSpec.IntValue PELLET_BURST_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.IntValue BRACE_ACCUMULATE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue BRACE_STACKS_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue BRACE_DAMAGE_PER_STACK;
    public static final ForgeConfigSpec.IntValue BRACE_COMBAT_TIMEOUT_TICKS;

    // ==================== spell_classification：法术分类 ====================
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NO_TARGET_SPELLS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REQUIRES_TARGET_SPELLS;

    // ==================== mod_charge：充能机制 ====================
    public static final ForgeConfigSpec.DoubleValue CHARGE_BASE_MAX;
    public static final ForgeConfigSpec.DoubleValue CHARGE_CD_REFERENCE;
    public static final ForgeConfigSpec.DoubleValue CHARGE_PER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CHARGE_KILL_BONUS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_OVERDRIVE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue CHARGE_OVERDRIVE_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue CHARGE_OVERDRIVE_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK;
    public static final ForgeConfigSpec.IntValue CHARGE_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue CHARGE_PASSIVE_RATE_PER_LEVEL;
    public static final ForgeConfigSpec.DoubleValue CHARGE_MAX_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHARGE_WEAPON_TYPE_COEFFICIENTS;

    static {
        // ---------- spell ----------
        SERVER_BUILDER.push("spell");
        MANA_COST_MULTIPLIER = SERVER_BUILDER
                .comment("Multiplier for spell mana cost when cast via gun.",
                         "1.0 = normal cost, 0.5 = half cost, 2.0 = double cost.")
                .defineInRange("mana_cost_multiplier", 0.4, 0.1, 10.0);
        REQUIRE_SNEAK_TO_CAST = SERVER_BUILDER
                .comment("Require player to be sneaking to cast spell with gun.")
                .define("require_sneak", false);
        SERVER_BUILDER.pop();

        // ---------- upgrade ----------
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

        // ---------- enchantment ----------
        SERVER_BUILDER.push("enchantment");
        MAX_ENCHANTMENTS_PER_ATTACHMENT = SERVER_BUILDER
                .comment("Maximum ArcaneMag enchantments per attachment item.",
                         "Only counts arcane_mag namespace enchantments; other mods unaffected.")
                .defineInRange("max_enchantments_per_attachment", 2, 1, 10);
        AMMO_RETENTION_CHANCE_PER_LEVEL = SERVER_BUILDER
                .comment("Ammo Retention: chance per enchantment level.",
                         "Total chance = level * this value (clamped to 1.0). E.g. level 3, 0.08 = 24.0%.")
                .defineInRange("ammo_retention_chance_per_level", 0.08, 0.0, 1.0);
        ARCANE_FOCUS_HEADSHOT_BONUS = SERVER_BUILDER
                .comment("Arcane Focus: flat headshot multiplier bonus per boosted bullet.",
                         "Added to the gun's headshot multiplier on headshot hits while charges remain.",
                         "E.g. gun headshot 1.5 + bonus 0.5 = 2.0x headshot damage.")
                .defineInRange("arcane_focus_headshot_bonus", 0.5, 0.0, 10.0);
        MANA_MAGAZINE_BASE_COST = SERVER_BUILDER
                .comment("Mana Magazine: base mana cost per conjured bullet (at level 0 of the formula).",
                         "Cost = max(base - reduction_per_level * level, min_cost).")
                .defineInRange("mana_magazine_base_cost", 14, 0, 10000);
        MANA_MAGAZINE_COST_REDUCTION_PER_LEVEL = SERVER_BUILDER
                .comment("Mana Magazine: mana cost reduction per enchantment level.",
                         "Cost = max(base - reduction_per_level * level, min_cost).",
                         "Default 2 gives I=12 / II=10 / III=8.")
                .defineInRange("mana_magazine_cost_reduction_per_level", 2, 0, 10000);
        MANA_MAGAZINE_MIN_COST = SERVER_BUILDER
                .comment("Mana Magazine: minimum mana cost per conjured bullet (formula floor).")
                .defineInRange("mana_magazine_min_cost", 1, 0, 10000);
        KUANG_YAN_MANA_PERCENT_PER_LEVEL = SERVER_BUILDER
                .comment("Kuang Yan (Frenzy Feast): mana restored per enchantment level on headshot kill,",
                         "as a fraction of max mana. Total = level * this value.",
                         "Default 0.05 gives I=5% / II=10% / III=15% of max mana.")
                .defineInRange("kuang_yan_mana_percent_per_level", 0.05, 0.0, 1.0);
        FOR_THE_PEOPLE_WINDOW_TICKS = SERVER_BUILDER
                .comment("For The People: headshot accumulation window in ticks (20 = 1 second).",
                         "Window resets on expiry. Default 120 = 6 seconds.")
                .defineInRange("for_the_people_window_ticks", 120, 20, 6000);
        FOR_THE_PEOPLE_REQUIRED_HEADSHOTS = SERVER_BUILDER
                .comment("For The People: distinct enemy headshots required within the window to trigger the buff.",
                         "Default 3.")
                .defineInRange("for_the_people_required_headshots", 3, 1, 20);
        FOR_THE_PEOPLE_BUFF_DURATION_TICKS = SERVER_BUILDER
                .comment("For The People: spell_power buff duration in ticks (20 = 1 second).",
                         "Re-triggering refreshes to this duration. Default 200 = 10 seconds.")
                .defineInRange("for_the_people_buff_duration_ticks", 200, 20, 6000);
        FOR_THE_PEOPLE_SPELL_POWER_PER_LEVEL = SERVER_BUILDER
                .comment("For The People: spell_power bonus (MULTIPLY_BASE) per enchantment level.",
                         "Total = level * this value. Default 0.15 gives I=+15% / II=+30% / III=+45%.")
                .defineInRange("for_the_people_spell_power_per_level", 0.15, 0.0, 5.0);
        DRY_MANA_FRENZY_MANA_MIN_PERCENT = SERVER_BUILDER
                .comment("Dry Mana Frenzy: lower bound of mana percent range that triggers the buff.",
                         "Active when mana% is within [min, max]. Default 10 (%)")
                .defineInRange("dry_mana_frenzy_mana_min_percent", 10.0, 0.0, 100.0);
        DRY_MANA_FRENZY_MANA_MAX_PERCENT = SERVER_BUILDER
                .comment("Dry Mana Frenzy: upper bound of mana percent range that triggers the buff.",
                         "Active when mana% is within [min, max]. Default 35 (%)")
                .defineInRange("dry_mana_frenzy_mana_max_percent", 35.0, 0.0, 100.0);
        DRY_MANA_FRENZY_DAMAGE_PER_LEVEL = SERVER_BUILDER
                .comment("Dry Mana Frenzy: gun damage multiplier bonus per enchantment level.",
                         "damageMul = 1 + level * this value. Default 0.20 gives I=+20% / II=+40% / III=+60%.")
                .defineInRange("dry_mana_frenzy_damage_per_level", 0.20, 0.0, 5.0);
        DRY_MANA_FRENZY_FIRE_RATE_BONUS = SERVER_BUILDER
                .comment("Dry Mana Frenzy: fixed fire rate (RPM) multiplier bonus, independent of level.",
                         "rpmMul = 1 + this value. Default 0.20 = +20% RPM.")
                .defineInRange("dry_mana_frenzy_fire_rate_bonus", 0.20, 0.0, 5.0);
        SHADOW_RELOAD_INVIS_DURATION_TICKS = SERVER_BUILDER
                .comment("Shadow Reload: True Invisibility duration in ticks on reload start (20 = 1 second).",
                         "Default 50 = 2.5 seconds.")
                .defineInRange("shadow_reload_invis_duration_ticks", 50, 1, 6000);
        SHADOW_RELOAD_COOLDOWN_TICKS = SERVER_BUILDER
                .comment("Shadow Reload: internal cooldown in ticks between triggers (20 = 1 second).",
                         "Default 100 = 5 seconds.")
                .defineInRange("shadow_reload_cooldown_ticks", 100, 1, 6000);
        MANA_HAWK_EYE_MANA_COST_PERCENT = SERVER_BUILDER
                .comment("Mana Hawk Eye: percent of max mana drained every 20 ticks while aiming.",
                         "Default 10 (%) = 10% max mana per 20 ticks (1s).")
                .defineInRange("mana_hawk_eye_mana_cost_percent", 10, 0.0, 100.0);
        MANA_HAWK_EYE_MANA_PER_BONUS = SERVER_BUILDER
                .comment("Mana Hawk Eye: absolute mana consumed per +1% damage bonus.",
                         "damageBonus% = min(consumedMana / this, maxBonus). Default 10 (mana points).")
                .defineInRange("mana_hawk_eye_mana_per_bonus", 10.0, 1.0, 10000.0);
        MANA_HAWK_EYE_MAX_BONUS_PER_LEVEL = SERVER_BUILDER
                .comment("Mana Hawk Eye: max damage bonus percent per enchantment level.",
                         "Total cap = level * this value. Default 20 gives I=+20% / II=+40% / III=+60%.")
                .defineInRange("mana_hawk_eye_max_bonus_per_level", 20.0, 1.0, 500.0);
        GUIDANCE_AMPLIFY_DAMAGE_BONUS_PER_LEVEL = SERVER_BUILDER
                .comment("Guidance Amplify: damage bonus percent per enchantment level.",
                         "Active while casting a continuous (CastType.CONTINUOUS) spell.",
                         "Total = level * this value. Default 20 gives I=+20% / II=+40% / III=+60%.")
                .defineInRange("guidance_amplify_damage_bonus_per_level", 20.0, 0.0, 500.0);
        GUIDANCE_AMPLIFY_RPM_BONUS = SERVER_BUILDER
                .comment("Guidance Amplify: flat fire rate (RPM) bonus percent while active.",
                         "Default 15 = +15% RPM (not affected by level).")
                .defineInRange("guidance_amplify_rpm_bonus", 15.0, 0.0, 500.0);
        SPELL_RESONANCE_COEFFICIENT = SERVER_BUILDER
                .comment("Spell Resonance: damage bonus coefficient.",
                         "bonus = enchantLevel × spellLevel × this coefficient.",
                         "Added to baseAmount before TACZ armor settlement.",
                         "Default 0.25 gives: III + spell 5 = +4.75 damage.")
                .defineInRange("spell_resonance_coefficient", 0.25, 0.0, 100.0);
        OVERFLOW_DAMAGE_BONUS_PER_LEVEL = SERVER_BUILDER
                .comment("Overflow: damage bonus percent per enchantment level.",
                         "Active when magazine spell charge is full.",
                         "Total = level * this value. Default 6 gives I=+6% / II=+12% / III=+18%.")
                .defineInRange("overflow_damage_bonus_per_level", 6.0, 0.0, 500.0);
        OVERFLOW_RPM_BONUS = SERVER_BUILDER
                .comment("Overflow: flat fire rate (RPM) bonus percent while active.",
                         "Default 10 = +10% RPM (not affected by level).")
                .defineInRange("overflow_rpm_bonus", 10, 0.0, 500.0);
        OVERFLOW_DURATION_TICKS = SERVER_BUILDER
                .comment("Overflow: active duration in ticks.",
                         "Removed early when the imbued spell is cast.",
                         "Default 240 = 12 seconds.")
                .defineInRange("overflow_duration_ticks", 240, 20, 1200);
        FRENZY_COMBAT_WINDOW_TICKS = SERVER_BUILDER
                .comment("Frenzy (Fire/Arcane): continuous combat window in ticks required to activate.",
                         "Combat = firing hit or taking damage from a LivingEntity.",
                         "Default 240 = 12 seconds.")
                .defineInRange("frenzy_combat_window_ticks", 240, 20, 6000);
        FRENZY_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("Frenzy (Fire/Arcane): grace period in ticks after the last combat action.",
                         "If no combat action occurs within this window, the buff is removed.",
                         "Default 60 = 3 seconds.")
                .defineInRange("frenzy_timeout_ticks", 60, 1, 6000);
        FIRE_FRENZY_DAMAGE_PER_LEVEL = SERVER_BUILDER
                .comment("Fire Frenzy: damage bonus percent per enchantment level.",
                         "Active after continuous combat window is met.",
                         "Total = level * this value. Default 5 gives I=+5% / II=+10% / III=+15%.")
                .defineInRange("fire_frenzy_damage_per_level", 5.0, 0.0, 500.0);
        ARCANE_FRENZY_SPELL_POWER_PER_LEVEL = SERVER_BUILDER
                .comment("Arcane Frenzy: spell_power bonus (MULTIPLY_BASE) per enchantment level.",
                         "Active after continuous combat window is met.",
                         "Total = level * this value. Default 0.10 gives I=+10% / II=+20% / III=+30%.")
                .defineInRange("arcane_frenzy_spell_power_per_level", 0.10, 0.0, 5.0);
        PELLET_BURST_THRESHOLD_BASE = SERVER_BUILDER
                .comment("Pellet Burst: base hit-count threshold to trigger explosion (at level I).",
                         "threshold = base - (level-1) * reduction. Default 50 gives I=50/II=40/III=30.")
                .defineInRange("pellet_burst_threshold_base", 50, 1, 1000);
        PELLET_BURST_THRESHOLD_REDUCTION_PER_LEVEL = SERVER_BUILDER
                .comment("Pellet Burst: threshold reduction per enchantment level.",
                         "Default 10: I=50/II=40/III=30.")
                .defineInRange("pellet_burst_threshold_reduction_per_level", 10, 0, 500);
        PELLET_BURST_CHARGE_CONSUME_RATIO = SERVER_BUILDER
                .comment("Pellet Burst: fraction of current magazine spell charge consumed on trigger.",
                         "Default 0.5 = half of current charge.")
                .defineInRange("pellet_burst_charge_consume_ratio", 0.5, 0.0, 1.0);
        PELLET_BURST_DAMAGE_COEFFICIENT = SERVER_BUILDER
                .comment("Pellet Burst: damage = consumed_charge * this coefficient.",
                         "Tune based on typical charge_max values. Default 0.05.")
                .defineInRange("pellet_burst_damage_coefficient", 0.1, 0.0, 100.0);
        PELLET_BURST_RADIUS = SERVER_BUILDER
                .comment("Pellet Burst: explosion radius in blocks.",
                         "Default 3.0.")
                .defineInRange("pellet_burst_radius", 3.0, 0.5, 20.0);
        PELLET_BURST_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("Pellet Burst: hit-count reset timeout in ticks.",
                         "If no hit occurs within this window, counter resets.",
                         "Default 60 = 3 seconds.")
                .defineInRange("pellet_burst_timeout_ticks", 60, 1, 6000);
        BRACE_ACCUMULATE_INTERVAL_TICKS = SERVER_BUILDER
                .comment("Brace: accumulation interval in ticks (1 stack per interval).",
                         "Default 20 = 1 stack per second.")
                .defineInRange("brace_accumulate_interval_ticks", 20, 1, 200);
        BRACE_STACKS_PER_LEVEL = SERVER_BUILDER
                .comment("Brace: max stacks per enchantment level.",
                         "Total max = level * this value. Default 5 gives I=5/II=10/III=15.")
                .defineInRange("brace_stacks_per_level", 5, 1, 100);
        BRACE_DAMAGE_PER_STACK = SERVER_BUILDER
                .comment("Brace: damage bonus percent per stack.",
                         "Applies to both gun damage and spell_power. Default 3.0 = +3% per stack.")
                .defineInRange("brace_damage_per_stack", 3.0, 0.0, 100.0);
        BRACE_COMBAT_TIMEOUT_TICKS = SERVER_BUILDER
                .comment("Brace: combat state timeout in ticks.",
                         "No combat action for this duration → start accumulating.",
                         "Default 60 = 3 seconds.")
                .defineInRange("brace_combat_timeout_ticks", 60, 1, 6000);
        SERVER_BUILDER.pop();

        // ---------- spell_classification ----------
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

        // ---------- mod_charge ----------
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
                .defineInRange("overdrive_threshold", 1.5, 1.0, 5.0);
        CHARGE_OVERDRIVE_DURATION_TICKS = SERVER_BUILDER
                .comment("Overdrive active duration in ticks (20 = 1 second).")
                .defineInRange("overdrive_duration_ticks", 200, 20, 1200);
        CHARGE_OVERDRIVE_MAX_STACKS = SERVER_BUILDER
                .comment("Maximum overdrive stacks (1 = binary on/off).")
                .defineInRange("overdrive_max_stacks", 1, 1, 10);
        CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK = SERVER_BUILDER
                .comment("Spell power bonus per overdrive stack (multiplier).",
                         "1.0 = no bonus, 1.5 = +50% damage.")
                .defineInRange("overdrive_spell_power_per_stack", 1.25, 1.0, 3.0);
        CHARGE_MAX_STACKS = SERVER_BUILDER
                .comment("Maximum charge stacks.")
                .defineInRange("max_stacks", 2, 1, 10);
        CHARGE_PASSIVE_RATE_PER_LEVEL = SERVER_BUILDER
                .comment("Passive charge regen per second per enchantment level.",
                         "Total rate = level * this value. E.g. level 2, value 0.5 = 1.0/sec.")
                .defineInRange("passive_rate_per_level", 5, 0.01, 10.0);
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
