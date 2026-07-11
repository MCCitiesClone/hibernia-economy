#!/usr/bin/env python3
"""Phase 3 — scoring + report. Reads findings.json, manifest.json, summaries.json,
rejected.jsonl. Writes REPORT.md. Scores are computed, never judged.

score(component, dimension) = max(0, round(10 - sum(weight(f)), 1))
  weight: blocker 3.0, major 1.5, minor 0.5, nit 0.1
  confidence multiplier: high x1.0, medium x0.7, low x0.4
Global dimensions score once, repeated in each component's row.
"""
import json, os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
SEV_W = {"blocker": 3.0, "major": 1.5, "minor": 0.5, "nit": 0.1}
CONF_M = {"high": 1.0, "medium": 0.7, "low": 0.4}
COMPONENT_DIMS = ["structure", "plugin-architecture", "behaviour", "testing"]
GLOBAL_DIMS = ["build", "infra", "dependencies", "database"]
ALL_DIMS = COMPONENT_DIMS + GLOBAL_DIMS

def load(name):
    with open(os.path.join(HERE, name)) as f:
        return json.load(f)

def load_jsonl(name):
    p = os.path.join(HERE, name)
    if not os.path.exists(p):
        return []
    out = []
    with open(p) as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(json.loads(line))
    return out

def weight(f):
    return SEV_W[f["severity"]] * CONF_M[f["confidence"]]

def score(findings):
    return max(0.0, round(10 - sum(weight(f) for f in findings), 1))

def main():
    findings = load("findings.json")
    manifest = load("manifest.json")
    summaries = load("summaries.json")
    rejected = load_jsonl("rejected.jsonl")
    components = [c["id"] for c in manifest["components"]]

    # bucket findings
    by_cell = defaultdict(list)
    global_by_dim = defaultdict(list)
    for f in findings:
        if f["component"] == "global":
            global_by_dim[f["dimension"]].append(f)
        else:
            by_cell[(f["component"], f["dimension"])].append(f)

    def cell_findings(comp, dim):
        if dim in GLOBAL_DIMS:
            return global_by_dim.get(dim, [])
        return by_cell.get((comp, dim), [])

    # applicability: which component-dims each component actually ran
    ran = defaultdict(set)
    for s in summaries:
        ran[s["component"]].add(s["dimension"])

    lines = []
    lines.append("# Monorepo Quality Audit — Report\n")
    lines.append("> **Read-only audit.** No source was modified. Upgrades/rewrites are captured as findings with remediation plans for a separate run.\n")
    lines.append("## Scoring formula\n")
    lines.append("```\nscore(component, dimension) = max(0, round(10 − Σ weight(f), 1))\n"
                 "weight: blocker=3.0, major=1.5, minor=0.5, nit=0.1\n"
                 "confidence multiplier: high×1.0, medium×0.7, low×0.4\n"
                 "Global dimensions (build/infra/dependencies/database) score once, repeated per component row.\n```\n")

    # ---- 1. Score matrix ----
    lines.append("## 1. Score matrix\n")
    header = "| Component | " + " | ".join(ALL_DIMS) + " | **mean** |"
    sep = "|" + "---|" * (len(ALL_DIMS) + 2)
    lines.append(header)
    lines.append(sep)
    dim_scores = defaultdict(list)
    comp_means = {}
    for comp in components:
        row = [comp]
        vals = []
        for dim in ALL_DIMS:
            if dim in COMPONENT_DIMS and dim not in ran.get(comp, set()):
                # dimension not applicable / not run for this component (e.g. plugin-architecture on non-plugins)
                row.append("—")
                continue
            sc = score(cell_findings(comp, dim))
            row.append(f"{sc:.1f}")
            vals.append(sc)
            dim_scores[dim].append(sc)
        mean = round(sum(vals) / len(vals), 1) if vals else 0.0
        comp_means[comp] = mean
        row.append(f"**{mean:.1f}**")
        lines.append("| " + " | ".join(row) + " |")
    # per-dimension means row
    mean_row = ["**mean**"]
    for dim in ALL_DIMS:
        vs = dim_scores.get(dim, [])
        mean_row.append(f"**{round(sum(vs)/len(vs),1):.1f}**" if vs else "—")
    mean_row.append("")
    lines.append("| " + " | ".join(mean_row) + " |")
    lines.append("")

    # ---- 2. Executive summary ----
    lines.append("## 2. Executive summary\n")
    counts = defaultdict(int)
    for f in findings:
        counts[f["severity"]] += 1
    lines.append(f"- **Total findings (post-verification, merged):** {len(findings)}")
    lines.append(f"- **Blockers:** {counts['blocker']}  |  **Major:** {counts['major']}  |  "
                 f"**Minor:** {counts['minor']}  |  **Nit:** {counts['nit']}")
    lines.append(f"- **Rejected in verification:** {len(rejected)}")
    lines.append("")
    top = sorted(findings, key=weight, reverse=True)[:10]
    lines.append("### Top 10 findings by weight\n")
    lines.append("| # | weight | severity | conf | component/dim | file:lines | description |")
    lines.append("|---|---|---|---|---|---|---|")
    for i, f in enumerate(top, 1):
        desc = f["description"].replace("|", "\\|")
        if len(desc) > 140:
            desc = desc[:140] + "…"
        lines.append(f"| {i} | {weight(f):.2f} | {f['severity']} | {f['confidence']} | "
                     f"{f['component']}/{f['dimension']} | `{f['file']}:{f['lines'][0]}-{f['lines'][1]}` | {desc} |")
    lines.append("")

    # ---- 3. Findings by component -> dimension ----
    lines.append("## 3. Findings by component → dimension\n")
    def emit_group(title, group):
        if not group:
            return
        lines.append(f"### {title}\n")
        lines.append("| id | sev | conf | verif | file:lines | description | recommendation | effort |")
        lines.append("|---|---|---|---|---|---|---|---|")
        for f in sorted(group, key=lambda x: ({"blocker":0,"major":1,"minor":2,"nit":3}[x["severity"]])):
            d = f["description"].replace("|", "\\|").replace("\n", " ")
            r = f["recommendation"].replace("|", "\\|").replace("\n", " ")
            if len(d) > 200: d = d[:200] + "…"
            if len(r) > 200: r = r[:200] + "…"
            verif = f.get("verification", "unverified")
            lines.append(f"| {f['id']} | {f['severity']} | {f['confidence']} | {verif} | "
                         f"`{f['file']}:{f['lines'][0]}-{f['lines'][1]}` | {d} | {r} | {f['effort']} |")
        lines.append("")
        # snippets in collapsible blocks
        lines.append("<details><summary>snippets</summary>\n")
        for f in group:
            snip = f.get("snippet", "")
            lines.append(f"**{f['id']}** `{f['file']}:{f['lines'][0]}-{f['lines'][1]}`\n")
            lines.append("```\n" + snip + "\n```\n")
        lines.append("</details>\n")

    for comp in components:
        comp_findings = [f for f in findings if f["component"] == comp]
        if comp_findings:
            lines.append(f"## Component: {comp}  (mean {comp_means.get(comp, 0):.1f})\n")
            for dim in COMPONENT_DIMS:
                emit_group(f"{comp} · {dim}", [f for f in comp_findings if f["dimension"] == dim])
    # global
    lines.append("## Global dimensions\n")
    for dim in GLOBAL_DIMS:
        emit_group(f"global · {dim}", global_by_dim.get(dim, []))

    # ---- 4. Remediation plan ----
    lines.append("## 4. Remediation plan\n")
    lines.append("Work packages, blockers first, then majors clustered by theme. Sized by summed effort.\n")
    blockers = [f for f in findings if f["severity"] == "blocker"]
    majors = [f for f in findings if f["severity"] == "major"]
    pkg = 1
    if blockers:
        lines.append(f"### WP{pkg} — Blockers (fix first)\n")
        for f in sorted(blockers, key=lambda x: x["component"]):
            lines.append(f"- **[{f['id']}]** ({f['effort']}) `{f['file']}:{f['lines'][0]}` — {f['description'][:180]}")
        lines.append("")
        pkg += 1
    # cluster majors by (component, dimension)
    clusters = defaultdict(list)
    for f in majors:
        clusters[(f["component"], f["dimension"])].append(f)
    for (comp, dim), group in sorted(clusters.items()):
        lines.append(f"### WP{pkg} — Majors: {comp} · {dim} ({len(group)} findings)\n")
        for f in group:
            lines.append(f"- **[{f['id']}]** ({f['effort']}) `{f['file']}:{f['lines'][0]}` — {f['description'][:160]}")
        lines.append("")
        pkg += 1
    # dependency upgrades as their own package
    dep_findings = global_by_dim.get("dependencies", [])
    if dep_findings:
        lines.append(f"### WP{pkg} — Dependency upgrades (separate remediation run)\n")
        for f in dep_findings:
            lines.append(f"- **[{f['id']}]** ({f['effort']}, {f['severity']}) {f['description'][:160]}")
        lines.append("")
        pkg += 1

    # ---- 5. Coverage appendix ----
    lines.append("## 5. Audit coverage appendix\n")
    lines.append(f"Rejected findings (failed verification): **{len(rejected)}** (see `audit/rejected.jsonl`).\n")
    lines.append("| component | dimension | files_examined | raw→kept | not_applicable |")
    lines.append("|---|---|---|---|---|")
    for s in summaries:
        summ = s.get("summary") or {}
        fe = summ.get("files_examined", "?")
        na = ", ".join(summ.get("not_applicable", []))[:120] or "—"
        lines.append(f"| {s['component']} | {s['dimension']} | {fe} | {s['raw_count']}→{s['kept']} | {na} |")
    lines.append("")

    with open(os.path.join(HERE, "REPORT.md"), "w") as f:
        f.write("\n".join(lines))
    print(f"REPORT.md written: {len(findings)} findings, blockers={counts['blocker']} major={counts['major']}")

if __name__ == "__main__":
    main()
