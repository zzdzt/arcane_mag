### Description

Integrates TaCZ (Timeless and Classics Zero) with Iron's Spells 'n Spellbooks through a magazine-based Weapon Mod system

### Features
- Spellbound Magazines — Spells are bound directly to gun magazines. Swap magazines to instantly switch your active spell loadout
- Combat-Fueled Casting — Generate mod charge through dealing damage. Expend accumulated charge to cast spells using your gun.
    1. **Charge Stacking** :Store up to spell charges (configurable). Build up a reserve and unleash multiple casts in sequence.
    2. **Overdrive Overcharge** — Continue attacking at full charge to enter *Overdrive*, empowering your next spell cast.
- Custom Spells - Add custom spells. Some designed for tacz synergy, some original Iron's Spells 'n Spellbooks-style spells.

Inspired by the *Remnant series*

---
#### How to Use

1. Use the **Arcane Anvil** to imbue your **magazine** with a spell from Iron's Spells 'n Spellbooks
2. Load the imbued magazine into a gun
3. Once fully charged, press **F** (default) to cast your spell! 
4. *(Optional)* Enable **Overdrive** and **Charge Stacking** per-spell via config pack.
  ```
config/
└── arcane_mag/
    └── spell_charge_mechanisms/
        ├── mod namespaces (e.g., "arcane_mag", "irons_spellbooks")/
        │   └── spell IDs.json
        └── irons_spellbooks/
            └── fireball.json                                
```
> ⚠️**Keybind Conflict:** The default cast key **F** conflicts with *Swap Item with Offhand*. Please rebind one in **Options → Controls** beforehand.

#### Download

You can download the latest version of the mod from Modrinth or CurseForge.

<div align="center">

[![Download on Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/arcanemag)
[![Download on CurseForge](https://img.shields.io/badge/Download-CurseForge-f16436?style=for-the-badge&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/arcanemag)

</div>

#### Required Dependencies:
The following minimum versions are required for proper functionality:
- [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks)：`1.20.1-3.16.2` or later
- [Timeless and Classics Zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)：`1.1.7-hotfix2` or later
