package com.zzdzt.arcanemag.utils;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.UpgradeOrbItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 枪械配件数据读写工具
 * 
 * - 仅弹匣配件（EXTENDED_MAG）可铭刻法术，每个弹匣一个法术
 * - 所有配件和枪械均可接受升级法球
 * - 升级法球提供法术强度属性加成
 */
public final class AttachmentDataUtils {

    // 升级法球 NBT
    public static final String NBT_UPGRADE_ORBS = "ArcaneMagUpgradeOrbs";
    public static final String NBT_ORB_TYPE = "Type";
    public static final String NBT_ORB_COUNT = "Count";

    private AttachmentDataUtils() {}

    // 类型判断 

    public static boolean isAttachment(ItemStack stack) {
        return stack != null && !stack.isEmpty() 
            && IAttachment.getIAttachmentOrNull(stack) != null;
    }

    public static boolean isGun(ItemStack stack) {
        return stack != null && !stack.isEmpty() 
            && stack.getItem() instanceof IGun;
    }

    public static boolean isValidUpgradeTarget(ItemStack stack) {
        return isAttachment(stack) || isGun(stack);
    }

    public static AttachmentType getAttachmentType(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment != null ? attachment.getType(stack) : null;
    }

    public static boolean isMagazineAttachment(ItemStack stack) {
        return getAttachmentType(stack) == AttachmentType.EXTENDED_MAG;
    }

    // 法术铭刻 

    public static boolean hasInscribedSpell(ItemStack stack) {
        return ISpellContainer.isSpellContainer(stack);
    }

    public static SpellData getInscribedSpell(ItemStack stack) {
        if (!ISpellContainer.isSpellContainer(stack)) return null;
        ISpellContainer container = ISpellContainer.get(stack);
        return container.isEmpty() ? null : container.getSpellAtIndex(0);
    }

    /**
     * 铭刻法术 - 使用铁魔法标准 ISpellContainer
     * 同时计算并写入充能条上限（基于法术冷却时间）
     */
    public static void inscribeSpell(ItemStack stack, AbstractSpell spell, int level) {
        ISpellContainer.createScrollContainer(spell, level, stack);
        // 计算充能条上限
        calculateAndSetChargeMax(stack, spell);
    }

    /**
     * 根据法术冷却时间计算充能条上限
     * chargeMax = baseChargeMax * (spellCooldownSeconds / cdReferenceSeconds)
     *
     * 充能机制（stacks/passive/overdrive）由弹匣附魔决定，铭刻时不再写入机制标记。
     */
    public static void calculateAndSetChargeMax(ItemStack stack, AbstractSpell spell) {
        try {
            double cooldownSeconds = spell.getSpellCooldown() / 20.0;
            double baseChargeMax = com.zzdzt.arcanemag.config.ArcaneMagConfig.CHARGE_BASE_MAX.get();
            double cdReference = com.zzdzt.arcanemag.config.ArcaneMagConfig.CHARGE_CD_REFERENCE.get();
            double chargeMaxMultiplier = com.zzdzt.arcanemag.config.ArcaneMagConfig.CHARGE_MAX_MULTIPLIER.get();
            double chargeMax = baseChargeMax * (cooldownSeconds / cdReference) * chargeMaxMultiplier;
            ModChargeData.setMax(stack, chargeMax);
            ModChargeData.setCharge(stack, 0.0);
            ModChargeData.setStacks(stack, 0);
            ModChargeData.setOverdriveStacks(stack, 0);
        } catch (Exception e) {
            com.zzdzt.arcanemag.ArcaneMag.LOGGER.warn(
                "[ArcaneMag] Failed to calculate charge max for spell {}: {}",
                spell.getSpellName(), e.getMessage()
            );
        }
    }

    public static boolean isSpellSource(ItemStack stack) {
        return ISpellContainer.isSpellContainer(stack);
    }

    public static SpellData extractSpellFromSource(ItemStack stack) {
        if (!isSpellSource(stack)) return null;
        ISpellContainer container = ISpellContainer.get(stack);
        return container.isEmpty() ? null : container.getSpellAtIndex(0);
    }

    // ========== 升级法球 ==========

    public static boolean isUpgradeOrb(ItemStack stack) {
        return stack != null && !stack.isEmpty() 
            && stack.getItem() instanceof UpgradeOrbItem;
    }

    public static UpgradeOrbType getOrbType(ItemStack stack) {
        if (!isUpgradeOrb(stack)) return null;
        return UpgradeOrbType.fromItemId(stack.getItem().getDescriptionId());
    }

    public static Map<UpgradeOrbType, Integer> getUpgradeOrbs(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return Collections.emptyMap();  

        ListTag orbsList = tag.getList(NBT_UPGRADE_ORBS, Tag.TAG_COMPOUND);
        if (orbsList.isEmpty()) return Collections.emptyMap();  

        Map<UpgradeOrbType, Integer> result = new EnumMap<>(UpgradeOrbType.class);
        for (int i = 0; i < orbsList.size(); i++) {
            CompoundTag orbTag = orbsList.getCompound(i);
            UpgradeOrbType type = UpgradeOrbType.fromName(orbTag.getString(NBT_ORB_TYPE));
            if (type != null) {
                result.put(type, orbTag.getInt(NBT_ORB_COUNT));
            }
        }
        return result.isEmpty() ? Collections.emptyMap() : result;
    }
    public static int getTotalUpgradeOrbCount(ItemStack stack) {
        return getUpgradeOrbs(stack).values().stream()
            .mapToInt(Integer::intValue).sum();
    }

    public static boolean canAddUpgradeOrb(ItemStack stack, int maxOrbs) {
        return getTotalUpgradeOrbCount(stack) < maxOrbs;
    }

    public static boolean addUpgradeOrb(ItemStack stack, UpgradeOrbItem orb, int maxOrbs) {
        if (!canAddUpgradeOrb(stack, maxOrbs)) return false;
        
        UpgradeOrbType type = getOrbType(new ItemStack(orb));
        if (type == null) return false;

        CompoundTag tag = stack.getOrCreateTag();
        ListTag orbsList = tag.getList(NBT_UPGRADE_ORBS, Tag.TAG_COMPOUND);
        
        // 查找是否已有同类型
        for (int i = 0; i < orbsList.size(); i++) {
            CompoundTag orbTag = orbsList.getCompound(i);
            if (orbTag.getString(NBT_ORB_TYPE).equals(type.name())) {
                orbTag.putInt(NBT_ORB_COUNT, orbTag.getInt(NBT_ORB_COUNT) + 1);
                return true;
            }
        }
        
        // 新增类型
        CompoundTag newOrb = new CompoundTag();
        newOrb.putString(NBT_ORB_TYPE, type.name());
        newOrb.putInt(NBT_ORB_COUNT, 1);
        orbsList.add(newOrb);
        tag.put(NBT_UPGRADE_ORBS, orbsList);
        return true;
    }
}