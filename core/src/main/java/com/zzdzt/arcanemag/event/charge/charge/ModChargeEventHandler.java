package com.zzdzt.arcanemag.event.charge;

import java.util.ArrayList;
import java.util.List;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.api.ChargeModifier;
import com.zzdzt.arcanemag.api.ModifierRegistry;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.network.ArcaneMagNetworking;
import com.zzdzt.arcanemag.network.ModChargeSyncPacket;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import com.zzdzt.arcanemag.utils.WeaponTypeCoefManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;

/**
 * 充能事件处理器。
 *
 * 充能机制完全由弹匣上的附魔决定：
 * - charge_stacks：溢出 → 额外释放层数（等级 = 最大层数）
 * - charge_passive：持枪自动回充（等级 = 速率档位）
 * - charge_overdrive：溢出累计 → 过载激活（等级 = 倍率档位）
 *
 * 三者同属 PerkExclusionGroup.CHARGE_MECHANISM 互斥组，同一弹匣只能有一个。
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ModChargeEventHandler {

    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        // 充能已满且无溢出机制 → 跳过
        if (ModChargeData.isFull(magazine) && !hasOverflowMechanism(magazine)) return;

        double damage = event.getAmount();
        double weaponCoef = WeaponTypeCoefManager.getCoefFromEvent(event);
        double efficiency = getChargeEfficiency(player);
        double gain = damage * weaponCoef * efficiency * ArcaneMagConfig.CHARGE_PER_DAMAGE.get();

        // 通过 ChargeModifier 接口修改充能获取量（如 unstable_charge 的随机倍率）
        for (ChargeModifier mod : ModifierRegistry.getChargeModifiers()) {
            int level = mod.getLevelOnMagazine(magazine);
            if (level > 0) {
                gain = mod.modifyGain(gain, level);
            }
        }

        addChargeWithOverflow(magazine, gain, player.level().getGameTime());
        syncToClient(player, magazine);
    }

    @SubscribeEvent
    public static void onGunMelee(GunMeleeEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer player)) return;

        ItemStack gunStack = event.getGunItemStack();
        if (gunStack.isEmpty()) return;
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        if (ModChargeData.isFull(magazine) && !hasOverflowMechanism(magazine)) return;

        float meleeDamage = WeaponTypeCoefManager.getMeleeDamage(player, gunStack);
        if (meleeDamage <= 0) return;

        double weaponCoef = WeaponTypeCoefManager.getCoefFromGunStack(gunStack);
        double efficiency = getChargeEfficiency(player);
        double gain = meleeDamage * weaponCoef * efficiency * ArcaneMagConfig.CHARGE_PER_DAMAGE.get();

        // 通过 ChargeModifier 接口修改充能获取量（如 unstable_charge 的随机倍率）
        for (ChargeModifier mod : ModifierRegistry.getChargeModifiers()) {
            int level = mod.getLevelOnMagazine(magazine);
            if (level > 0) {
                gain = mod.modifyGain(gain, level);
            }
        }

        addChargeWithOverflow(magazine, gain, player.level().getGameTime());
        syncToClient(player, magazine);
    }

    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        double killBonus = ArcaneMagConfig.CHARGE_KILL_BONUS.get();
        addChargeWithOverflow(magazine, killBonus, player.level().getGameTime());
        syncToClient(player, magazine);
    }

    /**
     * 被动充能 + 过载超时检查（每 tick）
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        // 过载超时检查（所有有 overdrive 数据的弹匣都检查）
        if (ModChargeData.hasOverdrive(magazine)) {
            ModChargeData.tickOverdriveExpire(magazine, player.level().getGameTime());
            if (!ModChargeData.hasOverdrive(magazine)) {
                SpellCastHandler.removeOverdriveBonus(player);
                syncToClient(player, magazine);
            }
        }

        // 通过 ChargeModifier 接口获取被动充能速率（per tick）
        double totalRatePerTick = 0;
        for (ChargeModifier mod : ModifierRegistry.getChargeModifiers()) {
            int level = mod.getLevelOnMagazine(magazine);
            if (level > 0) {
                totalRatePerTick += mod.getPassiveRatePerTick(level);
            }
        }
        if (totalRatePerTick <= 0) return;
        if (ModChargeData.isFull(magazine)) return;

        ModChargeData.addCharge(magazine, totalRatePerTick);

        // 节流同步：每 20 tick（per-player）或充满时立即同步
        if (ModChargeData.isFull(magazine) || player.tickCount % 20 == 0) {
            syncToClient(player, magazine);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 弹匣是否有溢出处理机制（任何注册的 ChargeModifier 且 isOverflowHandler）
     */
    private static boolean hasOverflowMechanism(ItemStack magazine) {
        for (ChargeModifier mod : ModifierRegistry.getChargeModifiers()) {
            if (mod.isOverflowHandler() && mod.getLevelOnMagazine(magazine) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 累加充能并处理溢出。
     * 溢出行为由注册的 ChargeModifier（isOverflowHandler）按注册顺序依次处理。
     * 例如 charge_stacks：溢出 → 层数；charge_overdrive：溢出累计 → 达阈值激活过载。
     */
    private static void addChargeWithOverflow(ItemStack magazine, double gain, long currentTick) {
        double current = ModChargeData.getCharge(magazine);
        double max = ModChargeData.getMax(magazine);
        if (max <= 0.0) return;

        double newCharge = current + gain;

        // 收集溢出处理 modifier（按注册顺序 = 优先级）
        List<ChargeModifier> overflowHandlers = new ArrayList<>();
        for (ChargeModifier mod : ModifierRegistry.getChargeModifiers()) {
            if (mod.isOverflowHandler() && mod.getLevelOnMagazine(magazine) > 0) {
                overflowHandlers.add(mod);
            }
        }

        while (newCharge >= max) {
            double prev = newCharge;
            for (ChargeModifier mod : overflowHandlers) {
                int level = mod.getLevelOnMagazine(magazine);
                double result = mod.handleOverflow(magazine, level, newCharge, max, currentTick);
                if (result < newCharge) {
                    // 此 handler 消耗了部分溢出，退出 for 重新检查 while
                    newCharge = result;
                    break;
                }
                // result >= newCharge：此 handler 未处理，继续试下一个
            }
            if (newCharge >= prev) {
                // 所有 handler 都未处理（或已无法继续），封顶停止
                newCharge = max;
                break;
            }
        }

        ModChargeData.setCharge(magazine, Math.max(0, Math.min(newCharge, max)));
    }

    private static double getChargeEfficiency(ServerPlayer player) {
        try {
            double cdr = player.getAttributeValue(
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.COOLDOWN_REDUCTION.get()
            );
            return Math.max(1.0, cdr);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private static void syncToClient(ServerPlayer player, ItemStack magazine) {
        try {
            ArcaneMagNetworking.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ModChargeSyncPacket(magazine)
            );
        } catch (Exception e) {
            ArcaneMag.LOGGER.warn("[ArcaneMag] Failed to sync charge data: {}", e.getMessage());
        }
    }
}
