package com.zzdzt.arcanemag.spell.thunderstream;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.registry.EntityRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ThunderStream extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ArcaneMag.MODID, "thunder_stream");

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.LIGHTNING_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(30)
            .build();

    private static final UUID SLOWDOWN_UUID = UUID.fromString("ad3fc7a7-4f80-4712-b782-fb593b66af2e");

    private static final int TIER_1_TICKS = 100;
    private static final int TIER_2_TICKS = 240;
    private static final float[] TIER_MULTIPLIERS = {1.0f, 2.0f, 4.0f};

    public ThunderStream() {
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 22;
        this.manaCostPerLevel = 0;
        this.castTime = 20 * 25;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        // 使用 getSpellPower() 显示 spell_power 加成后的实际基础伤害
        float baseDamage = getSpellPower(spellLevel, caster);
        return List.of(
            Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(baseDamage * TIER_MULTIPLIERS[0], 1))
        );
    }

    private float getBaseDamage(int spellLevel, LivingEntity entity) {
        return getSpellPower(spellLevel, entity);
    }

    public static float getCurrentDamageMultiplier(int castTicks) {
        if (castTicks < TIER_1_TICKS) return TIER_MULTIPLIERS[0];
        if (castTicks < TIER_2_TICKS) return TIER_MULTIPLIERS[1];
        return TIER_MULTIPLIERS[2];
    }

    public static int getCurrentTier(int castTicks) {
        if (castTicks < TIER_1_TICKS) return 0;
        if (castTicks < TIER_2_TICKS) return 1;
        return 2;
    }

    private float getRange() {
        return 24f;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.CONTINUOUS;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST_ONE_HANDED;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.FINISH_ANIMATION;
    }

    @Override
    public final ICastDataSerializable getEmptyCastData() {
        return new ThunderStreamCastData();
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(playerMagicData.getAdditionalCastData() instanceof ThunderStreamCastData)) {
            var castData = new ThunderStreamCastData();
            var beam = new ThunderStreamEntity(EntityRegistry.THUNDER_STREAM.get(), level, entity);

            var beamPos = calculateBeamPosition(entity);
            beam.moveTo(beamPos.x, beamPos.y, beamPos.z, entity.getYRot(), entity.getXRot());
            beam.setup(0, 0, getRange(), 0.25f);
            beam.updateLength(getRange(), level);
            beam.setBaseDamage(getBaseDamage(spellLevel, entity));

            level.addFreshEntity(beam);

            castData.setEntity(beam);
            playerMagicData.setAdditionalCastData(castData);

            if (!level.isClientSide) {
                applySlowdown(entity);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, @Nullable MagicData playerMagicData) {
        if (playerMagicData instanceof MagicData && playerMagicData.getAdditionalCastData() instanceof ThunderStreamCastData beamData && level instanceof ServerLevel server) {
            var dataEntity = beamData.getEntity(server);
            if (dataEntity instanceof ThunderStreamEntity beam) {
                var beamPos = calculateBeamPosition(entity);
                beam.moveTo(beamPos.x, beamPos.y, beamPos.z, entity.getYRot(), entity.getXRot());
                beam.updateLength(getRange(), level);
                beam.setCastTicks(beamData.getCastTicks());
                beamData.incrementCastTicks();
            }
        }
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);
    }

    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData, boolean cancelled) {
        if (playerMagicData.getAdditionalCastData() instanceof ThunderStreamCastData castData && level instanceof ServerLevel serverLevel) {
            var beam = castData.getEntity(serverLevel);
            if (beam != null) beam.discard();
        }
        if (!level.isClientSide) {
            removeSlowdown(entity);
        }
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
    }

    private void applySlowdown(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(SLOWDOWN_UUID);
        double baseSpeed = attribute.getBaseValue();
        var modifier = new AttributeModifier(
            SLOWDOWN_UUID,
            "Thunder Stream Slowdown",
            -baseSpeed * 0.90,
            AttributeModifier.Operation.ADDITION
        );
        attribute.addTransientModifier(modifier);
    }

    private void removeSlowdown(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(SLOWDOWN_UUID);
    }

    private static Vec3 calculateBeamPosition(LivingEntity entity) {
        return entity.getEyePosition(1.0f).add(0, -0.2, 0).add(entity.getLookAngle().scale(0.5f));
    }

    public static class ThunderStreamCastData implements ICastDataSerializable {
        private UUID entityId;
        private int castTicks = 0;

        public void setEntity(Entity entity) {
            entityId = entity.getUUID();
        }

        public Entity getEntity(ServerLevel level) {
            return level.getEntity(entityId);
        }

        public int getCastTicks() {
            return castTicks;
        }

        public void incrementCastTicks() {
            castTicks++;
        }

        @Override
        public void writeToBuffer(FriendlyByteBuf buf) {
            buf.writeUUID(entityId);
            buf.writeVarInt(castTicks);
        }

        @Override
        public void readFromBuffer(FriendlyByteBuf buf) {
            entityId = buf.readUUID();
            castTicks = buf.readVarInt();
        }

        @Override
        public void reset() {
            entityId = null;
            castTicks = 0;
        }

        @Override
        public CompoundTag serializeNBT() {
            var tag = new CompoundTag();
            tag.putUUID("Entity", entityId);
            tag.putInt("CastTicks", castTicks);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            entityId = nbt.getUUID("Entity");
            castTicks = nbt.getInt("CastTicks");
        }
    }
}