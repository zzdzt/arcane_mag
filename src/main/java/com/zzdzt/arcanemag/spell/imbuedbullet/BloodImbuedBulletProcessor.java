package com.zzdzt.arcanemag.spell.imbuedbullet;

import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 鲜血注魔子弹特效处理器。
 *
 * 命中时飞溅鲜血万能粒子（自动落地残留 blood_ground 血迹）。
 */
public class BloodImbuedBulletProcessor implements ImbuedBulletEffectProcessor {

    @Override
    public MutableComponent getDescriptionComponent(int spellLevel) {
        return null; // Tooltip 由法术类自身处理
    }

    @Override
    public void onCast(ServerPlayer caster, int spellLevel) {
        // 施法时视觉/音效（留空）
    }

    @Override
    public void onHit(ServerPlayer caster, LivingEntity target, float spellDamage, int spellLevel) {
        ServerLevel level = (ServerLevel) caster.level();
        spawnHitParticles(level, target);
    }

    /**
     * 鲜血命中粒子：BLOOD 万能粒子飞溅（自动落地生成 blood_ground 血迹残留）
     */
    private void spawnHitParticles(ServerLevel level, LivingEntity target) {
        Vec3 hitPos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        MagicManager.spawnParticles(
                level, ParticleHelper.BLOOD,
                hitPos.x, hitPos.y, hitPos.z,
                8,
                target.getBbWidth() * 0.3, target.getBbHeight() * 0.3, target.getBbWidth() * 0.3,
                0.05, false
        );
    }
}
