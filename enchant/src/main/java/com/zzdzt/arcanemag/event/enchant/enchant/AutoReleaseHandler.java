package com.zzdzt.arcanemag.event.enchant;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.zzdzt.arcanemag.ArcaneMag;
import com.zzdzt.arcanemag.enchant.ArcaneMagEnchant;
import com.zzdzt.arcanemag.enchant.registry.EnchantmentRegistry;
import com.zzdzt.arcanemag.network.SpellCastHandler;
import com.zzdzt.arcanemag.utils.BulletImpactCapture;
import com.zzdzt.arcanemag.utils.MagazineSpellHelper;
import com.zzdzt.arcanemag.utils.ModChargeData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * auto_release触发处理器
 * 
 * 子弹命中 → 法术在命中目标上释放。
 * 仅当弹匣充能满、且法术非持续类时触发。
 *
 * 订阅 {@link EntityHurtByGunEvent.Post} 且优先级 {@link EventPriority#LOW}，
 * 确保 TACZ 的充能累加（默认优先级 {@code ModChargeEventHandler}）先执行——
 * "把充能打满的那一发"能在当发即放。
 */
@EventBusSubscriber(modid = ArcaneMagEnchant.MODID)
public final class AutoReleaseHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGunHurtEntityPost(EntityHurtByGunEvent.Post event) {
        // 仅服务端：施法与充能同步均走服务端
        if (event.getLogicalSide() != LogicalSide.SERVER) return;

        // 攻击者必须是玩家（法术以玩家身份释放）
        if (!(event.getAttacker() instanceof ServerPlayer player)) return;

        // 主手必须是枪
        ItemStack gun = player.getMainHandItem();
        if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) return;

        // 1. 枪上配件是否带 auto_release
        if (EnchantmentRegistry.AUTO_RELEASE.get().levelOnGun(gun) <= 0) return;

        // 2. 弹匣是否铭刻法术
        SpellData sd = MagazineSpellHelper.extractSpell(gun);
        if (sd == null) return;

        // 3. 持续类不自动释放
        if (sd.getSpell().getCastType() == CastType.CONTINUOUS) return;

        // 4. 充能门控：
        //   - 首次施放（无活跃 recast 实例）需充能满 —— 要消耗充能，蓄满才能起手。
        //   - recast 段（已有活跃 RecastInstance）不耗充能，无需重新蓄满 —— 后续命中直接触发下一段。
        ItemStack mag = MagazineSpellHelper.getMagazineAttachment(gun);
        if (mag == null) return;
        boolean hasActiveRecast = MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(sd.getSpell());
        if (!hasActiveRecast && !ModChargeData.isFull(mag)) return;

        // 5. 不抢断进行中的施法（手动/自动互斥）
        if (SpellCastHandler.isPlayerCasting(player)) return;

        // 6. 命中实体（实体锚点）
        Entity hurt = event.getHurtEntity();
        LivingEntity targetHint = (hurt instanceof LivingEntity le) ? le : null;

        // 7. 精确命中点（点锚点，带 fallback）
        Vec3 point = null;
        if (event.getBullet() instanceof EntityKineticBullet bullet) {
            LivingEntity fallback = targetHint != null ? targetHint : player;
            point = BulletImpactCapture.consume(bullet, fallback);
        }

        SpellCastHandler.handleAutoRelease(
            sd.getSpell().getSpellResource(), sd.getLevel(), player, targetHint, point, hasActiveRecast);
    }
}
