#!/usr/bin/env python3
# Assembles the Cerberus design docs from the workflow result JSON + the two re-run part files.
import json, os
from collections import OrderedDict

OUT = r"C:\Users\Gabri\AppData\Local\Temp\claude\C--Users-Gabri-IdeaProjects-Cerb\7f12d083-1b83-4625-95f6-30b3865775f2\tasks\wowulyrqc.output"
ROOT = r"C:\Users\Gabri\IdeaProjects\Cerb"
DESIGN = os.path.join(ROOT, "docs", "design")
PARTS = os.path.join(DESIGN, "_parts")
DATE = "2026-06-08"

with open(OUT, encoding="utf-8") as f:
    data = json.load(f)
res = data["result"]
plans = res.get("plans") or []
synth = res.get("synth") or {}
critique = res.get("critique") or {}

def find_plan(keyword):
    for p in plans:
        if keyword.lower() in (p.get("domainTitle", "") or "").lower():
            return p
    return None

def read_part(name):
    p = os.path.join(PARTS, name)
    return open(p, encoding="utf-8").read().strip() if os.path.exists(p) else f"_(missing part: {name})_"

def extract_open(md):
    low = md.lower()
    idx = low.rfind("### open decisions")
    if idx == -1:
        return []
    tail = md[idx:]
    return [l.strip()[2:].strip() for l in tail.splitlines() if l.strip().startswith("- ")]

bosses_md = read_part("bosses.md")
loops_md = read_part("loops.md")

# Display order: ('file', md) or ('plan', keyword)
order = [
    ("file", loops_md),
    ("plan", "Bestiary"),
    ("file", bosses_md),
    ("plan", "Gear"),
    ("plan", "Progression"),
    ("plan", "World"),
    ("plan", "Signature"),
    ("plan", "Endgame"),
]

# ---------- MASTER PLAN ----------
M = []
M.append("# Cerberus — Game Design Plan\n")
M.append(f"*Generated {DATE} from an 8-domain multi-agent design pass, grounded in the live codebase "
         f"(real 10-tier system, the 35+ skills, and the systems already built). "
         f"Companion checklist: [OPEN_DECISIONS.md](OPEN_DECISIONS.md).*\n")

es = (synth.get("executiveSummary") or "").strip()
if es:
    M.append("## Executive Summary\n")
    M.append(es + "\n")

pillars = synth.get("designPillars") or []
if pillars:
    M.append("## Design Pillars\n")
    M.append("\n".join(f"{i+1}. {p}" for i, p in enumerate(pillars)) + "\n")

bo = synth.get("buildOrder") or []
if bo:
    M.append("## Recommended Build Order\n")
    M.append("\n".join(f"{i+1}. **{s.get('phase','')}** — {s.get('rationale','')}" for i, s in enumerate(bo)) + "\n")

M.append("\n---\n")
for kind, ref in order:
    if kind == "file":
        M.append(ref.strip() + "\n\n---\n")
    else:
        p = find_plan(ref)
        M.append(((p.get("markdown") or "").strip() if p else f"_(missing domain: {ref})_") + "\n\n---\n")

cn = synth.get("consistencyNotes") or []
if cn:
    M.append("## Cross-Domain Consistency Notes\n")
    M.append("\n".join(f"- {c}" for c in cn) + "\n")

# ---------- OPEN DECISIONS ----------
D = []
D.append("# Cerberus — Open Decisions\n")
D.append(f"*The \"what you still need to plan\" checklist. Generated {DATE}. "
         f"Companion: [CERBERUS_DESIGN_PLAN.md](CERBERUS_DESIGN_PLAN.md).*\n")

od = critique.get("openDecisions") or []
gaps = critique.get("gaps") or []

def prank(x):
    return {"P0": 0, "P1": 1, "P2": 2}.get((x.get("priority") or "").upper(), 3)

def esc(s):
    return (s or "").replace("|", "\\|").replace("\n", " ").strip()

if od:
    D.append("## Prioritized Decisions\n")
    groups = OrderedDict()
    for d in od:
        groups.setdefault(d.get("theme", "General"), []).append(d)
    for theme, items in groups.items():
        D.append(f"### {theme}\n")
        D.append("| Priority | Decision | Why it matters | Recommended default |")
        D.append("|---|---|---|---|")
        for d in sorted(items, key=prank):
            D.append(f"| {esc(d.get('priority'))} | {esc(d.get('decision'))} | {esc(d.get('why'))} | {esc(d.get('recommendation'))} |")
        D.append("")

if gaps:
    D.append("## Gaps Nobody Flagged (surfaced by the completeness critic)\n")
    D.append("\n".join(f"- {g}" for g in gaps) + "\n")

D.append("## Appendix — Per-Domain Open Questions\n")
# 6 JSON-plan domains
for p in plans:
    oqs = p.get("openQuestions") or []
    if oqs:
        D.append(f"### {p.get('domainTitle','')}\n")
        D.append("\n".join(f"- {q}" for q in oqs) + "\n")
# 2 re-run domains (parsed from their markdown)
for title, md in [("Bosses & Encounter Design", bosses_md),
                  ("Core Gameplay Loops, Player Fantasy & Balance", loops_md)]:
    oqs = extract_open(md)
    if oqs:
        D.append(f"### {title}\n")
        D.append("\n".join(f"- {q}" for q in oqs) + "\n")

os.makedirs(DESIGN, exist_ok=True)
master_path = os.path.join(DESIGN, "CERBERUS_DESIGN_PLAN.md")
dec_path = os.path.join(DESIGN, "OPEN_DECISIONS.md")
with open(master_path, "w", encoding="utf-8") as f:
    f.write("\n".join(M))
with open(dec_path, "w", encoding="utf-8") as f:
    f.write("\n".join(D))

# Report
print("plans in JSON:", len(plans))
for p in plans:
    print("   -", p.get("domainTitle"), f"({len(p.get('markdown') or '')} chars, {len(p.get('openQuestions') or [])} Qs)")
print("synth fields:", "exec" if es else "-", f"{len(pillars)} pillars", f"{len(bo)} build-steps", f"{len(cn)} notes")
print("critic:", len(od), "decisions,", len(gaps), "gaps")
print("WROTE:", master_path, f"({os.path.getsize(master_path)} bytes)")
print("WROTE:", dec_path, f"({os.path.getsize(dec_path)} bytes)")
