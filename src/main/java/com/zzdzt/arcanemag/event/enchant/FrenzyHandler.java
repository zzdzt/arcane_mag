package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.ArcaneFrenzyEnchantment;
import com.zzdzt.arcanemag.enchant.FireFrenzyEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * fire_frenzy / arcane_frenzy（火力狂热 / 奥术狂热）共享触发处理器。
 *
 * 共享战斗计时状态机（服务端权威）：
 *   - "战斗行为" = 开火命中（EntityHurtByGunEvent.Post）或受到 LivingEntity 来源伤害（LivingHurtEvent）
 *   - 每次战斗行为刷新 lastCombatTick；若 combatStartTick==0 则同时开窗
 *   - 持续战斗满 FRENZY_COMBAT_WINDOW_TICKS（默认 240t/12s）后激活对应附魔增益
 *   - 激活后 FRENZY_TIMEOUT_TICKS（默认 60t/3s）内无战斗行为 → 移除增益并重置
 *
 * 两附魔独立激活，互不依赖：
 *   - fire_frenzy：走 enchant 乘区（getActiveDamageBonusPercent 供 GunEnchantPropertyHandler 查询）
 *   - arcane_frenzy：走 spell_power AttributeModifier（MULTIPLY_BASE，本 handler 自管生命周期）
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class FrenzyHandler {

    private FrenzyHandler() {}

    /** arcane_frenzy 的 spell_power modifier 固定 UUID（幂等 apply/remove） */
    private static final UUID ARCANE_FRENZY_MODIFIER_UUID =
        UUID.fromString("8b4d2e6f-3c5e-4b7d-af9c-1e2a3b4c5d6e");

    /** 服务端按玩家 UUID 存共享战斗计时状态 */
    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    /**
     * 共享战斗计时容器。
     * combatStartTick = 0 表示未在战斗窗口内。
     */
    private static final class Tracker {
        long combatStartTick = 0;
        long lastCombatTick = 0;
        boolean fireFrenzyActive = false;
        boolean arcaneFrenzyActive = false;
    }

    // ==================== 战斗行为事件 ====================

    /** 开火命中 → 战斗行为 */
    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;
        // 任意枪械命中均算战斗行为（不强制要求枪上有狂热附魔，便于开窗后切换）
        registerCombat(player);
    }

    /** 受到 LivingEntity 来源伤害 → 战斗行为 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 仅"被攻击"算战斗行为（跌落/溺水/魔法自伤等不计）
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;
        registerCombat(player);
    }

    private static void registerCombat(ServerPlayer player) {
        long now = player.level().getGameTime();
        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), k -> new Tracker());
        if (tracker.combatStartTick == 0) {
            tracker.combatStartTick = now;
        }
        tracker.lastCombatTick = now;
    }

    // ==================== tick 状态机 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null) return;

        long now = player.level().getGameTime();

        // 超时：3s 内无战斗行为 → 移除所有增益 + 重置
        if (now - tracker.lastCombatTick > ArcaneMagConfig.FRENZY_TIMEOUT_TICKS.get()) {
            deactivateAll(player, tracker);
            TRACKERS.remove(player.getUUID());
            return;
        }

        // 持续战斗满窗口 → 检查激活
        if (tracker.combatStartTick != 0
            && now - tracker.combatStartTick >= ArcaneMagConfig.FRENZY_COMBAT_WINDOW_TICKS.get()) {
            tryActivate(player, tracker);
        }
    }

    private static void tryActivate(ServerPlayer player, Tracker tracker) {
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        // 火力狂热
        if (!tracker.fireFrenzyActive) {
            int level = EnchantmentRegistry.FIRE_FRENZY.get().levelOnGun(gunStack);
            if (level > 0) {
                tracker.fireFrenzyActive = true;
                refreshGunCache(player);
            }
        }

        // 奥术狂热
        if (!tracker.arcaneFrenzyActive) {
            int level = EnchantmentRegistry.ARCANE_FRENZY.get().levelOnGun(gunStack);
            if (level > 0) {
                tracker.arcaneFrenzyActive = true;
                applySpellPowerBuff(player, level);
            }
        }
    }

    private static void deactivateAll(ServerPlayer player, Tracker tracker) {
        if (tracker.fireFrenzyActive) {
            tracker.fireFrenzyActive = false;
            refreshGunCache(player);
        }
        if (tracker.arcaneFrenzyActive) {
            tracker.arcaneFrenzyActive = false;
            removeSpellPowerBuff(player);
        }
    }

    // ==================== spell_power modifier ====================

    private static void applySpellPowerBuff(ServerPlayer player, int level) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance == null) return;

        double amount = ArcaneFrenzyEnchantment.spellPowerBonus(level);
        instance.removeModifier(ARCANE_FRENZY_MODIFIER_UUID);
        instance.addTransientModifier(new AttributeModifier(
            ARCANE_FRENZY_MODIFIER_UUID, "ArcaneMagArcaneFrenzy", amount,
            AttributeModifier.Operation.MULTIPLY_BASE
        ));
    }

    private static void removeSpellPowerBuff(ServerPlayer player) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance != null) {
            instance.removeModifier(ARCANE_FRENZY_MODIFIER_UUID);
        }
    }

    // ==================== 查询（供 enchant 乘区统一处理器调用） ====================

    /** 火力狂热是否激活且当前枪有附魔 → 返回伤害加成百分比 */
    public static double getFireFrenzyDamageBonusPercent(net.minecraft.world.entity.player.Player player) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || !tracker.fireFrenzyActive) return 0;
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return 0;
        int level = EnchantmentRegistry.FIRE_FRENZY.get().levelOnGun(gunStack);
        return level <= 0 ? 0 : FireFrenzyEnchantment.damageBonusPercent(level);
    }

    // ==================== 工具 ====================

    private static void refreshGunCache(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    // ==================== 生命周期清理 ====================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(EntityTravelToDimensionEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TRACKERS.remove(player.getUUID());
        }
    }

    private static void cleanup(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        Tracker tracker = TRACKERS.remove(player.getUUID());
        if (tracker != null && tracker.arcaneFrenzyActive) {
            removeSpellPowerBuff(player);   // 防御性移除
        }
    }
}
