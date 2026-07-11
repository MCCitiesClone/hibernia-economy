#!/usr/bin/env python3
"""Phase 3 — before/after score diff. Same formula as generate_report.py, but scores
twice: BEFORE (all findings, as originally audited) and AFTER (only findings still open
— status in {pending, decision-needed, external, in-progress}; resolved/rejected/deferred
no longer count as live defects). Writes audit/REMEDIATION.md."""
import json, os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
SEV_W = {"blocker": 3.0, "major": 1.5, "minor": 0.5, "nit": 0.1}
CONF_M = {"high": 1.0, "medium": 0.7, "low": 0.4}
COMPONENT_DIMS = ["structure", "plugin-architecture", "behaviour", "testing"]
GLOBAL_DIMS = ["build", "infra", "dependencies", "database"]
ALL_DIMS = COMPONENT_DIMS + GLOBAL_DIMS
OPEN = {"pending", "decision-needed", "external", "in-progress"}

def load(n):
    with open(os.path.join(HERE, n)) as f:
        return json.load(f)

def w(f):
    return SEV_W[f["severity"]] * CONF_M[f["confidence"]]

def score(findings):
    return max(0.0, round(10 - sum(w(f) for f in findings), 1))

def main():
    findings = load("findings.json")
    manifest = load("manifest.json")
    components = [c["id"] for c in manifest["components"]]

    def bucket(live_only):
        by_cell = defaultdict(list); gdim = defaultdict(list)
        for f in findings:
            if live_only and f["status"] not in OPEN:
                continue
            if f["component"] == "global":
                gdim[f["dimension"]].append(f)
            else:
                by_cell[(f["component"], f["dimension"])].append(f)
        return by_cell, gdim

    ran = defaultdict(set)
    for f in findings:
        if f["component"] != "global":
            ran[f["component"]].add(f["dimension"])

    def cell_score(comp, dim, by_cell, gdim):
        fs = gdim.get(dim, []) if dim in GLOBAL_DIMS else by_cell.get((comp, dim), [])
        return score(fs)

    b_cell, b_g = bucket(False)
    a_cell, a_g = bucket(True)

    L = []
    L.append("# Remediation Pass — Close-out Report\n")
    from collections import Counter
    st = Counter(f["status"] for f in findings)
    total = len(findings)
    resolved = st.get("resolved", 0)
    L.append(f"**Findings:** {total} total — "
             f"**{resolved} resolved**, {st.get('rejected',0)} rejected, "
             f"{st.get('decision-needed',0)} decision-needed, {st.get('external',0)} external, "
             f"{st.get('deferred',0)} deferred, {st.get('pending',0)} pending.\n")
    sev = Counter(f["severity"] for f in findings)
    sev_res = Counter(f["severity"] for f in findings if f["status"] == "resolved")
    L.append("**By severity (resolved / total):** "
             + " · ".join(f"{s} {sev_res.get(s,0)}/{sev.get(s,0)}" for s in ["blocker","major","minor","nit"]) + "\n")
    L.append("Scoring: `max(0, 10 − Σ weight)`, weight blocker 3.0/major 1.5/minor 0.5/nit 0.1, "
             "confidence ×1.0/0.7/0.4. **After** counts only findings still open (decision-needed/external); "
             "resolved findings no longer subtract.\n")

    # matrix
    header = "| Component | " + " | ".join(ALL_DIMS) + " | mean |"
    L.append(header)
    L.append("|" + "---|" * (len(ALL_DIMS) + 2))
    b_dim = defaultdict(list); a_dim = defaultdict(list)
    for comp in components:
        cells = []
        for dim in ALL_DIMS:
            if dim in COMPONENT_DIMS and dim not in ran.get(comp, set()):
                cells.append(("—", "—")); continue
            bs = cell_score(comp, dim, b_cell, b_g)
            as_ = cell_score(comp, dim, a_cell, a_g)
            cells.append((bs, as_))
            b_dim[dim].append(bs); a_dim[dim].append(as_)
        bvals = [c[0] for c in cells if c[0] != "—"]
        avals = [c[1] for c in cells if c[1] != "—"]
        bm = round(sum(bvals)/len(bvals),1) if bvals else 0
        am = round(sum(avals)/len(avals),1) if avals else 0
        row = [comp] + [f"{c[0]}→{c[1]}" if c[0] != "—" else "—" for c in cells] + [f"**{bm}→{am}**"]
        L.append("| " + " | ".join(str(x) for x in row) + " |")
    mrow = ["**mean**"]
    for dim in ALL_DIMS:
        bv = b_dim.get(dim, []); av = a_dim.get(dim, [])
        if bv:
            mrow.append(f"{round(sum(bv)/len(bv),1)}→{round(sum(av)/len(av),1)}")
        else:
            mrow.append("—")
    mrow.append("")
    L.append("| " + " | ".join(mrow) + " |")
    L.append("")

    # any regressions? (after > before is impossible with this formula; flag any cell that dropped)
    drops = []
    for comp in components:
        for dim in ALL_DIMS:
            if dim in COMPONENT_DIMS and dim not in ran.get(comp, set()):
                continue
            bs = cell_score(comp, dim, b_cell, b_g)
            as_ = cell_score(comp, dim, a_cell, a_g)
            if as_ < bs:
                drops.append(f"{comp}/{dim}: {bs}→{as_}")
    L.append("**Score regressions (any cell where after < before):** "
             + ("NONE — every dimension improved or held." if not drops else ", ".join(drops)) + "\n")

    # remaining open, by status
    L.append("## Remaining open findings (require your decision)\n")
    for status in ["decision-needed", "external", "deferred", "pending"]:
        items = [f for f in findings if f["status"] == status]
        if not items:
            continue
        L.append(f"### {status} ({len(items)})\n")
        for f in sorted(items, key=lambda x: x["id"]):
            dec = (f.get("resolution") or {}).get("decision", "")
            L.append(f"- **{f['id']}** ({f['severity']}) — {dec or f['description'][:140]}")
        L.append("")

    with open(os.path.join(HERE, "REMEDIATION.md"), "w") as f:
        f.write("\n".join(L))
    print(f"REMEDIATION.md written. resolved={resolved}/{total}, regressions={len(drops)}")

if __name__ == "__main__":
    main()
