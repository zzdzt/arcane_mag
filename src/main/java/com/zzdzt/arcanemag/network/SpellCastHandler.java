package com.zzdzt.arcanemag.network;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.spell.GunCastTargetResolver;
import com.zzdzt.arcanemag.spell.SpellTargetDetector;
import com.zzdzt.arcanemag.spell.SpellBehaviorCache;
import com.zzdzt.arcanemag.spell.SpellBehaviorCache.SpellBehavior;
import com.zzdzt.arcanemag.utils.GunCastManaContext;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;

import net.minecraft.core.registries.BuiltInRegistries;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * 施法请求服务端处理器
 *
 * 自学习目标检测：
 * 每次 checkPreCastConditions 调用前重置 PreCastTargetHelperMixin 旗标，
 * 如果检查失败且旗标被设置（说明法术调用了 preCastTargetHelper），
 * 则自动用 GunCastTargetResolver 预解析目标并重试。
 * 重试成功后学习为 NEEDS_TARGET 并记入缓存，后续施法直接预解析。
 */
public class SpellCastHandler {
    static final Map<UUID, CastingContext> ACTIVE_CASTS = new ConcurrentHashMap<>();
    public static final UUID TEMP_MODIFIER_UUID = UUID.fromString("5ee128ef-08ff-4113-b12f-65ddfee78658");
    public static final UUID OVERDRIVE_MODIFIER_UUID = UUID.fromString("c073ee87-86a1-4427-bcab-93c369b2ca6e");

    //入口：处理客户端请求 
    public static void handleCastRequest(ResourceLocation spellId, int spellLevel, ServerPlayer player) {
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        if (!(gunStack.getItem() instanceof com.tacz.guns.api.item.IGun)) return;

        SpellData actualSpell = MagazineSpellHelper.extractSpell(gunStack);
        if (actualSpell == null) return;

        AbstractSpell spell = actualSpell.getSpell();
        int level = actualSpell.getLevel();

        // 校验客户端数据
        if (!spell.getSpellResource().equals(spellId) || level != spellLevel) return;
        if (ArcaneMagConfig.REQUIRE_SNEAK_TO_CAST.get() && !player.isShiftKeyDown()) return;

        // 如果已经在施法中，忽略新的请求
        if (ACTIVE_CASTS.containsKey(player.getUUID())) return;

        // 充能检查（替换原版 mana 检查）
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        boolean canCast = ModChargeData.hasStacks(magazine) || ModChargeData.isFull(magazine);
        if (!canCast) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.literal("§c充能不足")
            ));
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        Map<UpgradeOrbType, Integer> orbs = MagazineSpellHelper.getAllUpgradeOrbs(gunStack);
        if (!orbs.isEmpty()) {
            applyTemporaryAttributes(player, orbs);
        }

        try {
            //自学习目标检测 
            SpellBehavior targetNeed = SpellBehaviorCache.checkTargetNeed(spell);

            // 已知需要目标 & 非 UNKNOWN → 预解析目标
            if (targetNeed == SpellBehavior.NEEDS_TARGET) {
                LivingEntity target = GunCastTargetResolver.resolveTargetForSpell(player, spell);
                if (target != null) {
                    GunCastTargetResolver.setTargetCastData(player, target);
                } else if (SpellBehaviorCache.mustHaveTarget(spell)) {
                    // 严格依赖目标的法术，无目标直接取消
                    ArcaneMag.LOGGER.debug("Must-have-target spell {} has no target, cancelling", spellId);
                    cleanupCast(player, magicData, orbs);
                    return;
                }
                // 非严格依赖：继续尝试（让 checkPreCastConditions 自行判断）
            }

            // 路由到施法处理器
            CastType castType = spell.getCastType();

            if (castType == CastType.LONG) {
                handleLongCast(player, spell, level, magicData, gunStack, orbs);
            } else if (castType == CastType.CONTINUOUS) {
                handleContinuousCast(player, spell, level, magicData, gunStack, orbs);
            } else {
                handleInstantCast(player, spell, level, magicData, gunStack, orbs);
            }
        } catch (Exception e) {
            ArcaneMag.LOGGER.error("Error during cast request for spell {}: {}", spell.getSpellResource(), e.getMessage());
            cleanupCast(player, magicData, orbs);
        }
    }

    // 各类型施法
    private static void handleLongCast(ServerPlayer player, AbstractSpell spell, int level,
                                       MagicData magicData, ItemStack gunStack,
                                       Map<UpgradeOrbType, Integer> orbs) {
        // 法力系数旗标
        float coeff = ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue();
        GunCastManaContext.begin(player.getUUID(), coeff);
        try {
            CastResult result = spell.canBeCastedBy(level, CastSource.SPELLBOOK, magicData, player);
            if (result.message != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(result.message));
            }
            if (!result.isSuccess()) {
                cleanupCast(player, magicData, orbs);
                return;
            }
        } finally {
            GunCastManaContext.end(player.getUUID());
        }

        // 自学习目标检测 + 条件检查
        if (!checkPreCastConditionsWithLearning(player, spell, level, magicData, orbs)) {
            return;
        }

        if (player.isUsingItem()) player.stopUsingItem();

        // 过载加成：施法前应用临时法术强度
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine != null && ModChargeData.hasOverdrive(magazine)) {
            applyOverdriveBonus(player);
        }

        // 强制施法时长为 0，跳过吟唱时间
        int effectiveCastTime = 0;
        magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
        magicData.setPlayerCastingItem(gunStack);

        ACTIVE_CASTS.put(player.getUUID(), new CastingContext(
            player, spell, level, orbs, true, null, 0.0));

        // 清理充能状态（过载修饰符到 CastTickHandler 再移除，确保 Iron's Spells 先执行 onCast）
        if (magazine != null) {
            ModChargeData.onSpellCast(magazine);
            syncChargeData(player, magazine);
        }
    }

    private static void handleContinuousCast(ServerPlayer player, AbstractSpell spell, int level,
                                              MagicData magicData, ItemStack gunStack,
                                              Map<UpgradeOrbType, Integer> orbs) {
        // 法力系数旗标
        float coeff = ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue();
        GunCastManaContext.begin(player.getUUID(), coeff);
        try {
            CastResult result = spell.canBeCastedBy(level, CastSource.SPELLBOOK, magicData, player);
            if (result.message != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(result.message));
            }
            if (!result.isSuccess()) {
                cleanupCast(player, magicData, orbs);
                return;
            }
        } finally {
            GunCastManaContext.end(player.getUUID());
        }

        // 自学习目标检测 + 条件检查
        if (!checkPreCastConditionsWithLearning(player, spell, level, magicData, orbs)) {
            return;
        }

        // 计算充能扣除速率
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);

        // 过载加成：持续施法前应用临时法术强度（由 CastTickHandler 清理时移除）
        if (magazine != null && ModChargeData.hasOverdrive(magazine)) {
            applyOverdriveBonus(player);
        }

        int effectiveCastTime = spell.getEffectiveCastTime(level, player);
        magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
        magicData.setPlayerCastingItem(gunStack);

        double chargeMax = ModChargeData.getMax(magazine);

        int castTimeTicks = spell.getEffectiveCastTime(level, player);
        double chargeDrainPerTick = chargeMax / castTimeTicks;

        ACTIVE_CASTS.put(player.getUUID(),
            new CastingContext(player, spell, level, orbs, false, magazine, chargeDrainPerTick));
    }

    private static void handleInstantCast(ServerPlayer player, AbstractSpell spell, int level,
                                           MagicData magicData, ItemStack gunStack,
                                           Map<UpgradeOrbType, Integer> orbs) {
        // 法力系数旗标
        float coeff = ArcaneMagConfig.MANA_COST_MULTIPLIER.get().floatValue();
        GunCastManaContext.begin(player.getUUID(), coeff);
        try {
            CastResult result = spell.canBeCastedBy(level, CastSource.SPELLBOOK, magicData, player);
            if (result.message != null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(result.message));
            }
            if (!result.isSuccess()) {
                cleanupCast(player, magicData, orbs);
                return;
            }
        } finally {
            GunCastManaContext.end(player.getUUID());
        }

        // 自学习目标检测 + 条件检查
        if (!checkPreCastConditionsWithLearning(player, spell, level, magicData, orbs)) {
            return;
        }

        if (player.isUsingItem()) player.stopUsingItem();

        // 过载加成：施法前应用临时法术强度
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine != null && ModChargeData.hasOverdrive(magazine)) {
            applyOverdriveBonus(player);
        }

        int effectiveCastTime = 0;
        magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
        magicData.setPlayerCastingItem(gunStack);

        ACTIVE_CASTS.put(player.getUUID(), new CastingContext(
            player, spell, level, orbs, true, null, 0.0));

        // 清理充能状态（过载修饰符到 CastTickHandler 再移除，确保 Iron's Spells 先执行 onCast）
        if (magazine != null) {
            ModChargeData.onSpellCast(magazine);
            syncChargeData(player, magazine);
        }
    }

    // ==================== 自学习目标检测 ====================
    /**
     * 带自学习的 checkPreCastConditions。
     *
     * 流程：
     * 1. 重置 SpellTargetDetector 旗标
     * 2. 调用 spell.checkPreCastConditions()
     * 3. 如果失败且旗标被设置 → 法术需要目标但没找到 → 用 GunCastTargetResolver 重试
     * 4. 重试成功后学习为 NEEDS_TARGET
     * 5. 重试仍失败 → 学习为 NEEDS_TARGET（法术有目标依赖），返回 false
     *
     * @return true=通过条件检查，false=失败（已清理资源）
     */
    private static boolean checkPreCastConditionsWithLearning(
        ServerPlayer player, AbstractSpell spell, int level,
        MagicData magicData, Map<UpgradeOrbType, Integer> orbs) {

        ResourceLocation spellId = spell.getSpellResource();
        SpellBehavior knownBehavior = SpellBehaviorCache.getBehavior(spell);

        // 重置 Mixin 旗标
        SpellTargetDetector.reset();

        // 首次尝试
        boolean result = spell.checkPreCastConditions(player.level(), level, player, magicData);

        if (!result && SpellTargetDetector.wasCalled()) {
            // 法术调用了 preCastTargetHelper 但没找到目标 → 需要目标
            ArcaneMag.LOGGER.debug("[SelfLearn] {} called preCastTargetHelper and failed — learning as NEEDS_TARGET",
                spellId);

            // 用枪械目标解析器重试
            LivingEntity target = GunCastTargetResolver.resolveTargetForSpell(player, spell);
            if (target != null) {
                GunCastTargetResolver.setTargetCastData(player, target);

                // 重置旗标，重新检查
                SpellTargetDetector.reset();
                result = spell.checkPreCastConditions(player.level(), level, player, magicData);

                if (result) {
                    ArcaneMag.LOGGER.debug("[SelfLearn] {} succeeded with gun-resolved target {}",
                        spellId, target.getName().getString());
                }
            }

            // 无论重试是否成功，都学习为 NEEDS_TARGET（法术确实有目标依赖）
            SpellBehaviorCache.learnBehavior(spellId, SpellBehavior.NEEDS_TARGET);
        } else if (result && knownBehavior == SpellBehavior.UNKNOWN
                   && !SpellTargetDetector.wasCalled()) {
            // 成功且未调用 preCastTargetHelper → 自buff类型
            SpellBehaviorCache.learnBehavior(spellId, SpellBehavior.SELF_BUFF);
        }

        if (!result) {
            cleanupCast(player, magicData, orbs);
            return false;
        }

        return true;
    }

    //公共API
    public static void abortCastForPlayer(ServerPlayer player) {
        CastingContext context = ACTIVE_CASTS.remove(player.getUUID());
        if (context == null) return;

        MagicData magicData = MagicData.getPlayerMagicData(player);
        
        if (magicData.isCasting()) {
            Utils.serverSideCancelCast(player);
        }

        cleanupCast(player, magicData, context.orbs);
        removeOverdriveBonus(player);
        applyCooldownConfig(context.spell, magicData, player, true);
    }

    public static void applyCooldownConfig(AbstractSpell spell, MagicData magicData, ServerPlayer player, boolean cancelled) {
        magicData.getPlayerCooldowns().removeCooldown(spell.getSpellId());
    }

    // 属性管理
    public static void applyTemporaryAttributes(ServerPlayer player, Map<UpgradeOrbType, Integer> orbs) {
        float bonusPerOrb = ArcaneMagConfig.ORB_SPELL_POWER_BONUS.get().floatValue();
        for (Map.Entry<UpgradeOrbType, Integer> entry : orbs.entrySet()) {
            Attribute attr = entry.getKey().getAttribute();
            if (attr == null) continue;
            AttributeInstance instance = player.getAttribute(attr);
            if (instance == null) continue;

            double amount = entry.getValue() * bonusPerOrb;
            instance.removeModifier(TEMP_MODIFIER_UUID);
            instance.addTransientModifier(new AttributeModifier(
                    TEMP_MODIFIER_UUID, "ArcaneMagGunBonus", amount, AttributeModifier.Operation.MULTIPLY_BASE
            ));
        }
    }

    public static void removeTemporaryAttributes(ServerPlayer player, Map<UpgradeOrbType, Integer> orbs) {
        for (UpgradeOrbType type : orbs.keySet()) {
            Attribute attr = type.getAttribute();
            if (attr == null) continue;
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                instance.removeModifier(TEMP_MODIFIER_UUID);
            }
        }
    }

    // 过载加成

    /**
     * 应用过载法术强度加成（通过 irons_spellbooks:spell_power 全局加成，影响所有学派）
     * 仅在施法前调用，施法后由 removeOverdriveBonus 移除
     */
    public static void applyOverdriveBonus(ServerPlayer player) {
        double multiplier = ArcaneMagConfig.CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK.get();
        double amount = multiplier - 1.0; // 1.5 → 0.5 = +50%
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance == null) return;
        instance.removeModifier(OVERDRIVE_MODIFIER_UUID);
        instance.addTransientModifier(new AttributeModifier(
            OVERDRIVE_MODIFIER_UUID, "ArcaneMagOverdrive", amount,
            AttributeModifier.Operation.MULTIPLY_BASE
        ));
    }

    /**
     * 移除过载加成（安全幂等：无加成时无操作）
     */
    public static void removeOverdriveBonus(ServerPlayer player) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance != null) {
            instance.removeModifier(OVERDRIVE_MODIFIER_UUID);
        }
    }

    public static boolean isPlayerCasting(ServerPlayer player) {
        return ACTIVE_CASTS.containsKey(player.getUUID());
    }

    //内部工具 
    private static void cleanupCast(ServerPlayer player, MagicData magicData, Map<UpgradeOrbType, Integer> orbs) {
        removeTemporaryAttributes(player, orbs);
        magicData.setAdditionalCastData(null);
    }

    /**
     * 同步充能数据到客户端
     */
    private static void syncChargeData(ServerPlayer player, ItemStack magazine) {
        try {
            ArcaneMagNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ModChargeSyncPacket(magazine)
            );
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to sync charge data: {}", e.getMessage());
        }
    }

    static class CastingContext {
        final ServerPlayer player;
        final AbstractSpell spell;
        final int level;
        final Map<UpgradeOrbType, Integer> orbs;
        final boolean isInstantOrLong;
        final ItemStack magazine;
        final double chargeDrainPerTick;
        int castTicks = 0;
        boolean cancelled = false;

        CastingContext(ServerPlayer player, AbstractSpell spell, int level, Map<UpgradeOrbType, Integer> orbs,
                       boolean isInstantOrLong, ItemStack magazine, double chargeDrainPerTick) {
            this.player = player;
            this.spell = spell;
            this.level = level;
            this.orbs = orbs != null ? orbs : Collections.emptyMap();
            this.isInstantOrLong = isInstantOrLong;
            this.magazine = magazine;
            this.chargeDrainPerTick = chargeDrainPerTick;
        }
    }
}
