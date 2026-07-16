package com.zzdzt.arcanemag.spell.smoulderingfire;

import com.zzdzt.arcanemag.ArcaneMag;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.particle.FlameStrikeParticleOptions;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 焚灭
 * 
 * 强化版烈焰斩击：
 * - 更大的攻击范围
 * - 对燃烧中的目标造成更高伤害
 * - 主手 + 副手武器伤害叠加
 */
public class SmoulderingFireSpell extends AbstractSpell {
    
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(
        ArcaneMag.MODID, "smouldering_fire"
    );

    private static final float RADIUS = 5.5f;
    private static final float DISTANCE = 3.5f;
    
    //燃烧增伤倍率 
    private static final float BURNING_DAMAGE_MULTIPLIER = 2.5f;
    
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)  
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(18)  
            .build();

    public SmoulderingFireSpell() {
        this.manaCostPerLevel = 5;
        this.baseSpellPower = 4;      
        this.spellPowerPerLevel = 4;
        this.castTime = 15;           
        this.baseManaCost = 60;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("ui.irons_spellbooks.damage", getDamageText(spellLevel, caster))
        );
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundRegistry.FLAMING_STRIKE_UPSWING.get());
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundRegistry.FLAMING_STRIKE_SWING.get());
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public boolean canBeInterrupted(@Nullable Player player) {
        return false;
    }

    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        return getCastTime(spellLevel);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, 
                       CastSource castSource, MagicData playerMagicData) {
        
        Vec3 forward = entity.getForward();
        Vec3 hitLocation = entity.position()
            .add(0, entity.getBbHeight() * 0.3f, 0)
            .add(forward.scale(DISTANCE));
        
        var entities = level.getEntities(entity, 
            AABB.ofSize(hitLocation, RADIUS * 2, RADIUS, RADIUS * 2));
        
        var damageSource = this.getDamageSource(entity);
        int burningTargetsHit = 0;
        int totalTargetsHit = 0;

        for (Entity targetEntity : entities) {
            if (!(targetEntity instanceof LivingEntity livingTarget)) continue;
            if (!livingTarget.isAlive()) continue;
            if (!livingTarget.isPickable()) continue;
            
            Vec3 toTarget = targetEntity.position().subtract(entity.getEyePosition());
            if (toTarget.dot(forward) < 0) continue;
            
            if (entity.distanceToSqr(targetEntity) >= RADIUS * RADIUS) continue;
            
            if (!Utils.hasLineOfSight(level, entity.getEyePosition(), 
                    targetEntity.getBoundingBox().getCenter(), true)) continue;

            //伤害计算（主手+副手武器伤害叠加）
            float baseDamage = getDamage(spellLevel, entity, livingTarget);
            boolean wasBurning = livingTarget.isOnFire();
            
            if (DamageSources.applyDamage(targetEntity, baseDamage, damageSource)) {
                totalTargetsHit++;
                if (wasBurning) {
                    burningTargetsHit++;
                }
                
                int particleCount = wasBurning ? 50 : 30;
                MagicManager.spawnParticles(level, ParticleHelper.FIRE,
                    targetEntity.getX(),
                    targetEntity.getY() + targetEntity.getBbHeight() * 0.5f,
                    targetEntity.getZ(),
                    particleCount,
                    targetEntity.getBbWidth() * 0.5f,
                    targetEntity.getBbHeight() * 0.5f,
                    targetEntity.getBbWidth() * 0.5f,
                    0.03, false);
                
                EnchantmentHelper.doPostDamageEffects(entity, targetEntity);
            }
        }

        boolean mirrored = playerMagicData.getCastingEquipmentSlot()
            .equals(SpellSelectionManager.OFFHAND);
        
        MagicManager.spawnParticles(level, 
            new FlameStrikeParticleOptions(
                (float) forward.x, 
                (float) forward.y, 
                (float) forward.z, 
                mirrored, 
                false, 
                1.2f
            ),
            hitLocation.x, 
            hitLocation.y + 0.5, 
            hitLocation.z, 
            1, 0, 0, 0, 0, true
        );

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * 计算对目标的伤害。
     * 包含：法术强度 + 主手武器伤害 + 副手武器伤害 + 双手火焰附加等级
     * 燃烧目标额外 +150% 伤害（x2.5）
     */
    private float getDamage(int spellLevel, LivingEntity caster, LivingEntity target) {
        float base = getSpellPower(spellLevel, caster) 
                   + getTotalWeaponDamage(caster)           // 主手+副手武器伤害
                   + getTotalFireAspect(caster);              // 主手+副手火焰附加
        
        if (target.isOnFire()) {
            base *= BURNING_DAMAGE_MULTIPLIER;
        }
        
        return base;
    }

    // 计算主手 + 副手武器伤害总和
    private float getTotalWeaponDamage(LivingEntity entity) {
        float total = Utils.getWeaponDamage(entity, MobType.UNDEFINED); // 主手
        
        // 副手武器伤害
        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        total += getItemAttackDamage(offhand);
        
        return total;
    }

    // 从物品属性读取攻击伤害 
    private float getItemAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        
        double damage = 0;
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
            damage += modifier.getAmount();
        }
        // 注意：AttributeModifier 的 amount 已经包含了基础值和加成
        // 实际伤害 = amount（基础攻击 + 物品伤害 - 1，因为玩家基础攻击1已计入）
        return (float) damage;
    }

    // 计算主手 + 副手火焰附加等级总和
    private int getTotalFireAspect(LivingEntity entity) {
        int total = EnchantmentHelper.getFireAspect(entity); // 主手
        
        ItemStack offhand = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        total += EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, offhand);
        
        return total;
    }

    //提示信息 

    private String getDamageText(int spellLevel, LivingEntity entity) {
        if (entity != null) {
            float weaponDamage = getTotalWeaponDamage(entity);
            String plus = "";
            if (weaponDamage > 0) {
                plus = String.format(" (+%s)", Utils.stringTruncation(weaponDamage, 1));
            }
            String damage = Utils.stringTruncation(
                getSpellPower(spellLevel, entity) + weaponDamage, 1);
            return damage + plus;
        }
        return "" + getSpellPower(spellLevel, entity);
    }

    //动画 

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_HORIZONTAL_SWING_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }
}