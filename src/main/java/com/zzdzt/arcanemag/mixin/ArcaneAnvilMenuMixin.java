package com.zzdzt.arcanemag.mixin;

import com.zzdzt.arcanemag.config.ArcaneMagConfig;
import com.zzdzt.arcanemag.utils.AttachmentDataUtils;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu;
import io.redspace.ironsspellbooks.item.UpgradeOrbItem;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 扩展奥术铁砧：弹匣法术铭刻 + 枪械/配件升级法球
 */
@Mixin(ArcaneAnvilMenu.class)
public abstract class ArcaneAnvilMenuMixin extends ItemCombinerMenu {

    public ArcaneAnvilMenuMixin(
            @Nullable MenuType<?> type, int containerId,
            net.minecraft.world.entity.player.Inventory playerInventory,
            net.minecraft.world.inventory.ContainerLevelAccess access) {
        super(type, containerId, playerInventory, access);
    }

    @Inject(method = "createResult", at = @At("TAIL"), require = 0)
    private void onCreateResult(CallbackInfo ci) {
        ItemStack base = this.inputSlots.getItem(0);
        ItemStack modifier = this.inputSlots.getItem(1);
        if (base.isEmpty() || modifier.isEmpty()) return;

        ItemStack result = this.resultSlots.getItem(0);

        if (result.isEmpty()) {
            if (handleSpellInscription(base, modifier)) return;
            if (handleUpgradeOrb(base, modifier)) return;
        } else {
            if (AttachmentDataUtils.isMagazineAttachment(base) && AttachmentDataUtils.isSpellSource(modifier)) {
                SpellData spellData = AttachmentDataUtils.extractSpellFromSource(modifier);
                if (spellData != null) {
                    AttachmentDataUtils.calculateAndSetChargeMax(result, spellData.getSpell());
                }
            }
        }
    }

    /**
     * 弹匣法术铭刻：弹匣 + 卷轴/法术书
     */
    private boolean handleSpellInscription(ItemStack base, ItemStack modifier) {
        if (!AttachmentDataUtils.isMagazineAttachment(base)) return false;
        if (!AttachmentDataUtils.isSpellSource(modifier)) return false;

        SpellData spellData = AttachmentDataUtils.extractSpellFromSource(modifier);
        if (spellData == null) return false;

        ItemStack result = base.copy();
        AttachmentDataUtils.inscribeSpell(result, spellData.getSpell(), spellData.getLevel());
        this.resultSlots.setItem(0, result);
        return true;
    }

    /**
     * 升级法球：枪械/配件 + 法球
     */
    private boolean handleUpgradeOrb(ItemStack base, ItemStack modifier) {
        if (!AttachmentDataUtils.isValidUpgradeTarget(base)) return false;
        if (!(modifier.getItem() instanceof UpgradeOrbItem)) return false;

        boolean isAttachment = AttachmentDataUtils.isAttachment(base);
        boolean isGun = AttachmentDataUtils.isGun(base);

        if (isAttachment && !ArcaneMagConfig.ALLOW_UPGRADE_ORBS_ON_ATTACHMENTS.get()) return false;
        if (isGun && !ArcaneMagConfig.ALLOW_UPGRADE_ORBS_ON_GUNS.get()) return false;

        int maxOrbs = ArcaneMagConfig.MAX_UPGRADE_ORBS_PER_ITEM.get();
        if (!AttachmentDataUtils.canAddUpgradeOrb(base, maxOrbs)) return false;

        ItemStack result = base.copy();
        if (AttachmentDataUtils.addUpgradeOrb(result, (UpgradeOrbItem) modifier.getItem(), maxOrbs)) {
            this.resultSlots.setItem(0, result);
            return true;
        }
        return false;
    }
}