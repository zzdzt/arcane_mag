package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.enchant.OverflowEnchantment;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * overflow（充盈）触发处理器。
 *
 * 状态机（服务端权威）：
 *   - 持枪 + 枪上有本附魔 + 弹匣有注魔法术 + ModChargeData.isFull → active，记录 expiryTick
 *   - 重新充满会刷新 expiryTick
 *   - 法术释放（MagicData.isCasting()==true）→ 立即关闭
 *   - 超过 expiryTick → 关闭
 *   - active 状态翻转时 refreshGunCache
 *
 * 性能：状态翻转才 refresh，充满期间每 tick 不刷枪缓存。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class OverflowHandler {

    private OverflowHandler() {}

    /** 每玩家充盈过期 tick；0 表示未激活。 */
    private static final Map<UUID, Long> EXPIRY_TICK = new HashMap<>();

    // ==================== tick 检测充满 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        long now = player.level().getGameTime();
        UUID uuid = player.getUUID();

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            updateActive(player, 0L, now);
            return;
        }

        int level = EnchantmentRegistry.OVERFLOW.get().levelOnGun(gunStack);
        if (level <= 0) {
            updateActive(player, 0L, now);
            return;
        }

        // 弹匣需有注魔法术且充满
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) {
            updateActive(player, 0L, now);
            return;
        }
        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null || !ModChargeData.isFull(magazine)) {
            updateActive(player, 0L, now);
            return;
        }

        // 法术释放中 → 立即关闭（以先到者为准）
        MagicData md = MagicData.getPlayerMagicData(player);
        if (md != null && md.isCasting()) {
            updateActive(player, 0L, now);
            return;
        }

        // 充满且未施法 → 激活/刷新
        long duration = ArcaneMagConfig.OVERFLOW_DURATION_TICKS.get();
        updateActive(player, now + duration, now);
    }

    private static void updateActive(ServerPlayer player, long newExpiry, long now) {
        UUID uuid = player.getUUID();
        long oldExpiry = EXPIRY_TICK.getOrDefault(uuid, 0L);
        boolean wasActive = oldExpiry > 0 && now < oldExpiry;
        boolean isActive = newExpiry > 0;
        if (wasActive != isActive) {
            if (isActive) EXPIRY_TICK.put(uuid, newExpiry);
            else EXPIRY_TICK.remove(uuid);
            refreshGunCache(player);
        } else if (isActive) {
            // 已激活状态下刷新过期时间（重新充满）
            EXPIRY_TICK.put(uuid, newExpiry);
        }
    }

    // ==================== 查询（供 enchant 乘区统一处理器调用） ====================

    public static double getActiveDamageBonusPercent(Player player) {
        if (!isActive(player)) return 0;
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return 0;
        int level = EnchantmentRegistry.OVERFLOW.get().levelOnGun(gunStack);
        return level <= 0 ? 0 : OverflowEnchantment.damageBonusPercent(level);
    }

    public static double getActiveRpmBonusPercent(Player player) {
        if (!isActive(player)) return 0;
        return ArcaneMagConfig.OVERFLOW_RPM_BONUS.get();
    }

    private static boolean isActive(Player player) {
        long expiry = EXPIRY_TICK.getOrDefault(player.getUUID(), 0L);
        return expiry > 0 && player.level().getGameTime() < expiry;
    }

    // ==================== 工具 ====================

    private static void refreshGunCache(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    // ==================== 生命周期清理 ====================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EXPIRY_TICK.remove(event.getEntity().getUUID());
    }
}
