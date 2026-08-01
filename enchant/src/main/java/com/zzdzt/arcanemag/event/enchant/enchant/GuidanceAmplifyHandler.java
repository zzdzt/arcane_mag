package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.enchant.GuidanceAmplifyEnchantment;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * guidance_amplify（引导增幅）触发处理器。
 *
 * 状态机（服务端权威）：
 *   - 持枪 + 枪上有本附魔 + MagicData.isCasting() + getCastType()==CONTINUOUS → active=true
 *   - active 状态翻转时 refreshGunCache（仅在进出施法时刷一次，非每 tick）
 *   - AttachmentPropertyEvent 触发时若 active，应用伤害 + 射速增益
 *
 * 性能：状态翻转才 refresh，施法期间每 tick 不刷枪缓存。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class GuidanceAmplifyHandler {

    private GuidanceAmplifyHandler() {}

    private static final Map<UUID, Boolean> ACTIVE = new HashMap<>();

    // ==================== tick 检测持续施法 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            updateActive(player, false);
            return;
        }

        int level = EnchantmentRegistry.GUIDANCE_AMPLIFY.get().levelOnGun(gunStack);
        if (level <= 0) {
            updateActive(player, false);
            return;
        }

        MagicData md = MagicData.getPlayerMagicData(player);
        boolean isActive = md != null
                && md.isCasting()
                && md.getCastType() == CastType.CONTINUOUS;
        updateActive(player, isActive);
    }

    private static void updateActive(ServerPlayer player, boolean isActive) {
        UUID uuid = player.getUUID();
        boolean wasActive = ACTIVE.getOrDefault(uuid, false);
        if (wasActive != isActive) {
            ACTIVE.put(uuid, isActive);
            refreshGunCache(player);
        }
    }

    // ==================== 枪属性应用 ====================

    // ==================== 查询（供 enchant 乘区统一处理器调用） ====================

    /**
     * 返回当前引导增幅活跃的伤害加成百分比（0 = 未激活）。
     * 调用方需保证 player 持枪；本方法内部校验 active 状态与枪上附魔等级。
     */
    public static double getActiveDamageBonusPercent(Player player) {
        if (!ACTIVE.getOrDefault(player.getUUID(), false)) return 0;
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) return 0;
        int level = EnchantmentRegistry.GUIDANCE_AMPLIFY.get().levelOnGun(gunStack);
        return level <= 0 ? 0 : GuidanceAmplifyEnchantment.damageBonusPercent(level);
    }

    /**
     * 返回当前引导增幅活跃的射速加成百分比（0 = 未激活）。
     */
    public static double getActiveRpmBonusPercent(Player player) {
        if (!ACTIVE.getOrDefault(player.getUUID(), false)) return 0;
        return EnchantConfig.GUIDANCE_AMPLIFY_RPM_BONUS.get();
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
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
