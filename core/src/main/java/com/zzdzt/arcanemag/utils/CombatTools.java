package com.zzdzt.arcanemag.utils;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class CombatTools {
    private CombatTools() {}

    public enum KnockbackTypes {
        DEFAULT,
        NO_KNOCKBACK
    }

    public static DamageSource getDamageSource(Level level, Entity projectile, Entity owner, ResourceKey<DamageType> damageType) {
        var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var holder = reg.getHolder(damageType)
            .orElseGet(() -> (Holder.Reference<DamageType>) level.damageSources().genericKill().typeHolder());
        return new DamageSource(holder, projectile, owner);
    }

    public static boolean isValidCombatTarget(Entity target, @Nullable Entity owner) {
        if (target == owner) return false;
        if (target instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) return true;
        return target instanceof LivingEntity;
    }

    public static boolean applyDamage(Entity target, float amount, DamageSource source, 
                                       SchoolType school, KnockbackTypes kbType) {
        if (!(target instanceof LivingEntity living)) {
            return target.hurt(source, amount);
        }

        float finalAmount = amount * getResistMultiplier(living, school);

        if (kbType == KnockbackTypes.NO_KNOCKBACK) {
            // 简单实现：标记后清除击退
            boolean result = living.hurt(source, finalAmount);
            if (result) {
                living.setDeltaMovement(0, living.getDeltaMovement().y, 0);
            }
            return result;
        }

        return living.hurt(source, finalAmount);
    }

    private static float getResistMultiplier(LivingEntity entity, SchoolType school) {
        var baseResist = entity.getAttributeValue(AttributeRegistry.SPELL_RESIST.get());
        if (school == null) {
            return 2.0f - (float) Utils.softCapFormula(baseResist);
        }
        return 2.0f - (float) Utils.softCapFormula(school.getResistanceFor(entity) * baseResist);
    }

    public static boolean canBeHostileTo(Entity target, LivingEntity player) {
        if (player == null || target == null || target == player || !target.isAlive()) return false;
        if (player.isAlliedTo(target) || target.isAlliedTo(player)) return false;
        if (target instanceof TamableAnimal t && t.isTame() && t.isOwnedBy(player)) return false;
        if (target instanceof Enemy) return true;
        if (player.getLastHurtByMob() == target) return true;
        if (target instanceof Mob m && m.getTarget() == player) return true;
        if (target instanceof NeutralMob n) {
            var anger = n.getPersistentAngerTarget();
            if (anger != null && anger.equals(player.getUUID())) return true;
            return n.isAngryAt(player);
        }
        return false;
    }
}