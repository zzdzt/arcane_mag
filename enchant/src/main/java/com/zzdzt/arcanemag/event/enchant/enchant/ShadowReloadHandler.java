package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.ShadowReloadEnchantment;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;

import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * shadow_reload（暗隐装填）触发处理器。
 *
 * 监听 GunReloadEvent（换弹开始），服务端权威：
 *   1. 攻击者玩家主手枪弹匣刻有 shadow_reload
 *   2. 当前 gameTime 超过上次触发 + cooldownTicks
 *   3. 套 ISS TrueInvisibility 效果，时长 = invisDurationTicks
 *   4. 记录 lastTriggerTick
 *
 * CD 表按玩家 UUID 存，登出/死亡/换维清理。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class ShadowReloadHandler {

    private ShadowReloadHandler() {}

    /** 服务端按玩家 UUID 存上次触发 tick；0 = 未触发过 */
    private static final Map<UUID, Long> LAST_TRIGGER = new HashMap<>();

    @SubscribeEvent
    public static void onGunReload(GunReloadEvent event) {
        // 仅服务端：隐身以服务端权威为准
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        // 攻击者必须是服务端玩家
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 主手必须是枪
        ItemStack gunStack = event.getGunItemStack();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        // 枪上弹匣刻有 shadow_reload（仅 1 级）
        int level = EnchantmentRegistry.SHADOW_RELOAD.get().levelOnGun(gunStack);
        if (level <= 0) return;

        long now = player.level().getGameTime();
        long last = LAST_TRIGGER.getOrDefault(player.getUUID(), 0L);
        long cooldown = ShadowReloadEnchantment.cooldownTicks();

        // 内置 CD：上次触发 + cooldown 之前不再触发
        // last == 0 表示从未触发，直接放行
        if (last != 0 && now < last + cooldown) return;

        // 套真隐身
        int duration = ShadowReloadEnchantment.invisDurationTicks();
        player.addEffect(new MobEffectInstance(
            MobEffectRegistry.TRUE_INVISIBILITY.get(),
            duration,
            0,          // amplifier
            false,      // ambient
            true,       // visible（显示粒子）
            true        // showIcon（显示 buff 图标）
        ));

        LAST_TRIGGER.put(player.getUUID(), now);
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
        if (event.getEntity() instanceof ServerPlayer) {
            LAST_TRIGGER.remove(event.getEntity().getUUID());
        }
    }

    private static void cleanup(Entity entity) {
        if (entity == null) return;
        LAST_TRIGGER.remove(entity.getUUID());
    }
}
