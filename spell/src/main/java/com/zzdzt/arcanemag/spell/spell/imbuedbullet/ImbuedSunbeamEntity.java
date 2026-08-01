package com.zzdzt.arcanemag.spell.imbuedbullet;

import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.sunbeam.SunbeamEntity;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * 神圣注魔专用 Sunbeam 实体。
 *
 * 支持自定义伤害计算：最终伤害 = 枪械原始伤害 × 法术强度。
 * 例如：枪械伤害 20，法术强度 2.5 → 最终伤害 50。
 * 法术强度 1.0 → 最终伤害 20（100% 原始伤害）。
 */
public class ImbuedSunbeamEntity extends SunbeamEntity {

    private static final ResourceLocation HOLY_SPELL_ID = ResourceLocation.fromNamespaceAndPath(
            "arcane_mag", "holy_imbued_bullet"
    );

    private float gunDamage = 0f;
    private float spellPower = 1.0f;

    public ImbuedSunbeamEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public ImbuedSunbeamEntity(Level level) {
        this(EntityRegistry.SUNBEAM.get(), level);
    }

    public void setGunDamage(float damage) {
        this.gunDamage = damage;
    }

    public void setSpellPower(float power) {
        this.spellPower = power;
    }

    @Override
    public void applyEffect(LivingEntity target) {
        float finalDamage = gunDamage * spellPower;

        // 最小 1 点伤害保底
        if (finalDamage < 1.0f) {
            finalDamage = 1.0f;
        }

        DamageSources.applyDamage(
            target,
            finalDamage,
            io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(HOLY_SPELL_ID)
                    .getDamageSource(this, getOwner())
        );
    }
}
