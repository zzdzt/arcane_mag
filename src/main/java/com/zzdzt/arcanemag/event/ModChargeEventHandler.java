package com.zzdzt.arcanemag.event;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.network.ArcaneMagNetworking;
import com.zzdzt.arcanemag.network.ModChargeSyncPacket;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.utils.CuriosHelper;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import com.zzdzt.arcanemag.utils.WeaponTypeCoefManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class ModChargeEventHandler {

    // 机制解锁物品标签 — 佩戴后解锁对应充能机制
    private static final TagKey<Item> UNLOCKS_OVERDRIVE = ItemTags.create(
        new ResourceLocation("arcane_mag", "unlocks_overdrive"));
    private static final TagKey<Item> UNLOCKS_STACKS = ItemTags.create(
        new ResourceLocation("arcane_mag", "unlocks_stacks"));
    private static final TagKey<Item> UNLOCKS_PASSIVE = ItemTags.create(
        new ResourceLocation("arcane_mag", "unlocks_passive"));

    /**
     * 判断某个机制是否对当前玩家+弹匣激活
     * 激活条件 = 法术 JSON 白名单允许 OR 玩家 Curios 佩戴对应 Tag 饰品
     */
    private static boolean isMechanismActive(ServerPlayer player, ItemStack magazine,
                                              TagKey<Item> tagKey,
                                              java.util.function.Function<ItemStack, Boolean> spellCheck) {
        // 路径一：法术 JSON 白名单
        if (spellCheck.apply(magazine)) return true;
        // 路径二：玩家 Curios 佩戴对应装备
        return CuriosHelper.hasCurioWithTag(player, tagKey);
    }

    @SubscribeEvent
    public static void onEntityHurtByGunPost(EntityHurtByGunEvent.Post event) {
        // 只处理服务端
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        // 攻击者必须是玩家
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;

        // 必须持有铭刻了法术的弹匣
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        // 如果充能已满且无任何活跃机制，跳过
        if (ModChargeData.isFull(magazine)
            && !isMechanismActive(player, magazine, UNLOCKS_OVERDRIVE, ModChargeData::allowOverdrive)
            && !isMechanismActive(player, magazine, UNLOCKS_STACKS, ModChargeData::allowStacks)) {
            return;
        }

        // 计算充能
        double damage = event.getAmount();
        double weaponCoef = WeaponTypeCoefManager.getCoefFromEvent(event);
        double efficiency = getChargeEfficiency(player);

        double gain = damage * weaponCoef * efficiency * ArcaneMagConfig.CHARGE_PER_DAMAGE.get() * 1.0;

        // 累加充能
        addChargeWithOverflow(magazine, gain, player.level().getGameTime(), player);

        // 同步到客户端
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

        if (ModChargeData.isFull(magazine)
            && !isMechanismActive(player, magazine, UNLOCKS_OVERDRIVE, ModChargeData::allowOverdrive)
            && !isMechanismActive(player, magazine, UNLOCKS_STACKS, ModChargeData::allowStacks)) {
            return;
        }

        float meleeDamage = WeaponTypeCoefManager.getMeleeDamage(player, gunStack);
        if (meleeDamage <= 0) return;

        double weaponCoef = WeaponTypeCoefManager.getCoefFromGunStack(gunStack);
        double efficiency = getChargeEfficiency(player);

        double gain = meleeDamage * weaponCoef * efficiency * ArcaneMagConfig.CHARGE_PER_DAMAGE.get() * 1.0;

        addChargeWithOverflow(magazine, gain, player.level().getGameTime(), player);

        syncToClient(player, magazine);
    }

    @SubscribeEvent
    public static void onEntityKillByGun(EntityKillByGunEvent event) {

        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;

        // 必须持有铭刻了法术的弹匣
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        // 击杀奖励（不受效率影响）
        double killBonus = ArcaneMagConfig.CHARGE_KILL_BONUS.get();

        // 累加充能
        addChargeWithOverflow(magazine, killBonus, player.level().getGameTime(), player);

        // 同步到客户端
        syncToClient(player, magazine);
    }

    /**
     * 获取玩家充能效率（基于铁魔法冷却缩减属性）
     */
    private static double getChargeEfficiency(ServerPlayer player) {
        try {
            double cdr = player.getAttributeValue(
                io.redspace.ironsspellbooks.api.registry.AttributeRegistry.COOLDOWN_REDUCTION.get()
            );
            return Math.max(1.0, cdr); // 默认 1.0，冷却缩减会使其 > 1.0
        } catch (Exception e) {
            return 1.0;
        }
    }

    /**
     * 累加充能并处理溢出（多次充能层数 / 过载层数）
     */
    private static void addChargeWithOverflow(ItemStack magazine, double gain, long currentTick, ServerPlayer player) {
        double current = ModChargeData.getCharge(magazine);
        double max = ModChargeData.getMax(magazine);

        if (max <= 0.0) return;

        double newCharge = current + gain;

        // 预计算各机制激活状态
        boolean hasOverdrive = isMechanismActive(player, magazine, UNLOCKS_OVERDRIVE, ModChargeData::allowOverdrive);
        boolean hasStacks = isMechanismActive(player, magazine, UNLOCKS_STACKS, ModChargeData::allowStacks);

        // 处理溢出
        while (newCharge >= max) {
            if (hasStacks
                && ModChargeData.getStacks(magazine) < ModChargeData.getMaxStacks(magazine)) {
                // 存储额外释放次数
                ModChargeData.setStacks(magazine, ModChargeData.getStacks(magazine) + 1);
                newCharge -= max;
            } else if (hasOverdrive && !ModChargeData.hasOverdrive(magazine)) {
                // 累计溢出量，达到 max × threshold 后激活过载
                double overflow = newCharge - max;
                double accumulated = ModChargeData.getOverdriveProgress(magazine) + overflow;
                double threshold = max * ArcaneMagConfig.CHARGE_OVERDRIVE_THRESHOLD.get();
                if (accumulated >= threshold) {
                    ModChargeData.activateOverdrive(magazine, currentTick + 200L);
                    ModChargeData.setOverdriveProgress(magazine, 0.0);
                } else {
                    ModChargeData.setOverdriveProgress(magazine, accumulated);
                }
                newCharge = max;
                break;
            } else {
                // 无法存储更多，截断
                newCharge = max;
                break;
            }
        }

        ModChargeData.setCharge(magazine, Math.min(newCharge, max));
    }

    // 被动充能同步节流计数器（每 20 tick 同步一次，避免网络洪泛）
    private static int passiveSyncCounter = 0;

    /**
     * 被动充能：每 tick 根据 passive_rate 缓慢恢复充能值
     * 仅在服务端生效，避免客户端双倍计算
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅处理服务端 END 阶段
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) return;

        // 必须持有铭刻了法术的弹匣
        if (!MagazineSpellHelper.hasSpellMagazine(gunStack)) return;

        ItemStack magazine = MagazineSpellHelper.getMagazineAttachment(gunStack);
        if (magazine == null) return;

        // 过载超时检查（独立于被动充能，所有铭刻弹匣都检查）
        if (ModChargeData.hasOverdrive(magazine)) {
            ModChargeData.tickOverdriveExpire(magazine, player.level().getGameTime());
            if (!ModChargeData.hasOverdrive(magazine)) {
                // 过载已到期：移除属性加成 + 同步 HUD
                SpellCastHandler.removeOverdriveBonus(player);
                syncToClient(player, magazine);
            }
        }

        // 检查被动充能是否激活（JSON 白名单 或 Curios 饰品）
        if (!isMechanismActive(player, magazine, UNLOCKS_PASSIVE, ModChargeData::allowPassive)) return;

        // 充能已满则跳过
        if (ModChargeData.isFull(magazine)) return;

        double rate = ModChargeData.getPassiveRate(magazine);
        if (rate <= 0.0) return;

        // 每 tick 增加值 = 每秒速率 / 20
        double tickGain = rate / 20.0;
        ModChargeData.addCharge(magazine, tickGain);

        // 节流同步：每 20 tick（1 秒）同步一次，充能刚好满时立即同步
        if (ModChargeData.isFull(magazine)) {
            syncToClient(player, magazine);
            passiveSyncCounter = 0;
        } else if (++passiveSyncCounter >= 20) {
            syncToClient(player, magazine);
            passiveSyncCounter = 0;
        }
    }

    /**
     * 同步充能数据到客户端
     */
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
