package com.zzdzt.arcanemag.event.spell;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.ArcaneMagSpell;
import com.zzdzt.arcanemag.spell.imbuedbullet.ImbuedBulletState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * spell 模块状态清理处理器：仅清理 ImbuedBulletState（注魔子弹状态）。
 *
 * 归属 spell：依赖 ImbuedBulletState（spell 模块专属）。
 * core 的施法中断 + 临时属性清理由 CoreLifecycleHandler 处理，不在此耦合。
 * 同一事件可被多个 @EventBusSubscriber 监听，各管各的。
 */
@Mod.EventBusSubscriber(modid = ArcaneMagSpell.MODID)
public class SpellStateCleanupHandler {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ImbuedBulletState.clearAll(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ImbuedBulletState.clearAll(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ImbuedBulletState.clearAll(player);
    }
}
