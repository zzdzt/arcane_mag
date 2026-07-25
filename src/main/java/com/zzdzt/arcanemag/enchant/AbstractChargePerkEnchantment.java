package com.zzdzt.arcanemag.enchant;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 充能机制 Perk 基类（独属弹匣配件）。
 *
 * 与 {@link AbstractPerkEnchantment} 的区别：
 * 通过 getAllowedAttachmentType() 限定为 EXTENDED_MAG 槽位。
 *
 * 三个充能机制（stacks / passive / overdrive）继承本类，
 * 通过 getExclusionGroup 返回 {@link PerkExclusionGroup#CHARGE_MECHANISM} 互相排斥，
 * 保证同一弹匣只能刻一个充能机制；但不排斥其他 Perk（auto_release / ammo_retention）。
 *
 * 附魔等级 = 数值：
 * - stacks：等级 = 额外释放次数上限
 * - passive：等级 = 被动充能速率档位
 * - overdrive：等级 = 过载法强倍率档位
 */
public abstract class AbstractChargePerkEnchantment extends AbstractPerkEnchantment {

    protected AbstractChargePerkEnchantment(Rarity rarity) {
        super(rarity);
    }

    /**
     * 充能机制仅可刻于弹匣（EXTENDED_MAG）槽位。
     */
    @Nullable
    @Override
    public AttachmentType getAllowedAttachmentType() {
        return AttachmentType.EXTENDED_MAG;
    }

    /**
     * 充能机制属于 {@link PerkExclusionGroup#CHARGE_MECHANISM} 互斥组：
     * 三个充能机制三选一，与其他组及无组 Perk 兼容。
     */
    @Nullable
    @Override
    public PerkExclusionGroup getExclusionGroup() {
        return PerkExclusionGroup.CHARGE_MECHANISM;
    }

    /**
     * 读取弹匣上本附魔的等级。
     *
     * @param magazine 弹匣 ItemStack（EXTENDED_MAG 槽位）
     * @return 附魔等级，0 表示未刻本机制
     */
    public int levelOnMagazine(ItemStack magazine) {
        if (magazine == null || magazine.isEmpty()) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(this, magazine);
    }
}
