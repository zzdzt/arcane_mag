package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.PelletBurstEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * pellet_burst（爆破弹丸）触发处理器。
 *
 * 状态机（服务端权威）：
 *   - 每次枪械命中 +1 计数
 *   - 达到阈值（I=50/II=40/III=30）→ 消耗弹匣法术当前充能的一半，
 *     以命中实体为中心造成范围伤害（受护甲减免，来源=玩家）
 *   - 触发后计数减去阈值（保留余数，霰弹枪一枪多发可连续触发）
 *   - 充能为 0 时不触发，计数保留
 *   - 3 秒未命中 → 计数归零
 *
 * 霰弹枪一枪多发弹丸每次命中都触发 Post 事件，累积效率远高于单发枪。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class PelletBurstHandler {

    private PelletBurstHandler() {}

    private static final class Tracker {
        int hitCount = 0;
        long lastHitTick = 0;
    }

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    // ==================== 命中计数 ====================

    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        int level = EnchantmentRegistry.PELLET_BURST.get().levelOnGun(gunStack);
        if (level <= 0) return;

        // 必须有弹匣法术
        if (MagazineSpellHelper.extractSpell(gunStack) == null) return;

        long now = player.level().getGameTime();
        UUID uuid = player.getUUID();
        Tracker tracker = TRACKERS.computeIfAbsent(uuid, k -> new Tracker());

        // 超时重置
        int timeout = ArcaneMagConfig.PELLET_BURST_TIMEOUT_TICKS.get();
        if (tracker.lastHitTick > 0 && now - tracker.lastHitTick > timeout) {
            tracker.hitCount = 0;
        }
        tracker.lastHitTick = now;
        tracker.hitCount++;

        int threshold = PelletBurstEnchantment.getThreshold(level);
        if (tracker.hitCount < threshold) return;

        // 达到阈值，尝试触发
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        double charge = ModChargeData.getCharge(magazine);
        if (charge <= 0) return;   // 充能不足：不触发，计数保留

        // 消耗一半充能
        double ratio = ArcaneMagConfig.PELLET_BURST_CHARGE_CONSUME_RATIO.get();
        double consumed = charge * ratio;
        ModChargeData.setCharge(magazine, charge - consumed);

        // 计数减去阈值（保留余数）
        tracker.hitCount -= threshold;

        // 计算伤害
        double damage = consumed * ArcaneMagConfig.PELLET_BURST_DAMAGE_COEFFICIENT.get();
        if (damage <= 0) return;

        // 范围爆炸
        Entity center = event.getHurtEntity();
        if (center == null) return;
        explode(player, center, damage);
    }

    // ==================== 范围爆炸 ====================

    private static void explode(ServerPlayer player, Entity center, double damage) {
        ServerLevel level = (ServerLevel) player.level();
        double radius = ArcaneMagConfig.PELLET_BURST_RADIUS.get();

        AABB box = AABB.ofSize(center.position(), radius * 2, radius * 2, radius * 2);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);

        DamageSource source = level.damageSources().playerAttack(player);
        double radiusSq = radius * radius;
        for (LivingEntity entity : entities) {
            if (entity.getId() == player.getId()) continue;   // 排除自己
            if (entity.position().distanceToSqr(center.position()) > radiusSq) continue;
            entity.hurt(source, (float) damage);
        }

        // 爆炸粒子
        level.sendParticles(ParticleTypes.EXPLOSION,
            center.getX(), center.getY() + 1.0, center.getZ(),
            5, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ParticleTypes.GLOW,
            center.getX(), center.getY() + 1.0, center.getZ(),
            10, 0.4, 0.4, 0.4, 0.05);
    }

    // ==================== tick 超时清理 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null) return;

        long now = player.level().getGameTime();
        int timeout = ArcaneMagConfig.PELLET_BURST_TIMEOUT_TICKS.get();
        if (tracker.lastHitTick > 0 && now - tracker.lastHitTick > timeout) {
            TRACKERS.remove(player.getUUID());
        }
    }

    // ==================== 生命周期清理 ====================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            TRACKERS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(EntityTravelToDimensionEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }
}
