#!/usr/bin/env python3
"""Reskin realm-name tables across the design docs to the canonical Western-forward set.
Cultivation systems are unchanged — this only swaps the display-layer names."""
import sys, os

DESIGN = r"C:\Users\Gabri\IdeaProjects\Cerb\docs\design"

EDITS = {
    "POWER_SCALING.md": [
        ("**Realm flavor names** (display layer over the existing enum;",
         "**Realm names** (Western-forward, canonical per [NAMING.md](NAMING.md); display layer over the existing enum;"),
        # 2.1 table (Tier-anchored)
        ("| COMMON | Body Tempering |", "| COMMON | Awakened |"),
        ("| UNCOMMON | Qi Condensation |", "| UNCOMMON | Adept |"),
        ("| RARE | Foundation |", "| RARE | Champion |"),
        ("| EPIC | Core Formation |", "| EPIC | Ascendant |"),
        ("| LEGENDARY | Nascent Soul |", "| LEGENDARY | Paragon |"),
        ("| PERFECT | Soul Transformation |", "| PERFECT | Transcendent |"),
        ("| MYTHIC | Ascendant |", "| MYTHIC | Demigod |"),
        # 2.3 table (name(TIER)-anchored)
        ("| Body Tempering (COMMON) |", "| Awakened (COMMON) |"),
        ("| Qi Condensation (UNCOMMON) |", "| Adept (UNCOMMON) |"),
        ("| Foundation (RARE) |", "| Champion (RARE) |"),
        ("| Core Formation (EPIC) |", "| Ascendant (EPIC) |"),
        ("| Nascent Soul (LEGENDARY) |", "| Paragon (LEGENDARY) |"),
        ("| Soul Transformation (PERFECT) |", "| Transcendent (PERFECT) |"),
        ("| Ascendant (MYTHIC) |", "| Demigod (MYTHIC) |"),
    ],
    "WEAPONS.md": [
        ("| Realm (Tier) | Cultivation name |", "| Realm (Tier) | Realm name |"),
        ("| COMMON | Body Tempering |", "| COMMON | Awakened |"),
        ("| UNCOMMON | Qi Condensation |", "| UNCOMMON | Adept |"),
        ("| RARE | Foundation Establishment |", "| RARE | Champion |"),
        ("| EPIC | Core Formation |", "| EPIC | Ascendant |"),
        ("| LEGENDARY | Nascent Soul |", "| LEGENDARY | Paragon |"),
        ("| PERFECT | Soul Transformation |", "| PERFECT | Transcendent |"),
        ("| MYTHIC | Spirit Severing |", "| MYTHIC | Demigod |"),
        ("| GODLY | Dao Seeking |", "| GODLY | Empyrean |"),
        ("| PRIMORDIAL | Immortal Ascension |", "| PRIMORDIAL | Primordial |"),
    ],
    "CULTIVATION_REALMS.md": [
        ("| Realm name | Classic analog |", "| Realm name | Epithet (flavor) |"),
        ("**Stirring** | Qi Condensation", "**Mortal** | *Stirring*"),
        ("**Vesselborn** | Body Tempering", "**Awakened** | *Vesselborn*"),
        ("**Wellspring** | Foundation Establishment", "**Adept** | *Wellspring*"),
        ("**Tidecaller** | Core Formation", "**Champion** | *Tidecaller*"),
        ("**Embersoul** | Nascent Soul", "**Ascendant** | *Embersoul*"),
        ("**Rimeheart** | Soul Transformation", "**Paragon** | *Rimeheart*"),
        ("**Mirebound** | Void/Dao Seeking", "**Transcendent** | *Mirebound*"),
        ("**Ashen Lord** | Tribulation Transcendence", "**Demigod** | *Ashen Lord*"),
        ("**Maw-Eater** | Immortal Ascension", "**Empyrean** | *Maw-Eater*"),
        ("**Primordial Echo** | True Immortal / Dao Ancestor", "**Primordial** | *Primordial Echo*"),
    ],
}

missing = []
for fname, edits in EDITS.items():
    p = os.path.join(DESIGN, fname)
    text = open(p, encoding="utf-8").read()
    n = 0
    for old, new in edits:
        if old in text:
            text = text.replace(old, new, 1)
            n += 1
        else:
            missing.append(f"{fname}: NOT FOUND -> {old!r}")
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"{fname}: applied {n}/{len(edits)} replacements")

if missing:
    print("\n".join(missing))
    sys.exit(1)
print("OK - all realm-name replacements applied")
