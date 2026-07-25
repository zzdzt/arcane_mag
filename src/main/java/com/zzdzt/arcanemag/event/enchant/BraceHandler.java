package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.BraceEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;

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
 * brace（蓄势）触发处理器。
 *
 * 双蓄势状态机（服务端权威）：
 *   - 枪械蓄势 gunStacks：EntityHurtByGunEvent.Pre 消耗 → baseAmount × (1 + stacks × 3%)
 *   - 法术蓄势 spellStacks：applySpellCastBonus 消耗 → spell_power +stacks × 3%
 *
 * 积累：非战斗状态（3s 无战斗行为）后每秒 +1，上限 = level × 5。
 * 切枪：mainHandItem 引用变化 → 全部清零。
 * 两种蓄势独立消耗，互不干扰。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class BraceHandler {

    private BraceHandler() {}

    /** 法术蓄势 spell_power modifier UUID（独立于 TEMP/OVERDRIVE） */
    private static final UUID BRACE_MODIFIER_UUID =
        UUID.fromString("b3c4d5e6-f7a8-9012-bcde-f23456789012");

    private static final class Tracker {
        int gunStacks = 0;
        int spellStacks = 0;
        long lastCombatTick = 0;
        int accumulateCounter = 0;
        ItemStack gunRef = ItemStack.EMPTY;
    }

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    // ==================== 战斗行为注册 ====================

    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;
        registerCombat(player);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;
        registerCombat(player);
    }

    private static void registerCombat(ServerPlayer player) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker != null) {
            tracker.lastCombatTick = player.level().getGameTime();
        }
    }

    // ==================== tick：切枪检测 + 蓄势积累 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        int level = EnchantmentRegistry.BRACE.get().levelOnGun(gunStack);
        if (level <= 0) return;

        long now = player.level().getGameTime();
        UUID uuid = player.getUUID();
        Tracker tracker = TRACKERS.computeIfAbsent(uuid, k -> new Tracker());

        // 切枪检测：引用变化 → 全部清零
        if (tracker.gunRef != gunStack) {
            tracker.gunStacks = 0;
            tracker.spellStacks = 0;
            tracker.accumulateCounter = 0;
            tracker.gunRef = gunStack;
        }

        // 非战斗状态判断
        int timeout = ArcaneMagConfig.BRACE_COMBAT_TIMEOUT_TICKS.get();
        boolean nonCombat = tracker.lastCombatTick == 0
            || (now - tracker.lastCombatTick) > timeout;

        if (!nonCombat) {
            tracker.accumulateCounter = 0;
            return;
        }

        // 积累：每 interval tick +1
        int interval = ArcaneMagConfig.BRACE_ACCUMULATE_INTERVAL_TICKS.get();
        tracker.accumulateCounter++;
        if (tracker.accumulateCounter >= interval) {
            tracker.accumulateCounter = 0;
            int max = BraceEnchantment.getMaxStacks(level);
            if (tracker.gunStacks < max) tracker.gunStacks++;
            if (tracker.spellStacks < max) tracker.spellStacks++;
        }
    }

    // ==================== 枪械蓄势消耗 ====================

    @SubscribeEvent
    public static void onEntityHurtByGunPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.gunStacks <= 0) return;

        // 确认当前枪仍有蓄势附魔
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;
        if (EnchantmentRegistry.BRACE.get().levelOnGun(gunStack) <= 0) return;

        // 计算加成
        double bonusPercent = ArcaneMagConfig.BRACE_DAMAGE_PER_STACK.get();
        double multiplier = 1.0 + tracker.gunStacks * (bonusPercent / 100.0);
        event.setBaseAmount(event.getBaseAmount() * (float) multiplier);

        // 消耗枪械蓄势
        tracker.gunStacks = 0;
    }

    // ==================== 法术蓄势消耗（供 SpellCastHandler 调用） ====================

    /**
     * 施法前调用：读取法术蓄势层数，施加 spell_power modifier，清零层数。
     * 由 SpellCastHandler.applyTemporaryAttributes 调用点旁调用。
     */
    public static void applySpellCastBonus(ServerPlayer player, ItemStack gunStack) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.spellStacks <= 0) return;

        // 确认当前枪仍有蓄势附魔
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;
        if (EnchantmentRegistry.BRACE.get().levelOnGun(gunStack) <= 0) return;

        double bonusPercent = ArcaneMagConfig.BRACE_DAMAGE_PER_STACK.get();
        double amount = tracker.spellStacks * (bonusPercent / 100.0);

        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance == null) return;

        instance.removeModifier(BRACE_MODIFIER_UUID);
        instance.addTransientModifier(new AttributeModifier(
            BRACE_MODIFIER_UUID, "ArcaneMagBrace", amount,
            AttributeModifier.Operation.MULTIPLY_BASE
        ));

        // 消耗法术蓄势
        tracker.spellStacks = 0;
        // 施法也是战斗行为
        tracker.lastCombatTick = player.level().getGameTime();
    }

    /**
     * 施法后清理：移除 spell_power modifier。
     * 由 SpellCastHandler.removeTemporaryAttributes 内部调用。
     */
    public static void removeSpellCastBonus(ServerPlayer player) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance != null) {
            instance.removeModifier(BRACE_MODIFIER_UUID);
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
        if (tracker != null && tracker.spellStacks > 0) {
            removeSpellCastBonus(player);
        }
    }
}
