package com.zzdzt.arcanemag.event.spell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.zzdzt.arcanemag.gun.GunPropertyContext;
import com.zzdzt.arcanemag.spell.ArcaneMagSpell;
import com.zzdzt.arcanemag.spell.registry.EffectRegistry;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 法术效果对枪械属性的修改（spell 乘区）。
 *
 * 从 GunPropertyEventHandler 拆分而来，避免 core 反向依赖 spell 的 EffectRegistry。
 * 处理 GunEnhance / TacticalDash 效果对枪械属性的影响。
 */
@Mod.EventBusSubscriber(modid = ArcaneMagSpell.MODID)
public class SpellGunPropertyHandler {

    private static final Map<UUID, Boolean> LAST_GUN_ENHANCE_STATE = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_TACTICAL_DASH_STATE = new HashMap<>();
    private static final Set<UUID> PENDING_REFRESH = new HashSet<>();
    private static final Map<UUID, Boolean> CLIENT_LAST_GUN_ENHANCE = new HashMap<>();
    private static final Map<UUID, Boolean> CLIENT_LAST_TACTICAL_DASH = new HashMap<>();

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
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        markPendingIfRelevant(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        markPendingIfRelevant(event.getEntity(), event.getEffectInstance());
    }

    private static void markPendingIfRelevant(LivingEntity entity, MobEffectInstance instance) {
        if (instance == null || !(entity instanceof Player player)) return;
        var effect = instance.getEffect();
        if (effect == EffectRegistry.GUN_ENHANCE.get() || effect == EffectRegistry.TACTICAL_DASH.get()) {
            PENDING_REFRESH.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        UUID uuid = player.getUUID();
        boolean clientSide = player.level().isClientSide;

        Map<UUID, Boolean> gunEnhanceState = clientSide ? CLIENT_LAST_GUN_ENHANCE : LAST_GUN_ENHANCE_STATE;
        Map<UUID, Boolean> tacticalDashState = clientSide ? CLIENT_LAST_TACTICAL_DASH : LAST_TACTICAL_DASH_STATE;

        if (PENDING_REFRESH.remove(uuid)) {
            refreshGunCache(player);
        }

        boolean hasGunEnhanceNow = player.hasEffect(EffectRegistry.GUN_ENHANCE.get());
        boolean hadGunEnhanceLastTick = gunEnhanceState.getOrDefault(uuid, false);
        if (hadGunEnhanceLastTick != hasGunEnhanceNow) {
            refreshGunCache(player);
        }
        gunEnhanceState.put(uuid, hasGunEnhanceNow);

        boolean hasTacticalDashNow = player.hasEffect(EffectRegistry.TACTICAL_DASH.get());
        boolean hadTacticalDashLastTick = tacticalDashState.getOrDefault(uuid, false);
        if (hadTacticalDashLastTick != hasTacticalDashNow) {
            refreshGunCache(player);
        }
        tacticalDashState.put(uuid, hasTacticalDashNow);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        LAST_GUN_ENHANCE_STATE.remove(uuid);
        LAST_TACTICAL_DASH_STATE.remove(uuid);
        CLIENT_LAST_GUN_ENHANCE.remove(uuid);
        CLIENT_LAST_TACTICAL_DASH.remove(uuid);
        PENDING_REFRESH.remove(uuid);
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
