#!/usr/bin/env python3
"""Summarise a JFR recording per Cerberus method (CPU samples and allocations).

Usage: jfr_summary.py <recording.jfr> [seconds] [sample-period-ms]

- CPU: every jdk.ExecutionSample is one sampled moment of a running thread (profile.sh records
  one per millisecond per running thread; JFR's stock "profile" settings use 10 ms). A sample counts for Cerberus when any Cerberus frame is on the
  stack; it is attributed to the Cerberus frame nearest the top ("self + the server/library code
  it called") and, separately, to the outermost Cerberus frame (the entry point, usually an event
  handler or a scheduled task).
- Allocations: jdk.ObjectAllocationSample weights (estimated bytes), attributed the same way.
"""
import collections, json, subprocess, sys

PKGS = ("Attributes.", "Commands.", "CustomEntities.", "CustomItems.", "CustomTags.", "DefensiveMagic.",
        "GUIs.", "Listener.", "Manager.", "Skills.", "Spells.", "Traps.", "cerberus.")
PERIOD_MS = float(sys.argv[3]) if len(sys.argv) > 3 else 10.0


def frames(ev):
    st = ev["values"].get("stackTrace") or {}
    out = []
    for f in st.get("frames") or []:
        m = f.get("method") or {}
        t = (m.get("type") or {}).get("name", "?")
        out.append(t.replace("/", ".") + "." + m.get("name", "?"))
    return out


def ours(name):
    return name.startswith(PKGS)


def group(thread):
    if "Region Scheduler" in thread or "Region Tick" in thread:
        return "region threads"
    if thread.startswith("Server thread"):
        return "server thread"
    if "Async Scheduler" in thread or "async" in thread.lower():
        return "async"
    return "other"


def load(path, event):
    raw = subprocess.run(["jfr", "print", "--json", "--stack-depth", "64", "--events", event, path],
                         check=True, capture_output=True, text=True).stdout
    return json.loads(raw)["recording"]["events"]


def main():
    path = sys.argv[1]
    secs = float(sys.argv[2]) if len(sys.argv) > 2 else None
    cpu = load(path, "jdk.ExecutionSample")
    per_group = collections.Counter()
    ours_group = collections.Counter()
    inner = collections.Counter()
    entry = collections.Counter()
    for ev in cpu:
        th = ((ev["values"].get("sampledThread") or {}).get("javaName")) or "?"
        g = group(th)
        per_group[g] += 1
        fs = frames(ev)
        mine = [f for f in fs if ours(f)]
        if mine:
            ours_group[g] += 1
            inner[mine[0]] += 1
            entry[mine[-1]] += 1
    print(f"CPU samples: {len(cpu)} ({PERIOD_MS:.0f} ms each per running thread)" + (f" over {secs:.0f} s" if secs else ""))
    for g, n in per_group.most_common():
        line = f"  {g:15s} {n:7d} samples, Cerberus on the stack in {ours_group[g]:6d} ({100.0 * ours_group[g] / max(1, n):.1f}%)"
        if secs and g == "region threads":
            line += f"  = ~{ours_group[g] * PERIOD_MS / (secs * 20):.3f} ms of region-thread time per tick"
        print(line)
    print("\nTop Cerberus frames nearest the top of the stack (self + what they call outside Cerberus):")
    for name, n in inner.most_common(25):
        print(f"  {n:6d}  {name}")
    print("\nTop Cerberus entry points (outermost Cerberus frame):")
    for name, n in entry.most_common(15):
        print(f"  {n:6d}  {name}")

    alloc = load(path, "jdk.ObjectAllocationSample")
    total = 0.0
    mine_total = 0.0
    by = collections.Counter()
    for ev in alloc:
        w = float(ev["values"].get("weight") or 0)
        total += w
        mine = [f for f in frames(ev) if ours(f)]
        if mine:
            mine_total += w
            by[mine[0]] += w
    rate = f" ({mine_total / secs / 1e6:.2f} MB/s)" if secs else ""
    print(f"\nAllocations sampled: {total / 1e6:.1f} MB total, Cerberus frames on the stack: {mine_total / 1e6:.1f} MB{rate}")
    for name, w in by.most_common(15):
        print(f"  {w / 1e6:8.2f} MB  {name}")


if __name__ == "__main__":
    main()
