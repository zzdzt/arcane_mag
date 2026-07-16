package com.zzdzt.arcanemag.event;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.zzdzt.arcanemag.effect.GunEnhanceEffect;
import com.zzdzt.arcanemag.gun.GunPropertyContext;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.registry.EffectRegistry;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.UpgradeOrbType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "arcane_mag")
public class GunPropertyEventHandler {

    private static final Map<UUID, Boolean> LAST_GUN_ENHANCE_STATE = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_TACTICAL_DASH_STATE = new HashMap<>();
    private static final Map<UUID, Map<UpgradeOrbType, Integer>> ACTIVE_ORBS = new HashMap<>();

    @SubscribeEvent
    public static void onAttachmentPropertyEvent(AttachmentPropertyEvent event) {
        LivingEntity shooter = GunPropertyContext.getShooter();
        if (shooter == null) return;

        MobEffectInstance gunEnhance = shooter.getEffect(EffectRegistry.GUN_ENHANCE.get());
        if (gunEnhance != null) {
            applyGunEnhance(event.getCacheProperty(), gunEnhance.getAmplifier() + 1);
        }

        MobEffectInstance tacticalDash = shooter.getEffect(EffectRegistry.TACTICAL_DASH.get());
        if (tacticalDash != null) {
            applyTacticalDash(event.getCacheProperty(), tacticalDash.getAmplifier() + 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();

        boolean hasGunEnhanceNow = player.hasEffect(EffectRegistry.GUN_ENHANCE.get());
        boolean hadGunEnhanceLastTick = LAST_GUN_ENHANCE_STATE.getOrDefault(uuid, false);
        if (hadGunEnhanceLastTick != hasGunEnhanceNow) {
            refreshGunCache(player);
        }
        LAST_GUN_ENHANCE_STATE.put(uuid, hasGunEnhanceNow);

        boolean hasTacticalDashNow = player.hasEffect(EffectRegistry.TACTICAL_DASH.get());
        boolean hadTacticalDashLastTick = LAST_TACTICAL_DASH_STATE.getOrDefault(uuid, false);
        if (hadTacticalDashLastTick != hasTacticalDashNow) {
            refreshGunCache(player);
        }
        LAST_TACTICAL_DASH_STATE.put(uuid, hasTacticalDashNow);

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
        LAST_GUN_ENHANCE_STATE.remove(uuid);
        LAST_TACTICAL_DASH_STATE.remove(uuid);

        if (event.getEntity() instanceof ServerPlayer player) {
            Map<UpgradeOrbType, Integer> orbs = ACTIVE_ORBS.remove(uuid);
            if (orbs != null) {
                SpellCastHandler.removeTemporaryAttributes(player, orbs);
            }
        }
    }

    private static void refreshGunCache(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    private static void applyGunEnhance(AttachmentCacheProperty cache, int level) {
        float damageMul      = 1.0f + level * 0.20f;
        float rpmMul         = 1.0f + level * 0.15f;
        float adsMul         = 1.0f + level * 0.15f;
        float inaccuracyMul  = 1.0f / (1.0f + level * 0.15f);

        modifyFloat(cache, GunProperties.ADS_TIME, v -> v / adsMul);
        modifyInteger(cache, GunProperties.ROUNDS_PER_MINUTE, v -> Math.max(1, (int) (v * rpmMul)));
        modifyDamage(cache, damageMul);
        modifyInaccuracy(cache, inaccuracyMul);
    }

    private static void modifyFloat(AttachmentCacheProperty cache, com.tacz.guns.api.GunProperty<Float> property, Function<Float, Float> func) {
        Float val = cache.getCache(property);
        if (val != null) {
            cache.setCache(property, func.apply(val));
        }
    }

    private static void modifyInteger(AttachmentCacheProperty cache, com.tacz.guns.api.GunProperty<Integer> property, Function<Integer, Integer> func) {
        Integer val = cache.getCache(property);
        if (val != null) {
            cache.setCache(property, func.apply(val));
        }
    }

    private static void modifyDamage(AttachmentCacheProperty cache, float multiplier) {
        LinkedList<ExtraDamage.DistanceDamagePair> damage = cache.getCache(GunProperties.DAMAGE);
        if (damage == null || damage.isEmpty()) return;

        LinkedList<ExtraDamage.DistanceDamagePair> newDamage = new LinkedList<>();
        for (ExtraDamage.DistanceDamagePair pair : damage) {
            newDamage.add(new ExtraDamage.DistanceDamagePair(
                    pair.getDistance(),
                    pair.getDamage() * multiplier
            ));
        }
        cache.setCache(GunProperties.DAMAGE, newDamage);
    }

    private static void applyTacticalDash(AttachmentCacheProperty cache, int level) {
        float headshotAdd = 0.5f + (level - 1) * 0.1f;
        modifyFloat(cache, GunProperties.HEADSHOT_MULTIPLIER, v -> v + headshotAdd);
    }

    private static void modifyInaccuracy(AttachmentCacheProperty cache, float multiplier) {
        Map<InaccuracyType, Float> inaccuracy = cache.getCache(GunProperties.INACCURACY);
        if (inaccuracy == null || inaccuracy.isEmpty()) return;

        HashMap<InaccuracyType, Float> newInaccuracy = new HashMap<>();
        for (Map.Entry<InaccuracyType, Float> entry : inaccuracy.entrySet()) {
            newInaccuracy.put(entry.getKey(), entry.getValue() * multiplier);
        }
        cache.setCache(GunProperties.INACCURACY, newInaccuracy);
    }
}
