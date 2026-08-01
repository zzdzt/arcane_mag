package com.zzdzt.arcanemag.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zzdzt.arcanemag.ArcaneMag;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * 模块注册表（core 提供，模块自注册）。
 *
 * enchant / spell / 饰品模块在 {@code FMLCommonSetupEvent} 阶段调用 register 方法自注册。
 * core 通过 {@link #getChargeModifiers} / {@link #getSpellCastHooks} 遍历调用，
 * 不直接引用模块具体类，实现依赖反转。
 *
 * 注册顺序 = 溢出处理优先级（先注册先处理）。charge_stacks 应先于 charge_overdrive 注册。
 */
public final class ModifierRegistry {

    private ModifierRegistry() {}

    private static final List<ChargeModifier> CHARGE_MODIFIERS = new ArrayList<>();
    private static final List<SpellCastHook> SPELL_CAST_HOOKS = new ArrayList<>();

    private static volatile boolean frozen = false;

    /**
     * 注册充能修改器。必须在 setup 阶段调用，setup 结束后冻结。
     */
    public static synchronized void registerChargeModifier(ChargeModifier modifier) {
        if (frozen) {
            ArcaneMag.LOGGER.warn("[ModifierRegistry] Frozen, rejected charge modifier: {}", modifier);
            return;
        }
        CHARGE_MODIFIERS.add(modifier);
    }

    /**
     * 注册施法流程钩子。必须在 setup 阶段调用，setup 结束后冻结。
     */
    public static synchronized void registerSpellCastHook(SpellCastHook hook) {
        if (frozen) {
            ArcaneMag.LOGGER.warn("[ModifierRegistry] Frozen, rejected spell cast hook: {}", hook);
            return;
        }
        SPELL_CAST_HOOKS.add(hook);
    }

    /**
     * 获取所有充能修改器（只读视图）。注册顺序即遍历顺序。
     */
    public static List<ChargeModifier> getChargeModifiers() {
        return Collections.unmodifiableList(CHARGE_MODIFIERS);
    }

    /**
     * 获取所有施法流程钩子（只读视图）。
     */
    public static List<SpellCastHook> getSpellCastHooks() {
        return Collections.unmodifiableList(SPELL_CAST_HOOKS);
    }

    /**
     * 冻结注册表，禁止后续注册。由 {@link FreezeHandler} 在 setup 结束时自动调用。
     */
    public static synchronized void freeze() {
        frozen = true;
        ArcaneMag.LOGGER.info("[ModifierRegistry] Frozen: {} charge modifiers, {} spell cast hooks",
                CHARGE_MODIFIERS.size(), SPELL_CAST_HOOKS.size());
    }

    /**
     * 在所有 mod 的 setup 完成后冻结注册表，防止运行时误注册。
     * 使用 FMLLoadCompleteEvent，确保 enchant/spell 等 AFTER 模块的 setup 注册先完成。
     */
    @EventBusSubscriber(modid = ArcaneMag.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class FreezeHandler {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            event.enqueueWork(ModifierRegistry::freeze);
        }
    }
}
