#!/usr/bin/env python3
import json, os

OUT = r"C:\Users\Gabri\AppData\Local\Temp\claude\C--Users-Gabri-IdeaProjects-Cerb\d3c4d486-3c80-49ad-bbba-d94bef64bbc2\tasks\wscxdhjkh.output"
DESIGN = r"C:\Users\Gabri\IdeaProjects\Cerb\docs\design"
DATE = "2026-06-08"

res = json.load(open(OUT, encoding="utf-8"))["result"]

def strip_preamble(md):
    if not md:
        return "_(missing section)_"
    lines = md.splitlines()
    for i, l in enumerate(lines):
        if l.lstrip().startswith("#"):
            return "\n".join(lines[i:]).strip()
    return md.strip()

def write_doc(fname, title, body):
    clean = strip_preamble(body)
    if not clean.startswith("# "):
        clean = (f"# Cerberus — {title}\n\n*Generated {DATE}. Companion to the "
                 f"[master design plan](CERBERUS_DESIGN_PLAN.md) and [index](README.md).*\n\n---\n\n" + clean)
    p = os.path.join(DESIGN, fname)
    with open(p, "w", encoding="utf-8") as f:
        f.write(clean + "\n")
    print(f"WROTE {fname}: {len(clean)} chars -> {os.path.getsize(p)} bytes")

write_doc("TALISMANS.md", "Talisman System", res.get("talismans"))
write_doc("ECHO_FAMILIES.md", "Echo Families & Orders", res.get("families"))
write_doc("README.md", "Design — Index & Status", res.get("index"))
