package com.zzdzt.arcanemag.network;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public class CastTickHandler {
    private static final int MAX_CONTINUOUS_CAST_TICKS = 600;
    private static final int MAX_LONG_CAST_TICKS = 200;

    /** Recast 序列期间延迟清理 overdrive 的玩家（recast 结束后统一移除） */
    static final Set<UUID> DEFERRED_OVERDRIVE = new HashSet<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 处理延迟的 overdrive 清理（recast 序列结束后）
        if (!DEFERRED_OVERDRIVE.isEmpty()) {
            processDeferredOverdrive(event.getServer());
        }

        if (SpellCastHandler.ACTIVE_CASTS.isEmpty()) return;

        Iterator<Map.Entry<UUID, SpellCastHandler.CastingContext>> iterator = SpellCastHandler.ACTIVE_CASTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpellCastHandler.CastingContext> entry = iterator.next();
            SpellCastHandler.CastingContext context = entry.getValue();
            ServerPlayer player = context.player;
            MagicData magicData = MagicData.getPlayerMagicData(player);

            try {
                context.castTicks++;

                // 检查是否需要中断（切枪、死亡、下线等）
                if (shouldAbortCast(player, context, magicData)) {
                    abortCast(context, magicData);
                    iterator.remove();
                    continue;
                }

                // INSTANT / LONG 类型处理
                if (context.isInstantOrLong) {
                    if (!magicData.isCasting()) {
                        // 自然完成：铁魔法底层已自动触发 onCast + SpellOnCastEvent 扣除折扣后法力
                        boolean recastStillActive = magicData.getPlayerRecasts().hasRecastForSpell(context.spell);
                        if (!recastStillActive) {
                            // Recast 序列已结束（或非法术无 recast），执行完整清理
                            SpellCastHandler.applyCooldownConfig(context.spell, magicData, player, false);
                            SpellCastHandler.removeTemporaryAttributes(player, context.orbs);
                            SpellCastHandler.removeOverdriveBonus(player);
                            ItemStack mag = MagazineSpellHelper.getMagazineAttachment(player.getMainHandItem());
                            if (mag != null) ModChargeData.clearOverdrive(mag);
                            magicData.setAdditionalCastData(null);
                        } else {
                            // Recast 序列仍在进行，延迟清理 overdrive
                            DEFERRED_OVERDRIVE.add(player.getUUID());
                        }
                        // 无论是否有 recast，都从 ACTIVE_CASTS 移除（不阻塞后续 recast 按键）
                        iterator.remove();
                    } else if (context.castTicks > MAX_LONG_CAST_TICKS) {
                        // 超时强制中止
                        ArcaneMag.LOGGER.warn("LONG spell {} timed out after {} ticks for player {}", context.spell.getSpellResource(), MAX_LONG_CAST_TICKS, player.getName().getString());
                        abortCast(context, magicData);
                        iterator.remove();
                    }
                    continue;
                }

                // CONTINUOUS 类型处理
                if (context.castTicks > MAX_CONTINUOUS_CAST_TICKS) {
                    ArcaneMag.LOGGER.warn("CONTINUOUS spell {} exceeded max duration ({} ticks), force aborting for player {}", context.spell.getSpellResource(), MAX_CONTINUOUS_CAST_TICKS, player.getName().getString());
                    abortCast(context, magicData);
                    iterator.remove();
                    continue;
                }

                // 持续施法充能扣除
                if (context.magazine != null && context.chargeDrainPerTick > 0) {
                    double currentCharge = ModChargeData.getCharge(context.magazine);
                    ModChargeData.setCharge(context.magazine, currentCharge - context.chargeDrainPerTick);

                    // 充能耗尽，检查是否有 stacks 可以继续
                    if (ModChargeData.getCharge(context.magazine) <= 0) {
                        if (ModChargeData.hasStacks(context.magazine)) {
                            // 消耗一层，重新填充充能
                            ModChargeData.consumeStack(context.magazine);
                            ModChargeData.setCharge(context.magazine, ModChargeData.getMax(context.magazine));
                            ArcaneMag.LOGGER.debug("Continuous spell {} consumed stack, continuing cast for player {}", context.spell.getSpellResource(), player.getName().getString());
                        } else {
                            ArcaneMag.LOGGER.debug("Continuous spell {} aborted: charge depleted for player {}", context.spell.getSpellResource(), player.getName().getString());
                            abortCast(context, magicData);
                            iterator.remove();
                            continue;
                        }
                    }
                }

                if (!magicData.isCasting()) {
                    // 自然完成或法力耗尽导致的中断：铁魔法已经处理了 onCast
                    boolean cancelled = magicData.getMana() <= 0;
                    SpellCastHandler.applyCooldownConfig(context.spell, magicData, player, cancelled);
                    SpellCastHandler.removeTemporaryAttributes(player, context.orbs);
                    SpellCastHandler.removeOverdriveBonus(player);
                    if (context.magazine != null) ModChargeData.clearOverdrive(context.magazine);
                    magicData.setAdditionalCastData(null);
                    iterator.remove();
                }
            } catch (Exception e) {
                // 单个玩家施法异常不中断其他玩家的处理
                ArcaneMag.LOGGER.error("Error ticking cast for spell {} (player {}): {}",
                    context.spell.getSpellResource(), player.getName().getString(), e.getMessage(), e);
                SpellCastHandler.forceCleanup(player, magicData, context.orbs);
                iterator.remove();
            }
        }
    }

    /**
     * 处理延迟的 overdrive 清理：recast 序列结束后移除过载加成。
     * 每 tick 检查 DEFERRED_OVERDRIVE 中的玩家是否仍有活跃 recast。
     */
    private static void processDeferredOverdrive(net.minecraft.server.MinecraftServer server) {
        Iterator<UUID> it = DEFERRED_OVERDRIVE.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                it.remove();
                continue;
            }
            MagicData magicData = MagicData.getPlayerMagicData(player);
            // 所有 recast 均已结束 → 执行延迟清理
            if (!magicData.getPlayerRecasts().hasRecastsActive()) {
                SpellCastHandler.removeOverdriveBonus(player);
                ItemStack mag = MagazineSpellHelper.getMagazineAttachment(player.getMainHandItem());
                if (mag != null) ModChargeData.clearOverdrive(mag);
                // 移除 IS 在 recast 序列结束时添加的冷却
                SpellData currentSpell = MagazineSpellHelper.extractSpell(player.getMainHandItem());
                if (currentSpell != null) {
                    magicData.getPlayerCooldowns().removeCooldown(currentSpell.getSpell().getSpellId());
                }
                it.remove();
            }
        }
    }

    private static boolean shouldAbortCast(ServerPlayer player, SpellCastHandler.CastingContext context, MagicData magicData) {
        if (player.isRemoved() || !player.isAlive()) return true;
        if (context.cancelled) return true;
        if (!player.isAddedToWorld()) return true;
        if (player.hasDisconnected()) return true;
        
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof com.tacz.guns.api.item.IGun)) return true;
        
        SpellData currentSpell = MagazineSpellHelper.extractSpell(mainHand);
        if (currentSpell == null || currentSpell.getSpell() != context.spell) return true;
        
        return false;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        DEFERRED_OVERDRIVE.remove(uuid);
        SpellCastHandler.CastingContext context = SpellCastHandler.ACTIVE_CASTS.remove(uuid);
        if (context != null) {
            MagicData magicData = MagicData.getPlayerMagicData(context.player);
            abortCast(context, magicData);
        }
    }

    private static void abortCast(SpellCastHandler.CastingContext context, MagicData magicData) {
        ServerPlayer player = context.player;
        try {
            if (magicData.isCasting()) {
                Utils.serverSideCancelCast(player);
            }
        } catch (Exception e) {
            ArcaneMag.LOGGER.error("Error aborting cast for spell {}: {}", context.spell.getSpellResource(), e.getMessage());
            magicData.resetCastingState();
        }
        SpellCastHandler.applyCooldownConfig(context.spell, magicData, player, true);
        SpellCastHandler.removeTemporaryAttributes(player, context.orbs);
        SpellCastHandler.removeOverdriveBonus(player);
        if (context.magazine != null) ModChargeData.clearOverdrive(context.magazine);
        magicData.setAdditionalCastData(null);
    }
}
