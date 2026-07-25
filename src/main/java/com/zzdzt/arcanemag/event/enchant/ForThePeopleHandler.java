package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.ForThePeopleEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * for_the_people（我为人人）触发处理器。
 *
 * 状态机：6 秒窗口内累积爆头命中的不同敌人 UUID，达到阈值（默认 3）后触发
 * 10 秒 spell_power 加成。可刷新：触发即清零计数、续满 buff 时长。
 *
 * 服务端单边维护 {@link #TRACKERS}（与 ArcaneAimGunHandler.ACTIVE_SESSIONS 同模式），
 * modifier 用独立固定 UUID（与 TEMP_MODIFIER_UUID / OVERDRIVE_MODIFIER_UUID 互不干扰），
 * 本 handler 自管生命周期。
 *
 * 事件双端派发，仅以服务端为准（参考 ArcaneFocusHandler）。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class ForThePeopleHandler {

    private ForThePeopleHandler() {}

    /** 临时 spell_power modifier 的固定 UUID（幂等 apply/remove 的关键） */
    private static final UUID MODIFIER_UUID =
        UUID.fromString("7a3c1f5e-2b4d-4a6c-9e8b-0d1f2a3b4c5d");

    /** 服务端按玩家 UUID 存状态 */
    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    /**
     * 可变状态容器（每玩家一份）。
     * windowExpiryTick = 0 表示无活跃窗口；buffExpiryTick = 0 表示无活跃 buff。
     */
    private static final class Tracker {
        final Set<UUID> headshotTargets = new HashSet<>();
        long windowExpiryTick = 0;
        long buffExpiryTick = 0;

        void resetWindow() {
            headshotTargets.clear();
            windowExpiryTick = 0;
        }
    }

    // ==================== 爆头事件 ====================

    @SubscribeEvent
    public static void onEntityHurtByGunPre(EntityHurtByGunEvent.Pre event) {
        // 仅服务端：计数与 buff 均以服务端为准
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        // 仅爆头命中
        if (!event.isHeadShot()) return;

        // 攻击者必须是玩家
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        // 主手必须是枪且刻有 for_the_people
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;
        int level = EnchantmentRegistry.FOR_THE_PEOPLE.get().levelOnGun(gunStack);
        if (level <= 0) return;   // 当前枪无此附魔：不计入，但不主动清窗口

        // 被命中实体必须存在
        Entity hurtEntity = event.getHurtEntity();
        if (hurtEntity == null) return;

        long now = player.level().getGameTime();
        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), k -> new Tracker());

        // 窗口过期：先清空再以本次爆头开新窗口
        if (tracker.windowExpiryTick != 0 && now > tracker.windowExpiryTick) {
            tracker.resetWindow();
        }
        if (tracker.windowExpiryTick == 0) {
            tracker.windowExpiryTick = now + ArcaneMagConfig.FOR_THE_PEOPLE_WINDOW_TICKS.get();
        }

        // 加入目标 UUID（HashSet 自动去重）
        tracker.headshotTargets.add(hurtEntity.getUUID());

        // 达到阈值 → 触发 buff + 清零计数
        int required = ArcaneMagConfig.FOR_THE_PEOPLE_REQUIRED_HEADSHOTS.get();
        if (tracker.headshotTargets.size() >= required) {
            triggerBuff(player, level, now);
            tracker.resetWindow();   // 触发后立即清零爆头计数
        }
    }

    // ==================== 触发 / 刷新 buff ====================

    private static void triggerBuff(ServerPlayer player, int level, long now) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null) return;

        applySpellPowerBuff(player, level);
        tracker.buffExpiryTick = now + ArcaneMagConfig.FOR_THE_PEOPLE_BUFF_DURATION_TICKS.get();
        spawnTriggerBurst(player);
    }

    // ==================== 粒子 ====================

    /** 触发瞬间：玩家中心 END_ROD 爆发 */
    private static void spawnTriggerBurst(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 1.0, player.getZ(),
            30, 0.4, 0.6, 0.4, 0.15);
        level.sendParticles(ParticleTypes.GLOW,
            player.getX(), player.getY() + 1.0, player.getZ(),
            15, 0.3, 0.5, 0.3, 0.05);
    }

    private static void applySpellPowerBuff(ServerPlayer player, int level) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance == null) return;

        double amount = ForThePeopleEnchantment.spellPowerBonus(level);
        instance.removeModifier(MODIFIER_UUID);   // 幂等：刷新时按当前等级重算金额
        instance.addTransientModifier(new AttributeModifier(
            MODIFIER_UUID, "ArcaneMagForThePeople", amount,
            AttributeModifier.Operation.MULTIPLY_BASE
        ));
    }

    private static void removeSpellPowerBuff(ServerPlayer player) {
        Attribute spellPower = BuiltInRegistries.ATTRIBUTE.get(
            new ResourceLocation("irons_spellbooks", "spell_power"));
        if (spellPower == null) return;
        AttributeInstance instance = player.getAttribute(spellPower);
        if (instance != null) {
            instance.removeModifier(MODIFIER_UUID);
        }
    }

    // ==================== tick 倒计时 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null) return;

        long now = player.level().getGameTime();

        // buff 到期：移除 modifier
        if (tracker.buffExpiryTick != 0 && now > tracker.buffExpiryTick) {
            removeSpellPowerBuff(player);
            tracker.buffExpiryTick = 0;
        }

        // 窗口到期：清理过期窗口（内存回收）
        if (tracker.windowExpiryTick != 0 && now > tracker.windowExpiryTick) {
            tracker.resetWindow();
        }

        // 双零：移除整个 tracker，避免 Map 无限增长
        if (tracker.buffExpiryTick == 0 && tracker.windowExpiryTick == 0) {
            TRACKERS.remove(player.getUUID());
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
        // 重生后新实体不继承 transient modifier，但防御性清状态
        if (event.getEntity() instanceof ServerPlayer player) {
            TRACKERS.remove(player.getUUID());
        }
    }

    private static void cleanup(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        Tracker tracker = TRACKERS.remove(player.getUUID());
        if (tracker != null && tracker.buffExpiryTick != 0) {
            removeSpellPowerBuff(player);   // 防御性移除
        }
    }
}
