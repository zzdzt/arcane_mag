package com.zzdzt.arcanemag.event.charge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 法球属性管理（core）。
 *
 * 法术效果对枪械属性的修改（GunEnhance/TacticalDash）已移至
 * {@link com.zzdzt.arcanemag.event.spell.SpellGunPropertyHandler}（spell 模块），
 * 避免 core 反向依赖 spell 的 EffectRegistry。
 */
@Mod.EventBusSubscriber(modid = "arcane_mag")
public class GunPropertyEventHandler {

    private static final Map<UUID, Map<UpgradeOrbType, Integer>> ACTIVE_ORBS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        applyOrbAttributesContinuously((ServerPlayer) player);
    }

    private static void applyOrbAttributesContinuously(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        Map<UpgradeOrbType, Integer> currentOrbs = mainHand.getItem() instanceof IGun
                ? MagazineSpellHelper.getAllUpgradeOrbs(mainHand)
                : Map.of();

        UUID uuid = player.getUUID();
        Map<UpgradeOrbType, Integer> previousOrbs = ACTIVE_ORBS.getOrDefault(uuid, Map.of());

        if (!currentOrbs.equals(previousOrbs)) {
            SpellCastHandler.removeTemporaryAttributes(player, previousOrbs);
            if (!currentOrbs.isEmpty()) {
                SpellCastHandler.applyTemporaryAttributes(player, currentOrbs);
            }
            ACTIVE_ORBS.put(uuid, currentOrbs.isEmpty() ? Map.of() : new HashMap<>(currentOrbs));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();

        if (event.getEntity() instanceof ServerPlayer player) {
            Map<UpgradeOrbType, Integer> orbs = ACTIVE_ORBS.remove(uuid);
            if (orbs != null) {
                SpellCastHandler.removeTemporaryAttributes(player, orbs);
            }
        }
    }
}
