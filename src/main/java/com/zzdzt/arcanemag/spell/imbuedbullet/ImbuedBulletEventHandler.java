package com.zzdzt.arcanemag.spell.imbuedbullet;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.zzdzt.arcanemag.ArcaneMag;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 注魔子弹TACZ命中集成处理器。
 * 
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public final class ImbuedBulletEventHandler {

    private ImbuedBulletEventHandler() {}

    /**
     * TACZ的护甲穿透机制会在一次射击中调用多次hurt()，每次都会触发Post事件
     * 这会导致同一个目标在同一tick内被多次处理注魔效果
     */
    private static final Map<UUID, Long> PROCESSED_THIS_TICK = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGunHit(EntityHurtByGunEvent.Post event) {

        // 只处理服务端
        if (event.getLogicalSide().isClient()) return;

        // 获取射击者（TACZ 事件直接提供）
        LivingEntity attacker = event.getAttacker();
        if (!(attacker instanceof ServerPlayer shooter)) return;

        // 获取命中目标
        Entity hurtEntity = event.getHurtEntity();
        if (!(hurtEntity instanceof LivingEntity target)) return;

        // 去重检查：同一tick内同一目标只处理一次注魔效果
        long currentTick = shooter.level().getGameTime();
        Long lastTick = PROCESSED_THIS_TICK.get(target.getUUID());
        if (lastTick != null && lastTick == currentTick) {
            return;
        }
        PROCESSED_THIS_TICK.put(target.getUUID(), currentTick);

        // 查找玩家身上的注魔效果
        ImbuedBulletSpell activeSpell = findActiveImbuedSpell(shooter);
        if (activeSpell == null) return;

        // 从 MobEffect 获取等级
        MobEffectInstance effectInstance = shooter.getEffect(activeSpell.getImbuedEffect());
        if (effectInstance == null) return;

        int spellLevel = effectInstance.getAmplifier() + 1;

        // 如果目标已死（死于枪械伤害本身），直接调用子类的死亡处理
        if (!target.isAlive()) {
            activeSpell.onTargetDeadOnHit(shooter, target, spellLevel);
            return;
        }

        // 获取枪械原始配置伤害
        float gunDamage = getOriginalGunDamage(event);
        if (gunDamage <= 0) {
            gunDamage = event.getBaseAmount();
        }

        if (gunDamage <= 0) return;

        // 执行注魔效果
        activeSpell.onBulletHit(shooter, target, gunDamage, spellLevel);
    }

    /**
     * 监听目标死亡事件，处理鲜血注魔的击杀奖励。
     * 
     * 当带有鲜血标记的目标在标记持续时间内死亡时，
     * 为施加标记的玩家触发 VIGOR 奖励。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity target = event.getEntity();
        if (target == null) return;

        // 方式1：通过 NBT 标记检测（多枪打死情况，第一枪写了标记）
        if (target.getPersistentData().contains(BloodImbuedBulletSpell.MARK_TAG, net.minecraft.nbt.CompoundTag.TAG_COMPOUND)) {
            BloodImbuedBulletSpell.onTargetMarkedDeath(target);
            return;
        }

        // 方式2：通过 source 检测 shooter（一枪秒掉备用，Post 事件未触发）
        if (event.getSource() != null) {
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof ServerPlayer shooter) {
                ImbuedBulletSpell activeSpell = findActiveImbuedSpell(shooter);
                if (activeSpell instanceof BloodImbuedBulletSpell) {
                    MobEffectInstance effectInstance = shooter.getEffect(activeSpell.getImbuedEffect());
                    if (effectInstance != null) {
                        int spellLevel = effectInstance.getAmplifier() + 1;
                        BloodImbuedBulletSpell.applyVigorReward(shooter, spellLevel);
                        return;
                    }
                }
            }
        }
    }


    private static float getOriginalGunDamage(EntityHurtByGunEvent.Post event) {
        Entity bullet = event.getBullet();
        if (!(bullet instanceof EntityKineticBullet kineticBullet)) {
            return 0;
        }

        try {
            var tag = kineticBullet.getPersistentData();
            if (tag.contains("GunBaseDamage")) {
                return tag.getFloat("GunBaseDamage");
            }

        } catch (Exception e) {
            ArcaneMag.LOGGER.error("Failed to get original gun damage from bullet entity", e);
        }

        return 0;
    }

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
