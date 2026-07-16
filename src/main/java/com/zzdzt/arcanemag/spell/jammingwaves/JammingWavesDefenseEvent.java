package com.zzdzt.arcanemag.spell.jammingwaves;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.spell.jammingwaves.handler.JammingWavesEffectBroadcaster;
import com.zzdzt.arcanemag.spell.jammingwaves.handler.JammingWavesGeometry;
import com.zzdzt.arcanemag.spell.jammingwaves.handler.JammingWavesManaDrain;
import com.zzdzt.arcanemag.spell.jammingwaves.handler.JammingWavesMeleeHandler;
import com.zzdzt.arcanemag.spell.jammingwaves.handler.JammingWavesProjectileHandler;
import com.zzdzt.arcanemag.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Jamming Waves（干扰波纹）防御事件处理器 — 精简入口类。
 * 
 * 所有具体逻辑已委托给 handler 子包中的单一职责类：
 * 
 *   {@link JammingWavesGeometry} — 方向/位置/法线计算
 *   {@link JammingWavesProjectileHandler} — 弹射物拦截
 *   {@link JammingWavesMeleeHandler} — 近战拦截
 *   {@link JammingWavesManaDrain} — 法力消耗
 *   {@link JammingWavesEffectBroadcaster} — 特效广播
 */
@Mod.EventBusSubscriber(modid = ArcaneMag.MODID)
public final class JammingWavesDefenseEvent {

    public static final double FRONT_DOT_THRESHOLD = 0.25;
    public static final double INTERCEPT_RADIUS = 3.5;
    public static final double INTERCEPT_RADIUS_SQ = INTERCEPT_RADIUS * INTERCEPT_RADIUS;
    private static final double MELEE_INTERCEPT_DISTANCE = 1.5;

    private JammingWavesDefenseEvent() {}

    // 主动拦截（onServerCastTick 中调用）方法

    public static void interceptNearbyProjectiles(Level level, int spellLevel,
                                                   LivingEntity caster, @Nullable MagicData magicData) {
        if (level.isClientSide || magicData == null || spellLevel <= 0) return;
        if (!JammingWavesState.isWarmedUp(caster)) return;

        JammingWavesEffectBroadcaster.spawnAmbientWall(level, caster, INTERCEPT_RADIUS);

        var context = new JammingWavesContext(magicData, spellLevel);
        var searchBox = caster.getBoundingBox().inflate(INTERCEPT_RADIUS);
        var projectiles = level.getEntitiesOfClass(
            Projectile.class, searchBox,
            projectile -> JammingWavesProjectileHandler.shouldIntercept(caster, projectile, INTERCEPT_RADIUS_SQ)
        );

        for (var projectile : projectiles) {
            if (JammingWavesGeometry.isProjectileInFrontArc(caster, projectile, FRONT_DOT_THRESHOLD)) {
                JammingWavesProjectileHandler.neutralize(caster, context, projectile);
            }
        }
    }

    // LivingAttackEvent 

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        var target = event.getEntity();
        if (target.level().isClientSide) return;

        var context = getActiveJammingWaves(target);
        if (context == null) return;
        if (!JammingWavesState.isWarmedUp(target)) return;

        var source = event.getSource();

        if (!JammingWavesGeometry.isDamageFromFront(target, source, FRONT_DOT_THRESHOLD)) {
            JammingWavesEffectBroadcaster.onDefenseFailed(target, context, source, MELEE_INTERCEPT_DISTANCE);
            return;
        }

        if (!isBlockableByJammingWaves(source)) {
            JammingWavesEffectBroadcaster.onDefenseFailed(target, context, source, MELEE_INTERCEPT_DISTANCE);
            return;
        }

        event.setCanceled(true);

        if (JammingWavesMeleeHandler.isMeleeAttack(source)
            && JammingWavesMeleeHandler.isCloseRange(target, source.getEntity(), INTERCEPT_RADIUS_SQ)) {
            JammingWavesMeleeHandler.handleIntercept(target, context, source);
            return;
        }

        var directEntity = source.getDirectEntity();
        if (directEntity instanceof Projectile projectile) {
            var interceptPos = JammingWavesGeometry.getRangedInterceptPosition(
                target, source.getEntity(), projectile.getDeltaMovement(), INTERCEPT_RADIUS);
            JammingWavesProjectileHandler.neutralize(target, context, projectile, interceptPos, source.getEntity());
            return;
        }

        var fallbackPos = JammingWavesGeometry.getRangedInterceptPosition(
            target, source.getEntity(), null, INTERCEPT_RADIUS);
        var fallbackNormal = JammingWavesGeometry.getInterceptNormal(target, fallbackPos, source.getEntity(), null);
        JammingWavesEffectBroadcaster.onIntercept(target, context, fallbackPos, fallbackNormal);
    }

    // 工具 

    private static boolean isBlockableByJammingWaves(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) return false;
        var direct = source.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
            && arrow.getPierceLevel() > 0) {
            return false;
        }
        return true;
    }

    @Nullable
    private static JammingWavesContext getActiveJammingWaves(LivingEntity entity) {
        var magicData = MagicData.getPlayerMagicData(entity);
        if (magicData == null || !magicData.isCasting()) return null;
        if (!SpellRegistry.JAMMING_WAVES.get().getSpellId().equals(magicData.getCastingSpellId()))
            return null;
        return new JammingWavesContext(magicData, Math.max(1, magicData.getCastingSpellLevel()));
    }
}
