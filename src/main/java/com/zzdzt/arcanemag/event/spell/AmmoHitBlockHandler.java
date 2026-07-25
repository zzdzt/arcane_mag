package com.zzdzt.arcanemag.event.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.imbuedbullet.ImbuedBulletSpell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * 注魔子弹命中方块处理器。
 *
 * 监听 TACZ 的 AmmoHitBlockEvent
 * 霰弹枪去重：per-player CD（1s），同一次射击的多发弹丸只触发一次。
 */
@EventBusSubscriber(modid = ArcaneMag.MODID)
public final class AmmoHitBlockHandler {

    private AmmoHitBlockHandler() {}

    /** 每玩家上次触发 tick，用于霰弹枪多发去重。 */
    private static final Map<UUID, Long> LAST_TRIGGER_TICK = new HashMap<>();
    private static final long DEDUP_CD_TICKS = 15L; // 0.75 秒

    @SubscribeEvent
    public static void onAmmoHitBlock(AmmoHitBlockEvent event) {
        // 仅服务端
        if (event.getLevel().isClientSide()) return;

        // 获取射击者
        EntityKineticBullet bullet = event.getAmmo();
        Entity owner = bullet.getOwner();
        if (!(owner instanceof ServerPlayer shooter)) return;

        // 去重：霰弹枪多发弹丸同时命中方块时只处理第一发
        long now = shooter.level().getGameTime();
        Long lastTick = LAST_TRIGGER_TICK.get(shooter.getUUID());
        if (lastTick != null && now - lastTick < DEDUP_CD_TICKS) return;

        // 查找玩家身上的注魔效果
        ImbuedBulletSpell activeSpell = findActiveImbuedSpell(shooter);
        if (activeSpell == null) return;

        // 获取等级
        var effectInstance = shooter.getEffect(activeSpell.getImbuedEffect());
        if (effectInstance == null) return;
        int spellLevel = effectInstance.getAmplifier() + 1;

        // 命中坐标
        Vec3 hitPos = event.getHitResult().getLocation();

        float gunDamage = bullet.getDamage(hitPos);
        if (gunDamage <= 0) return;

        // 记录去重tick
        LAST_TRIGGER_TICK.put(shooter.getUUID(), now);

        // 触发方块命中特效
        activeSpell.onBulletHitBlock(shooter, hitPos, gunDamage, spellLevel);
    }

    // ==================== 工具 ====================

    private static ImbuedBulletSpell findActiveImbuedSpell(ServerPlayer player) {
        for (var entry : com.zzdzt.arcanemag.registry.SpellRegistry.SPELLS.getEntries()) {
            if (entry.get() instanceof ImbuedBulletSpell imbuedSpell) {
                if (player.hasEffect(imbuedSpell.getImbuedEffect())) {
                    return imbuedSpell;
                }
            }
        }
        return null;
    }
}
