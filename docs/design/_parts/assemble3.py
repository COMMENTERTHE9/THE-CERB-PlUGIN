#!/usr/bin/env python3
import json, os

OUT = r"C:\Users\Gabri\AppData\Local\Temp\claude\C--Users-Gabri-IdeaProjects-Cerb\d3c4d486-3c80-49ad-bbba-d94bef64bbc2\tasks\wveoedg8u.output"
DESIGN = r"C:\Users\Gabri\IdeaProjects\Cerb\docs\design"
DATE = "2026-06-08"

with open(OUT, encoding="utf-8") as f:
    res = json.load(f)["result"]

def strip_preamble(md):
    if not md:
        return "_(missing section)_"
    lines = md.splitlines()
    for i, l in enumerate(lines):
        if l.lstrip().startswith("#"):
            return "\n".join(lines[i:]).strip()
    return md.strip()

header = ("# Cerberus — Mounts & Vehicles\n\n"
          "*Generated " + DATE + ". Companion to [GEAR.md](GEAR.md) and the [master design plan](CERBERUS_DESIGN_PLAN.md). "
          "Western-forward naming per [NAMING.md](NAMING.md); controlled-hyperscaling per [POWER_SCALING.md](POWER_SCALING.md). "
          "Uses the new `mount` summon slot from the 20-slot gear layout.*\n\n---\n\n")

order = [
    ("Overview & Integration", res.get("synthesis")),
    (None, res.get("mounts")),
    (None, res.get("vehicles")),
    (None, res.get("systems")),
]

body_parts = []
for label, md in order:
    section = strip_preamble(md)
    if label:
        # synthesis section: give it a clean H2 if it doesn't lead with one
        if not section.lstrip().startswith("#"):
            section = f"## {label}\n\n{section}"
    body_parts.append(section)

doc = header + "\n\n---\n\n".join(body_parts) + "\n"
p = os.path.join(DESIGN, "MOUNTS_AND_VEHICLES.md")
with open(p, "w", encoding="utf-8") as f:
    f.write(doc)

for k in ("mounts", "vehicles", "systems", "synthesis"):
    print(f"{k}: {len(res.get(k) or '')} chars")
print("WROTE", p, f"({os.path.getsize(p)} bytes)")
