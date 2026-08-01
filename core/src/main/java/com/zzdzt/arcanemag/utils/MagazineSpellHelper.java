package com.zzdzt.arcanemag.utils;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MagazineSpellHelper {

    private MagazineSpellHelper() {}

    // 空集合单例，避免频繁创建
    private static final Map<UpgradeOrbType, Integer> EMPTY_ORBS = Collections.emptyMap();

    /**
     * 收集枪械所有配件
     */
    public static List<ItemStack> collectAttachments(ItemStack gunStack) {
        List<ItemStack> attachments = new ArrayList<>();
        if (!(gunStack.getItem() instanceof IGun gun)) return attachments;

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack attachment = gun.getAttachment(gunStack, type);
            if (!attachment.isEmpty()) {
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    /**
     * 获取弹匣配件
     */
    @Nullable
    public static ItemStack getMagazineAttachment(ItemStack gunStack) {
        if (!(gunStack.getItem() instanceof IGun gun)) return null;
        ItemStack magazine = gun.getAttachment(gunStack, AttachmentType.EXTENDED_MAG);
        return magazine.isEmpty() ? null : magazine;
    }

    /**
     * 从枪械提取法术 - 仅从弹匣读取
     */
    @Nullable
    public static SpellData extractSpell(ItemStack gunStack) {
        ItemStack magazine = getMagazineAttachment(gunStack);
        if (magazine == null) return null;
        return AttachmentDataUtils.getInscribedSpell(magazine);
    }

    public static boolean hasSpellMagazine(ItemStack gunStack) {
        return extractSpell(gunStack) != null;
    }

    /**
     * 聚合枪械所有升级法球（含枪械本身和所有配件）
     */
    public static Map<UpgradeOrbType, Integer> getAllUpgradeOrbs(ItemStack gunStack) {
        Map<UpgradeOrbType, Integer> total = new EnumMap<>(UpgradeOrbType.class);

        // 枪械自身
        mergeOrbs(total, AttachmentDataUtils.getUpgradeOrbs(gunStack));

        // 所有配件
        for (ItemStack attachment : collectAttachments(gunStack)) {
            mergeOrbs(total, AttachmentDataUtils.getUpgradeOrbs(attachment));
        }

        return total.isEmpty() ? EMPTY_ORBS : total;
    }

    public static int getTotalUpgradeOrbCount(ItemStack gunStack) {
        return getAllUpgradeOrbs(gunStack).values().stream()
            .mapToInt(Integer::intValue).sum();
    }

    private static void mergeOrbs(Map<UpgradeOrbType, Integer> target,
                                   Map<UpgradeOrbType, Integer> source) {
        source.forEach((type, count) -> target.merge(type, count, Integer::sum));
    }
}