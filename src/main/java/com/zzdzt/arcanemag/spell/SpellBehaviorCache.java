package com.zzdzt.arcanemag.spell;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.config.ArcaneMagConfig;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 法术行为缓存
 *
 * 策略（按优先级）：
 * 1. 用户配置白名单/黑名单（最高优先级，覆盖一切）
 * 2. 运行时自学习缓存（通过 SpellTargetDetector + PreCastTargetHelperMixin 自动检测）
 * 3. 默认保守回退
 *
 * 自学习：
 * PreCastTargetHelperMixin 拦截 Utils.preCastTargetHelper 调用 → SpellTargetDetector 设置旗标，
 * 如果法术调用了 preCastTargetHelper 但失败（返回 false），
 * 则自动用 GunCastTargetResolver 重试一次，成功后学习为 NEEDS_TARGET。
 */
public class SpellBehaviorCache {

    public enum SpellBehavior {
        NEEDS_TARGET,   // 需要 preCastTargetHelper / 准星目标
        SELF_BUFF,      // 自身增益（不调用 preCastTargetHelper）
        PROJECTILE,     // 投射物（自带目标逻辑）
        AOE,            // 范围效果
        SUMMON,         // 召唤
        TELEPORT,       // 传送
        UNKNOWN         // 未分类，采用默认安全策略
    }

    private static final Map<ResourceLocation, SpellBehavior> LEARNED_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取法术的已知行为。如果未缓存，返回 UNKNOWN。
     */
    public static SpellBehavior getBehavior(AbstractSpell spell) {
        return LEARNED_CACHE.getOrDefault(spell.getSpellResource(), SpellBehavior.UNKNOWN);
    }

    /**
     * 自学习：将法术的行为记入缓存。
     */
    public static void learnBehavior(ResourceLocation spellId, SpellBehavior behavior) {
        if (behavior == SpellBehavior.UNKNOWN) return;
        SpellBehavior existing = LEARNED_CACHE.get(spellId);
        // NEEDS_TARGET 具有最高缓存优先级（安全关键），其他类型允许更新
        if (existing == SpellBehavior.NEEDS_TARGET && behavior != SpellBehavior.NEEDS_TARGET) {
            return; // 不降级 NEEDS_TARGET
        }
        LEARNED_CACHE.put(spellId, behavior);
        ArcaneMag.LOGGER.debug("[SpellBehaviorCache] Learned {} -> {}", spellId, behavior);
    }

    // ==================== 目标需求判断（枪械施法用）====================

    /**
     * 检查法术是否需要目标选择（用于枪械施法流程）。
     *
     * 优先级：
     * 1. 配置白名单（NO_TARGET）-> 不需要
     * 2. 配置黑名单（REQUIRES_TARGET）-> 需要
     * 3. 运行时自学习缓存 -> 按缓存判断
     * 4. 默认：UNKNOWN（触发自学习流程）
     *
     * @return true=需要目标, false=不需要, null 用 SpellBehavior.UNKNOWN 表示"未知"
     */
    public static SpellBehavior checkTargetNeed(AbstractSpell spell) {
        String spellPath = spell.getSpellResource().getPath().toLowerCase();
        String fullId = spell.getSpellResource().toString().toLowerCase();

        // 1. 配置白名单：明确不需要目标
        List<? extends String> noTargetList = ArcaneMagConfig.NO_TARGET_SPELLS.get();
        if (noTargetList != null) {
            for (String pattern : noTargetList) {
                if (matchesPattern(spellPath, fullId, pattern)) {
                    return SpellBehavior.SELF_BUFF;
                }
            }
        }

        // 2. 配置黑名单：明确需要目标
        List<? extends String> targetList = ArcaneMagConfig.REQUIRES_TARGET_SPELLS.get();
        if (targetList != null) {
            for (String pattern : targetList) {
                if (matchesPattern(spellPath, fullId, pattern)) {
                    return SpellBehavior.NEEDS_TARGET;
                }
            }
        }

        // 3. 运行时自学习缓存
        SpellBehavior cached = LEARNED_CACHE.get(spell.getSpellResource());
        if (cached != null) {
            return cached;
        }

        // 4. 默认：UNKNOWN — 触发自学习流程
        return SpellBehavior.UNKNOWN;
    }

    /**
     * 检查法术是否必须依赖目标数据才能执行 onCast。
     * 这些法术在其 onCast 中直接读取 TargetEntityCastData，
     * 如果数据不存在则会直接返回 null / 不执行。
     *
     * 简化版：仅保留已知硬编码列表 + 已学习的 NEEDS_TARGET 检查。
     */
    public static boolean mustHaveTarget(AbstractSpell spell) {
        String path = spell.getSpellResource().getPath().toLowerCase();

        // 1. 已知在 onCast 中直接读取 TargetEntityCastData 的自定义法术
        if (path.equals("arcane_aim")
            || path.equals("guiding_bolt")
            || path.contains("jingtingjue")
            || path.contains("targeted_")
            || path.contains("_aim")) {
            return true;
        }

        // 2. 通过自学习缓存确认
        return getBehavior(spell) == SpellBehavior.NEEDS_TARGET;
    }

    //模式匹配

    public static boolean matchesPattern(String spellPath, String fullId, String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;
        String p = pattern.toLowerCase().trim();

        // 学派标记
        if (p.startsWith("@")) {
            String schoolName = p.substring(1);
            return spellPath.contains(schoolName) || fullId.contains(schoolName);
        }

        // 正则标记
        if (p.startsWith("/") && p.endsWith("/")) {
            String regex = p.substring(1, p.length() - 1);
            try {
                Pattern compiled = PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
                return compiled.matcher(spellPath).matches() || compiled.matcher(fullId).matches();
            } catch (Exception e) {
                ArcaneMag.LOGGER.warn("Invalid regex pattern in spell config: {}", pattern);
                return false;
            }
        }

        // 移除命名空间
        String cleanPattern = p.replace("irons_spellbooks:", "");
        String cleanPath = spellPath.replace("irons_spellbooks:", "");
        String cleanFullId = fullId.replace("irons_spellbooks:", "");

        // 通配符匹配
        if (cleanPattern.contains("*")) {
            String regex = cleanPattern.replace("*", ".*");
            Pattern compiled = PATTERN_CACHE.computeIfAbsent(regex, Pattern::compile);
            return compiled.matcher(cleanPath).matches() || compiled.matcher(cleanFullId).matches();
        }

        // 精确匹配
        return cleanPath.equals(cleanPattern) || cleanFullId.equals(cleanPattern)
            || cleanPath.endsWith("_" + cleanPattern) || cleanPath.startsWith(cleanPattern + "_");
    }

    // 辅助方法

    public static boolean isTeleportSpell(AbstractSpell spell) {
        String spellPath = spell.getSpellResource().getPath().toLowerCase();
        return spellPath.contains("teleport") || spellPath.contains("recall")
            || spellPath.contains("frost_step") || spellPath.contains("ascension")
            || spellPath.contains("blink") || spellPath.contains("step")
            || spellPath.equals("portal");
    }
}
