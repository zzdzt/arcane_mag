package com.zzdzt.arcanemag.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 施法流程钩子接口。
 *
 * 附魔 Handler（如 ArcaneFocus / Brace）实现此接口并自注册到 {@link ModifierRegistry}，
 * 让 core 的施法流程通过注册表遍历调用，而非硬编码引用具体 Handler 类。
 *
 * 未来饰品模块也可实现此接口参与施法流程。
 */
public interface SpellCastHook {

    /**
     * 施法前调用（用于施加临时属性、消耗蓄势等）。
     */
    default void onPreCast(ServerPlayer player, ItemStack gunStack) {}

    /**
     * 施法成功后调用（用于触发附魔效果，如 ArcaneFocus 标记下次爆头加成）。
     */
    default void onPostCast(ServerPlayer player, ItemStack gunStack) {}

    /**
     * 施法结束/中断后调用（用于移除临时属性等）。不需要 gunStack。
     */
    default void onCastEnd(ServerPlayer player) {}
}
