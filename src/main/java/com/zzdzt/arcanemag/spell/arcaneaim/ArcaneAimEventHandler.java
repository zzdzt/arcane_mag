package com.zzdzt.arcanemag.spell.arcaneaim;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.effect.ArcaneAimEffect;
import com.zzdzt.arcanemag.registry.EffectRegistry;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ArcaneAimEventHandler {
    private static final Map<UUID, Item> LAST_MAIN_HAND_ITEM = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        UUID uuid = player.getUUID();
        Item currentItem = player.getMainHandItem().getItem();
        Item lastItem = LAST_MAIN_HAND_ITEM.getOrDefault(uuid, null);

        // 只检测物品类型变化
        if (currentItem != lastItem) {
            if (player.hasEffect(EffectRegistry.ARCANE_AIM.get())) {
                player.removeEffect(EffectRegistry.ARCANE_AIM.get());
                player.getPersistentData().remove(ArcaneAimEffect.AIM_TARGET_TAG);
            }
        }

         if (!player.hasEffect(EffectRegistry.ARCANE_AIM.get())) {
            if (player.getPersistentData().hasUUID(ArcaneAimEffect.AIM_TARGET_TAG)) {
                player.getPersistentData().remove(ArcaneAimEffect.AIM_TARGET_TAG);
            }
        } else {
            // Buff 还在，检查目标是否存活 + 持续生成标记粒子
            UUID targetUUID = player.getPersistentData().getUUID(ArcaneAimEffect.AIM_TARGET_TAG);
            if (targetUUID != null) {
                LivingEntity target = findTargetByUUID(player, targetUUID);

                if (target == null || !target.isAlive() || player.distanceToSqr(target) > 4096) {
                    // 目标死亡或消失，移除 Buff
                    player.removeEffect(EffectRegistry.ARCANE_AIM.get());
                    player.getPersistentData().remove(ArcaneAimEffect.AIM_TARGET_TAG);
                } else {
                    // 在目标头顶生成微弱粒子
                    if (player.level().getGameTime() % 20 == 0 && player.level() instanceof ServerLevel serverLevel) {
                        Vec3 pos = target.position().add(0, target.getBbHeight() + 0.3, 0);
                        // 只让施法玩家自己看到持续标记粒子
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverLevel.sendParticles(
                                serverPlayer,
                                net.minecraft.core.particles.ParticleTypes.WITCH,
                                true,
                                pos.x, pos.y, pos.z,
                                2,      // 少量粒子
                                0.15, 0.1, 0.15,  // 小范围扩散
                                0.02    // 极慢速度
                            );
                        }
                    }
                }
            }
        }

        LAST_MAIN_HAND_ITEM.put(uuid, currentItem);
    }

    @Nullable
    private static LivingEntity findTargetByUUID(Player player, UUID targetUUID) {
        if (player.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(targetUUID);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MAIN_HAND_ITEM.remove(event.getEntity().getUUID());
    }
}