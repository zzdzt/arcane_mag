package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.DryManaFrenzyEnchantment;
import com.zzdzt.arcanemag.gun.GunPropertyContext;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * dry_mana_frenzy（涸法狂击）触发处理器。
 *
 * 状态机：每 tick 检测玩家 IS 法力百分比，落入配置区间 [min, max] 时 active=true。
 * 状态翻转时手动 refresh 枪属性缓存（性能优化：避免每 tick 重算）。
 * AttachmentPropertyEvent 触发时若 active 且枪上刻有本附魔，应用伤害+射速增益。
 *
 * 双端维护 active 状态：服务端权威结算命中，客户端用于枪属性 UI 显示。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class DryManaFrenzyHandler {

    private DryManaFrenzyHandler() {}

    /** 服务端 active 状态 */
    private static final Map<UUID, Boolean> SERVER_ACTIVE = new HashMap<>();
    /** 客户端 active 状态（单人 client/server 共享 JVM，必须隔离） */
    private static final Map<UUID, Boolean> CLIENT_ACTIVE = new HashMap<>();

    /** 当前玩家是否处于涸法狂击激活态（供 AttachmentPropertyEvent 查询） */
    public static boolean isActive(Player player) {
        Map<UUID, Boolean> map = player.level().isClientSide ? CLIENT_ACTIVE : SERVER_ACTIVE;
        return map.getOrDefault(player.getUUID(), false);
    }

    // ==================== tick 检测法力区间 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        boolean clientSide = player.level().isClientSide;

        // 客户端仅当主手是枪时才检测（非持枪不刷新枪缓存，省性能）
        ItemStack mainHand = player.getMainHandItem();
        boolean isGun = !mainHand.isEmpty() && mainHand.getItem() instanceof IGun;
        if (clientSide && !isGun) {
            // 非枪时若曾 active 则翻转为 inactive
            Map<UUID, Boolean> map = CLIENT_ACTIVE;
            if (map.remove(player.getUUID()) != null) {
                // 无枪可刷新，仅清状态
            }
            return;
        }
        // 服务端也仅在持枪时检测（无枪时增益无意义）
        if (!clientSide && !isGun) {
            if (SERVER_ACTIVE.remove(player.getUUID()) != null) {
                // 无枪无操作
            }
            return;
        }

        // 法力百分比
        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData == null) return;
        double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        if (maxMana <= 0) return;
        double manaPercent = magicData.getMana() / maxMana;  // 0.0 ~ 1.0+

        double minPct = ArcaneMagConfig.DRY_MANA_FRENZY_MANA_MIN_PERCENT.get() / 100.0;
        double maxPct = ArcaneMagConfig.DRY_MANA_FRENZY_MANA_MAX_PERCENT.get() / 100.0;

        boolean nowActive = manaPercent >= minPct && manaPercent <= maxPct;

        Map<UUID, Boolean> map = clientSide ? CLIENT_ACTIVE : SERVER_ACTIVE;
        boolean wasActive = map.getOrDefault(player.getUUID(), false);
        if (wasActive != nowActive) {
            map.put(player.getUUID(), nowActive);
            // 状态翻转：刷新枪属性缓存
            if (player instanceof ServerPlayer sp) {
                refreshGunCache(sp);
            } else {
                refreshGunCacheClient(player);
            }
        }
    }

    // ==================== 枪属性应用 ====================

    @SubscribeEvent
    public static void onAttachmentPropertyEvent(AttachmentPropertyEvent event) {
        LivingEntity shooter = GunPropertyContext.getShooter();
        if (!(shooter instanceof Player player)) return;
        if (!isActive(player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return;

        int level = EnchantmentRegistry.DRY_MANA_FRENZY.get().levelOnGun(gunStack);
        if (level <= 0) return;

        applyDryManaFrenzy(event.getCacheProperty(), level);
    }

    private static void applyDryManaFrenzy(AttachmentCacheProperty cache, int level) {
        float damageMul = DryManaFrenzyEnchantment.damageMultiplier(level);
        float rpmMul    = DryManaFrenzyEnchantment.rpmMultiplier();
        modifyInteger(cache, GunProperties.ROUNDS_PER_MINUTE, v -> Math.max(1, (int) (v * rpmMul)));
        modifyDamage(cache, damageMul);
    }

    // ==================== 工具 ====================

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

    private static void refreshGunCache(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    private static void refreshGunCacheClient(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    // ==================== 生命周期清理 ====================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        SERVER_ACTIVE.remove(uuid);
        CLIENT_ACTIVE.remove(uuid);
    }
}
