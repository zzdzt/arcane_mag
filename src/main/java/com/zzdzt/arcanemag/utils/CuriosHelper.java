package com.zzdzt.arcanemag.utils;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Curios 饰品装备检查工具
 * 无 Curios 时自动降级返回 false，只靠 JSON 白名单生效
 */
public final class CuriosHelper {

    private CuriosHelper() {}

    /**
     * 检查玩家 Curios 槽位中是否佩戴了指定 Tag 的物品
     * @param entity 玩家实体
     * @param tagKey 物品标签
     * @return true 如果在任意 Curios 槽位中佩戴了匹配 Tag 的物品
     */
    public static boolean hasCurioWithTag(LivingEntity entity, TagKey<Item> tagKey) {
        if (!ModList.get().isLoaded("curios")) {
            return false;
        }
        try {
            return CuriosApi.getCuriosInventory(entity).resolve()
                .map(handler -> handler.isEquipped(stack -> stack.is(tagKey)))
                .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}
