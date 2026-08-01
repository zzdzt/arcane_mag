package com.zzdzt.arcanemag.item;

import com.zzdzt.arcanemag.ArcaneMag;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class HeartItem extends Item {

    private final int baseUseDuration;
    private final int baseCooldownTicks;
    public static final String COOLDOWN_ID = "arcane_mag:heart_cooldown";

    public HeartItem(Properties properties, int baseUseDurationTicks, int baseCooldownTicks) {
        super(properties);
        this.baseUseDuration = baseUseDurationTicks;
        this.baseCooldownTicks = baseCooldownTicks;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return baseUseDuration;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (isOnCooldown(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            applyHeartEffect(serverPlayer);
            applyCooldown(serverPlayer);
            playUseSound(serverPlayer);
        }
        return stack;
    }

    protected abstract void applyHeartEffect(ServerPlayer player);

    protected SoundEvent getUseSound() {
        return SoundEvents.EMPTY;
    }

    protected void playUseSound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                getUseSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    protected boolean isOnCooldown(ServerPlayer player) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        return magicData.getPlayerCooldowns().getSpellCooldowns().containsKey(COOLDOWN_ID);
    }

    protected void applyCooldown(ServerPlayer player) {
        MagicData magicData = MagicData.getPlayerMagicData(player);
        int actualCooldown = Utils.applyCooldownReduction(baseCooldownTicks, player);
        magicData.getPlayerCooldowns().addCooldown(COOLDOWN_ID, actualCooldown);
        magicData.getPlayerCooldowns().syncToPlayer(player);
    }

    public int getBaseCooldownTicks() {
        return baseCooldownTicks;
    }

    public String getCooldownId() {
        return COOLDOWN_ID;
    }
}