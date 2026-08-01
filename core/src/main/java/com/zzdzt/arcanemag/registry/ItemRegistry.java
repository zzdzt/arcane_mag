package com.zzdzt.arcanemag.registry;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.item.ArcaneStaffItem;
import com.zzdzt.arcanemag.item.BlankHeartItem;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.util.ItemPropertiesHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * core 物品注册（法杖 + 空白之心）。
 *
 * 法术卷轴已移至 {@link SpellItemRegistry}（spell 模块），
 * 避免 core 反向依赖 spell 的 SpellRegistry。
 */
public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneMag.MODID);

    // ========== 奥术法杖 ==========
    public static final RegistryObject<Item> ARCANE_STAFF = ITEMS.register("arcane_staff",
        () -> new ArcaneStaffItem(
            ItemPropertiesHelper.equipment(1).rarity(net.minecraft.world.item.Rarity.EPIC),
            5.0f,    // 攻击伤害
            -3.0f,   // 攻击速度（标准法杖速度）
            // 额外属性
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.20, AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.15, AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10, AttributeModifier.Operation.MULTIPLY_BASE)
        )
    );

    // ========== 空白之心 ==========
    public static final RegistryObject<Item> BLANK_HEART = ITEMS.register("blank_heart",
        () -> new BlankHeartItem(
            new Item.Properties()
                .stacksTo(1)
                .rarity(net.minecraft.world.item.Rarity.RARE)
        )
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
