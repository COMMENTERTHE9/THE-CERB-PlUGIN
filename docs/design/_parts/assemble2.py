#!/usr/bin/env python3
import json, os

OUT = r"C:\Users\Gabri\AppData\Local\Temp\claude\C--Users-Gabri-IdeaProjects-Cerb\7f12d083-1b83-4625-95f6-30b3865775f2\tasks\w6w31s24m.output"
DESIGN = r"C:\Users\Gabri\IdeaProjects\Cerb\docs\design"
DATE = "2026-06-08"

with open(OUT, encoding="utf-8") as f:
    data = json.load(f)
res = data["result"]

def strip_preamble(md):
    if not md:
        return "_(missing section)_"
    lines = md.splitlines()
    for i, l in enumerate(lines):
        if l.lstrip().startswith("#"):
            return "\n".join(lines[i:]).strip()
    return md.strip()

docs = {
    "WEAPONS.md": ("Weapons System", res.get("weapons")),
    "GEAR.md": ("Gear System", res.get("gear")),
    "CULTIVATION_REALMS.md": ("Cultivation Realm & Ascension System", res.get("cultivation")),
    "POWER_SCALING.md": ("Cultivation Power-Scaling Math", res.get("power")),
    "CULTIVATION_INTEGRATION.md": ("Cultivation Integration & Doctrine Change", res.get("synthesis")),
}

os.makedirs(DESIGN, exist_ok=True)
for fname, (title, body) in docs.items():
    clean = strip_preamble(body)
    header = (f"# Cerberus — {title}\n\n"
              f"*Generated {DATE} (cultivation/xianxia design pass). Companion to the "
              f"[master design plan](CERBERUS_DESIGN_PLAN.md). Reflects the **controlled-hyperscaling** "
              f"doctrine — see [CULTIVATION_INTEGRATION.md](CULTIVATION_INTEGRATION.md) for what this "
              f"overturns from the original plan.*\n\n---\n\n")
    p = os.path.join(DESIGN, fname)
    with open(p, "w", encoding="utf-8") as f:
        f.write(header + clean + "\n")
    print(f"WROTE {fname}: {len(clean)} chars -> {os.path.getsize(p)} bytes")
