package com.zzdzt.arcanemag.network;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.event.enchant.ArcaneFocusHandler;
import com.zzdzt.arcanemag.event.enchant.BraceHandler;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
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
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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

    //充能消耗模式：NORMAL=手动施法；AUTO=自动释放
    public enum ConsumeMode { NORMAL, AUTO }

    private static final int AUTO_RECAST_GAP_TICKS = 6;
    private static final Map<UUID, Integer> LAST_RECYCLE_TICK = new ConcurrentHashMap<>();

    /**
     * 施法后按模式清理充能。
     * - NORMAL：走 ModChargeData.onSpellCast。
     * - AUTO：清充能条（consumeAll）+ 清过载。
     */
    private static void consumeAfterCast(ItemStack magazine, ConsumeMode mode) {
        if (magazine == null) return;
        if (mode == ConsumeMode.AUTO) {
            ModChargeData.consumeAll(magazine);
            ModChargeData.clearOverdrive(magazine);
            ModChargeData.resetOverdriveExpire(magazine);
        } else {
            ModChargeData.onSpellCast(magazine);
        }
    }

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

        MagicData magicData = MagicData.getPlayerMagicData(player);

        // ====== Recast 快速路径 ======
        // 如果该法术有活跃的 RecastInstance
        // 跳过充能检查和 ACTIVE_CASTS 阻塞，直接触发 IS 内部 recast 逻辑
        boolean isRecast = magicData.getPlayerRecasts().hasRecastForSpell(spell);
        if (isRecast) {
            handleRecastCast(player, spell, level, magicData, gunStack);
            return;
        }

        // ====== 正常首次施放路径 ======
        // 如果已经在施法中，忽略新的请求
        if (ACTIVE_CASTS.containsKey(player.getUUID())) return;

        // 充能检查
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        boolean canCast = ModChargeData.hasStacks(magazine) || ModChargeData.isFull(magazine);
        if (!canCast) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                net.minecraft.network.chat.Component.translatable("message.arcane_mag.insufficient_charge")
                    .withStyle(net.minecraft.ChatFormatting.RED)
            ));
            return;
        }

        Map<UpgradeOrbType, Integer> orbs = MagazineSpellHelper.getAllUpgradeOrbs(gunStack);
        if (!orbs.isEmpty()) {
            applyTemporaryAttributes(player, orbs);
        }
        BraceHandler.applySpellCastBonus(player, gunStack);

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
                handleLongCast(player, spell, level, magicData, gunStack, orbs, ConsumeMode.NORMAL);
            } else if (castType == CastType.CONTINUOUS) {
                handleContinuousCast(player, spell, level, magicData, gunStack, orbs);
            } else {
                handleInstantCast(player, spell, level, magicData, gunStack, orbs, ConsumeMode.NORMAL);
            }
        } catch (Exception e) {
            ArcaneMag.LOGGER.error("Error during cast request for spell {}: {}", spell.getSpellResource(), e.getMessage(), e);
            forceCleanup(player, magicData, orbs);
        }
    }

    /**
     * Recast 快速路径：法术已有活跃 RecastInstance，直接触发下一次 recast。
     * 公开供 auto_release 的连续 recast 驱动复用（与玩家按键 recast 同路径）。
     */
    private static void handleRecastCast(ServerPlayer player, AbstractSpell spell, int level,
                                           MagicData magicData, ItemStack gunStack) {
        try {
            if (player.isUsingItem()) player.stopUsingItem();

            int effectiveCastTime = 0;
            magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
            magicData.setPlayerCastingItem(gunStack);

            // 不加入 ACTIVE_CASTS：recast 由 IS 的 PlayerRecasts 管理生命周期
        } catch (Exception e) {
            ArcaneMag.LOGGER.error("Error during recast for spell {}: {}", spell.getSpellResource(), e.getMessage(), e);
            magicData.resetCastingState();
        }
    }

    // 各类型施法
    private static void handleLongCast(ServerPlayer player, AbstractSpell spell, int level,
                                       MagicData magicData, ItemStack gunStack,
                                       Map<UpgradeOrbType, Integer> orbs, ConsumeMode mode) {
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
            applyOverdriveBonus(player, magazine);
        }

        // 强制施法时长为 0，跳过吟唱时间
        int effectiveCastTime = 0;
        magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
        magicData.setPlayerCastingItem(gunStack);

        // 奥术聚焦：施法成功，写入爆头增强层数
        ArcaneFocusHandler.onSpellCast(player, gunStack);

        ACTIVE_CASTS.put(player.getUUID(), new CastingContext(
            player, spell, level, orbs, true, null, 0.0));

        // 清理充能状态（过载修饰符到 CastTickHandler 再移除，确保 Iron's Spells 先执行 onCast）
        if (magazine != null) {
            consumeAfterCast(magazine, mode);
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
            applyOverdriveBonus(player, magazine);
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
                                           Map<UpgradeOrbType, Integer> orbs, ConsumeMode mode) {
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
            applyOverdriveBonus(player, magazine);
        }

        int effectiveCastTime = 0;
        magicData.initiateCast(spell, level, effectiveCastTime, CastSource.SPELLBOOK, "mainhand");
        magicData.setPlayerCastingItem(gunStack);

        // 奥术聚焦：施法成功，写入爆头增强层数
        ArcaneFocusHandler.onSpellCast(player, gunStack);

        ACTIVE_CASTS.put(player.getUUID(), new CastingContext(
            player, spell, level, orbs, true, null, 0.0));

        // 清理充能状态（过载修饰符到 CastTickHandler 再移除，确保 Iron's Spells 先执行 onCast）
        if (magazine != null) {
            consumeAfterCast(magazine, mode);
            syncChargeData(player, magazine);
        }
    }

    /**
     * 自动释放入口（auto_release 附魔触发）：服务端直接调用，不走客户端。
     *
     * 与 {@link #handleCastRequest} 的差异：
     * 1. 以命中实体为目标（targetHint），不依赖准星解析（除非无命中实体 → 由 handler 内部自学习回退准星）。
     * 2. 消耗走 {@link ConsumeMode#AUTO}。
     * 3. 持续类法术（CONTINUOUS）不自动释放，交由玩家手动施放。
     *
     * @param targetHint  命中实体（可空）；非空时作为法术目标，跳过准星重解析。
     * @param impactPoint 精确命中点（可空）；暂保留，未来用于点指向 AoE 法术的视觉/落点锚定。
     * @param hasActiveRecast 调用方（AutoReleaseHandler）已判定：该法术是否已有活跃 RecastInstance。
     *                        true=本次命中走 recast 段（无需充能满）；false=走首次施放（需充能满）。
     *                        由调用方传入以避免重复判定，决策单一来源。
     */
    public static void handleAutoRelease(ResourceLocation spellId, int level, ServerPlayer player,
                                        @Nullable LivingEntity targetHint, @Nullable Vec3 impactPoint,
                                        boolean hasActiveRecast) {
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        if (!(gunStack.getItem() instanceof com.tacz.guns.api.item.IGun)) return;

        SpellData actualSpell = MagazineSpellHelper.extractSpell(gunStack);
        if (actualSpell == null) return;

        AbstractSpell spell = actualSpell.getSpell();
        int spellLevel = actualSpell.getLevel();
        if (!spell.getSpellResource().equals(spellId) || spellLevel != level) return;

        // 持续类不自动释放
        if (spell.getCastType() == CastType.CONTINUOUS) return;

        if (ACTIVE_CASTS.containsKey(player.getUUID())) return;

        MagicData magicData = MagicData.getPlayerMagicData(player);

        // ===== recast 法术：每发命中放一段 =====
        // 若已有活跃 RecastInstance（hasActiveRecast，由 AutoReleaseHandler 传入，避免重复判定），
        // 本次命中直接放链中下一段；不耗充能、不要求充能满。
        if (hasActiveRecast) {
            if (magicData.isCasting()) return;               // 上一段 recast 尚未结算，等下一段命中
            if (AUTO_RECAST_GAP_TICKS > 0) {                 // 最小间隔接口（默认 0 = 无额外间隔）
                int now = player.level().getServer().getTickCount();
                Integer last = LAST_RECYCLE_TICK.get(player.getUUID());
                if (last != null && now - last < AUTO_RECAST_GAP_TICKS) return;
                LAST_RECYCLE_TICK.put(player.getUUID(), now);
            }
            handleRecastCast(player, spell, spellLevel, magicData, gunStack);
            return;
        }

        // 首次施放：充能满门控
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null || !ModChargeData.isFull(magazine)) return;

        Map<UpgradeOrbType, Integer> orbs = MagazineSpellHelper.getAllUpgradeOrbs(gunStack);
        if (!orbs.isEmpty()) {
            applyTemporaryAttributes(player, orbs);
        }
        BraceHandler.applySpellCastBonus(player, gunStack);

        // 目标：命中实体优先，直接设为法术目标；为空则交由 handler 内部自学习走准星回退
        if (targetHint != null) {
            GunCastTargetResolver.setTargetCastData(player, targetHint);
        }

        try {
            CastType ct = spell.getCastType();
            if (ct == CastType.LONG) {
                handleLongCast(player, spell, spellLevel, magicData, gunStack, orbs, ConsumeMode.AUTO);
            } else {
                // INSTANT（CONTINUOUS 已在上方返回）
                handleInstantCast(player, spell, spellLevel, magicData, gunStack, orbs, ConsumeMode.AUTO);
            }
        } catch (Exception e) {
            ArcaneMag.LOGGER.error("auto_release cast failed for spell {}: {}",
                spell.getSpellResource(), e.getMessage(), e);
            forceCleanup(player, magicData, orbs);
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
        BraceHandler.removeSpellCastBonus(player);
    }

    // 过载加成

    /**
     * 应用过载法术强度加成。
     * 倍率由弹匣 charge_overdrive 附魔等级决定：
     * multiplier = 1.0 + level × (config - 1.0)
     * 例：config=1.5, level=2 → multiplier=2.0 (+100%)
     */
    public static void applyOverdriveBonus(ServerPlayer player, ItemStack magazine) {
        int level = EnchantmentRegistry.CHARGE_OVERDRIVE.get().levelOnMagazine(magazine);
        if (level <= 0) return;

        double step = ArcaneMagConfig.CHARGE_OVERDRIVE_SPELL_POWER_PER_STACK.get() - 1.0;
        double amount = level * step; // e.g. level 2, step 0.5 → amount 1.0 = +100%

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

    /**
     * 轻量清理：施法前校验失败时调用（尚未 initiateCast）
     */
    private static void cleanupCast(ServerPlayer player, MagicData magicData, Map<UpgradeOrbType, Integer> orbs) {
        removeTemporaryAttributes(player, orbs);
        magicData.setAdditionalCastData(null);
    }

    /**
     * 强制全量清理：施法执行中异常时调用，保证玩家不卡在 isCasting 状态。
     * 所有操作幂等，可安全重复调用。
     */
    static void forceCleanup(ServerPlayer player, MagicData magicData, Map<UpgradeOrbType, Integer> orbs) {
        ACTIVE_CASTS.remove(player.getUUID());
        if (magicData.isCasting()) {
            magicData.resetCastingState();
        }
        removeTemporaryAttributes(player, orbs);
        removeOverdriveBonus(player);
        magicData.setAdditionalCastData(null);
        ItemStack mag = MagazineSpellHelper.getMagazineAttachment(player.getMainHandItem());
        if (mag != null) ModChargeData.clearOverdrive(mag);
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
