package com.zzdzt.arcanemag.event.enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.enchant.ManaHawkEyeEnchantment;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * mana_hawk_eye（法力鹰眼）触发处理器。
 *
 * 状态机（服务端权威）：
 *   - 开镜中（IGunOperator.getSynIsAiming）：每 20tick 扣 COST_PERCENT% max_mana，累计 consumedMana
 *   - bonusPercent = min(consumedMana / MANA_PER_BONUS, maxBonusPercent(level))
 *   - 仅 bonusPercent 整数变化时 refreshGunCache（性能优化）
 *   - 退出开镜：consumedMana=0, bonusPercent=0, refresh
 *   - 法力不足：actualCost = min(mana, cost)，bonus 停止增长但保持已累积值
 *
 * AttachmentPropertyEvent 触发时若 bonusPercent > 0 且枪上刻有本附魔，应用伤害倍率。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class ManaHawkEyeHandler {

    private ManaHawkEyeHandler() {}

    private static final int MANA_DRAIN_INTERVAL_TICKS = 20;  // 每 20tick（1s）扣一次法力

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private static final class Tracker {
        double consumedMana = 0;     // 累计已消耗法力（绝对值）
        int bonusPercent = 0;        // 当前应用的伤害加成 %
        int drainTickCounter = 0;    // 扣法力节流计数
    }

    // ==================== tick 检测开镜 + 扣法力 ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            // 无枪：清状态
            clearTracker(player);
            return;
        }

        int level = EnchantmentRegistry.MANA_HAWK_EYE.get().levelOnGun(gunStack);
        if (level <= 0) {
            // 枪无本附魔：清状态
            clearTracker(player);
            return;
        }

        IGunOperator op = IGunOperator.fromLivingEntity(player);
        boolean isAiming = op.getSynIsAiming();

        Tracker t = TRACKERS.get(player.getUUID());

        if (!isAiming) {
            // 退出开镜：清零累计 + 刷新
            if (t != null && (t.consumedMana > 0 || t.bonusPercent > 0)) {
                t.consumedMana = 0;
                t.bonusPercent = 0;
                t.drainTickCounter = 0;
                refreshGunCache(player);
            }
            return;
        }

        // 开镜中
        if (t == null) {
            t = new Tracker();
            TRACKERS.put(player.getUUID(), t);
        }

        // 已达上限：不再扣法力（保持满 bonus，退出开镜才清零）
        int maxBonus = ManaHawkEyeEnchantment.maxBonusPercent(level);
        if (t.bonusPercent >= maxBonus) {
            return;
        }

        t.drainTickCounter++;
        if (t.drainTickCounter < MANA_DRAIN_INTERVAL_TICKS) return;
        t.drainTickCounter = 0;

        // 扣 COST_PERCENT% max_mana
        MagicData md = MagicData.getPlayerMagicData(player);
        if (md == null) return;
        double maxMana = player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        if (maxMana <= 0) return;
        double cost = maxMana * EnchantConfig.MANA_HAWK_EYE_MANA_COST_PERCENT.get() / 100.0;
        double actualCost = Math.min(md.getMana(), cost);
        if (actualCost <= 0) return;   // 法力耗尽：不扣不增长，保持现有 bonus
        md.setMana(md.getMana() - (float) actualCost);
        t.consumedMana += actualCost;

        // 计算新 bonus
        double manaPerBonus = EnchantConfig.MANA_HAWK_EYE_MANA_PER_BONUS.get();
        int newBonus = manaPerBonus > 0
            ? (int) Math.min(t.consumedMana / manaPerBonus, maxBonus)
            : maxBonus;
        if (newBonus != t.bonusPercent) {
            t.bonusPercent = newBonus;
            refreshGunCache(player);   // 仅整数 % 变化时刷新
        }
    }

    // ==================== 枪属性应用 ====================

    // ==================== 查询（供 enchant 乘区统一处理器调用） ====================

    /**
     * 返回当前法力鹰眼活跃的伤害加成百分比（0 = 未激活）。
     * 调用方需保证 player 持枪且枪上有本附魔；本方法仅读 tracker 状态。
     */
    public static int getActiveDamageBonusPercent(Player player) {
        Tracker t = TRACKERS.get(player.getUUID());
        return (t == null) ? 0 : t.bonusPercent;
    }

    // ==================== 工具 ====================

    private static void refreshGunCache(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof IGun) {
            com.tacz.guns.resource.modifier.AttachmentPropertyManager.postChangeEvent(player, mainHand);
        }
    }

    private static void clearTracker(ServerPlayer player) {
        Tracker t = TRACKERS.remove(player.getUUID());
        if (t != null && t.bonusPercent > 0) {
            refreshGunCache(player);
        }
    }

    // ==================== 生命周期清理 ====================

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }
}
