package com.zzdzt.arcanemag.event.charge;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.network.ArcaneMagNetworking;
import com.zzdzt.arcanemag.network.ModChargeSyncPacket;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import com.zzdzt.arcanemag.utils.WeaponTypeCoefManager;
import com.zzdzt.arcanemag.enchant.UnstableChargeEnchantment;
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

        // 不稳定充能：随机倍率（可负）
        if (EnchantmentRegistry.UNSTABLE_CHARGE.get().levelOnMagazine(magazine) > 0) {
            gain *= UnstableChargeEnchantment.rollFactor();
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

        // 不稳定充能：随机倍率（可负）
        if (EnchantmentRegistry.UNSTABLE_CHARGE.get().levelOnMagazine(magazine) > 0) {
            gain *= UnstableChargeEnchantment.rollFactor();
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

        // 被动充能：读弹匣附魔等级
        int passiveLevel = EnchantmentRegistry.CHARGE_PASSIVE.get().levelOnMagazine(magazine);
        if (passiveLevel <= 0) return;
        if (ModChargeData.isFull(magazine)) return;

        // 每秒恢复量 = 等级 × 配置基础速率
        double ratePerSecond = passiveLevel * ArcaneMagConfig.CHARGE_PASSIVE_RATE_PER_LEVEL.get();
        if (ratePerSecond <= 0.0) return;

        double tickGain = ratePerSecond / 20.0;
        ModChargeData.addCharge(magazine, tickGain);

        // 节流同步：每 20 tick（per-player）或充满时立即同步
        if (ModChargeData.isFull(magazine) || player.tickCount % 20 == 0) {
            syncToClient(player, magazine);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 弹匣是否有溢出处理机制（stacks 或 overdrive）
     */
    private static boolean hasOverflowMechanism(ItemStack magazine) {
        return EnchantmentRegistry.CHARGE_STACKS.get().levelOnMagazine(magazine) > 0
            || EnchantmentRegistry.CHARGE_OVERDRIVE.get().levelOnMagazine(magazine) > 0;
    }

    /**
     * 累加充能并处理溢出。
     * 溢出行为由弹匣附魔决定：
     * - charge_stacks：溢出 → 层数（等级 = 上限）
     * - charge_overdrive：溢出累计 → 达阈值激活过载
     */
    private static void addChargeWithOverflow(ItemStack magazine, double gain, long currentTick) {
        double current = ModChargeData.getCharge(magazine);
        double max = ModChargeData.getMax(magazine);
        if (max <= 0.0) return;

        double newCharge = current + gain;

        int stacksLevel = EnchantmentRegistry.CHARGE_STACKS.get().levelOnMagazine(magazine);
        int overdriveLevel = EnchantmentRegistry.CHARGE_OVERDRIVE.get().levelOnMagazine(magazine);

        while (newCharge >= max) {
            if (stacksLevel > 0 && ModChargeData.getStacks(magazine) < stacksLevel) {
                // stacks 机制：溢出转化为额外释放层数，等级 = 上限
                ModChargeData.setStacks(magazine, ModChargeData.getStacks(magazine) + 1);
                newCharge -= max;
            } else if (overdriveLevel > 0 && !ModChargeData.hasOverdrive(magazine)) {
                // overdrive 机制：累计溢出，达阈值激活
                double overflow = newCharge - max;
                double accumulated = ModChargeData.getOverdriveProgress(magazine) + overflow;
                double threshold = max * ArcaneMagConfig.CHARGE_OVERDRIVE_THRESHOLD.get();
                if (accumulated >= threshold) {
                    ModChargeData.activateOverdrive(magazine,
                        currentTick + ArcaneMagConfig.CHARGE_OVERDRIVE_DURATION_TICKS.get());
                    ModChargeData.setOverdriveProgress(magazine, 0.0);
                } else {
                    ModChargeData.setOverdriveProgress(magazine, accumulated);
                }
                newCharge = max;
                break;
            } else {
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
