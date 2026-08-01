package com.zzdzt.arcanemag.event.enchant;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.utils.ShotLedger;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * 射击追踪事件处理器——ShotLedger 的事件入口。
 *
 * 负责三阶段匹配的事件接入：
 * - GunFireEvent → rememberShot（阶段一）
 * - EntityJoinLevelEvent → bindBullet（阶段二）
 * - ServerTickEvent → prune（过期清理）
 *
 * 当前未启用：ENABLED = false。
 * 未来法术需要 per-bullet 数据关联时，将 ENABLED 设为 true 并在
 * rememberShot 处填充实际 payload 即可激活。
 *
 * 注意：阶段三（claim）由具体法术的事件处理器自行调用，
 * 本类不处理命中事件，避免对未启用法术产生性能开销。
 */
@Mod.EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public class ShotTrackingEvents {

    /**
     * 总开关。设为 true 启用射击追踪。
     * 未来可改为 config 项或按弹匣法术动态启用。
     */
    public static final boolean ENABLED = false;

    private static final ShotLedger LEDGER = new ShotLedger();

    /**
     * 获取全局 ShotLedger 实例（供法术事件处理器调用 claim/peek）。
     */
    public static ShotLedger getLedger() {
        return LEDGER;
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (!ENABLED) return;
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        if (!(event.getShooter() instanceof ServerPlayer shooter)) return;

        var gunStack = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) return;

        // TODO: 未来在此处根据弹匣法术构建实际 payload
        // 当前仅记录空 payload 作为骨架验证
        ShotLedger.ShotPayload payload = new ShotLedger.ShotPayload(null, 0, null);
        LEDGER.rememberShot(shooter, gunId, payload);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!ENABLED) return;
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof EntityKineticBullet bullet)) return;
        if (!(bullet.getOwner() instanceof ServerPlayer shooter)) return;

        LEDGER.bindBullet(shooter, bullet);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED) return;
        if (event.phase != TickEvent.Phase.END) return;

        LEDGER.prune(event.getServer().getTickCount());
    }
}
