package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.config.EnchantConfig;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Perk 附魔基类。
 *
 * 统一所有行为型 Perk 的公共逻辑：
 * 
 * canEnchant / canApplyAtEnchantingTable 限定 TACZ 配件（IAttachment）
 * isAllowedOnBooks = true（铁砧 + 附魔书路线）
 * checkCompatibility 基础 Perk 之间默认兼容（互斥仅限充能机制子组）
 * 单配件 ArcaneMag 附魔总数上限（不影响其他模组附魔）
 * levelOnGun 遍历枪所有配件取本附魔最高等级
 * 
 *
 * 子类只需定义：getMaxLevel / getMinCost / getMaxCost，以及具体的触发 Handler。
 */
public abstract class AbstractPerkEnchantment extends Enchantment implements IPerk {

    /**
     * 自定义 category：vanilla 逻辑不会通过 category 意外匹配到本附魔。
     * 实际适用性完全由 canEnchant / canApplyAtEnchantingTable 控制。
     */
    public static final EnchantmentCategory PERK_CATEGORY =
        EnchantmentCategory.create("arcane_perk", item -> false);

    protected AbstractPerkEnchantment(Rarity rarity) {
        super(rarity, PERK_CATEGORY, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 本 Perk 限定使用的配件槽位。返回 null 表示任意配件均可刻。
     * 子类覆写以限定槽位。
     */
    @Nullable
    public AttachmentType getAllowedAttachmentType() {
        return null;
    }

    @Override
    public boolean canEnchant(@NotNull ItemStack stack) {
        IAttachment att = IAttachment.getIAttachmentOrNull(stack);
        if (att == null) return false;
        AttachmentType allowed = getAllowedAttachmentType();
        if (allowed != null && att.getType(stack) != allowed) return false;
        return withinEnchantmentLimit(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(@NotNull ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    /** ==================== 互斥 ====================
     * 本 Perk 所属的互斥组。返回 null 表示不属于任何互斥组（与所有附魔兼容）。
     * 子类覆写以加入某个互斥组；同组 Perk 互相排斥。
     */
    @Nullable
    public PerkExclusionGroup getExclusionGroup() {
        return null;
    }

    /**
     * 互斥判定：仅当两者同属一个非空互斥组时排斥。
     * 无组的基础 Perk 之间、不同组之间、以及与原版/其他模组附魔均兼容。
     */
    @Override
    public boolean checkCompatibility(@NotNull Enchantment other) {
        PerkExclusionGroup group = getExclusionGroup();
        if (group == null) return true;
        if (other instanceof AbstractPerkEnchantment otherPerk) {
            return group != otherPerk.getExclusionGroup();
        }
        return true;
    }

    /**==================== 数量上限 ====================
     * 统计物品上 ArcaneMag 命名空间的附魔总数。
     * 仅统计 namespace = "arcane_mag" 的附魔，不干扰其他模组。
     */
    public static int countModEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int count = 0;
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        for (Enchantment enchantment : enchantments.keySet()) {
            var key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (key != null && ArcaneMag.MODID.equals(key.getNamespace())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 检查配件上 ArcaneMag 附魔是否未达上限。
     * 子类覆写 canEnchant 时应调用本方法。
     */
    protected boolean withinEnchantmentLimit(ItemStack stack) {
        int max = EnchantConfig.MAX_ENCHANTMENTS_PER_ATTACHMENT.get();
        return countModEnchantments(stack) < max;
    }

    /**==================== 工具方法 ====================
     * 遍历枪所有已安装配件，取本附魔的最高等级。
     *
     * @param gunStack 枪械 ItemStack
     * @return 最高等级，0 表示枪上无此附魔
     */
    public int levelOnGun(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) return 0;
        int max = 0;
        for (ItemStack attachment : MagazineSpellHelper.collectAttachments(gunStack)) {
            int lvl = EnchantmentHelper.getItemEnchantmentLevel(this, attachment);
            if (lvl > max) max = lvl;
        }
        return max;
    }
}
