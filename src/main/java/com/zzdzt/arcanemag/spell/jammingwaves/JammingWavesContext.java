package com.zzdzt.arcanemag.spell.jammingwaves;

import io.redspace.ironsspellbooks.api.magic.MagicData;

/**
 * 干扰波纹施法上下文数据。
 * 
 * 封装了施法期间的 MagicData 和法术等级，供各 Handler 共享使用。
 */
public final class JammingWavesContext {
    public final MagicData magicData;
    public final int spellLevel;

    public JammingWavesContext(MagicData magicData, int spellLevel) {
        this.magicData = magicData;
        this.spellLevel = spellLevel;
    }
}
