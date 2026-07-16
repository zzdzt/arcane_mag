package com.zzdzt.arcanemag.event;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.spell.imbuedbullet.ImbuedBulletState;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ArcaneMagEvents {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ImbuedBulletState.clearAll(player);

        if (SpellCastHandler.isPlayerCasting(player)) {
            SpellCastHandler.abortCastForPlayer(player);
        }
        removeAllTemporaryAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ImbuedBulletState.clearAll(player);

        if (SpellCastHandler.isPlayerCasting(player)) {
            SpellCastHandler.abortCastForPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ImbuedBulletState.clearAll(player);

        if (SpellCastHandler.isPlayerCasting(player)) {
            SpellCastHandler.abortCastForPlayer(player);
        }
        removeAllTemporaryAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        removeAllTemporaryAttributes(player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            removeAllTemporaryAttributes(newPlayer);
        }
    }

    private static void removeAllTemporaryAttributes(ServerPlayer player) {
        boolean cleaned = false;

        for (UpgradeOrbType type : UpgradeOrbType.values()) {
            Attribute attribute = type.getAttribute();
            if (attribute == null) continue;

            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;

            // 使用 SpellCastHandler 中的统一 UUID
            if (instance.getModifier(SpellCastHandler.TEMP_MODIFIER_UUID) != null) {
                instance.removeModifier(SpellCastHandler.TEMP_MODIFIER_UUID);
                cleaned = true;
            }
        }

        if (cleaned) {
            ArcaneMag.LOGGER.debug("Cleaned up temporary attributes for player {}", 
                player.getName().getString());
        }
    }
}