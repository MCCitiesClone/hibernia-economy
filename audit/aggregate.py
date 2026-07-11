#!/usr/bin/env python3
"""Phase 2 — aggregate + apply verification verdicts + dedup/merge.

Input:  audit/raw_results.json   ({"cells":[{component,dimension,findings,summary,verdicts}]})
Output: audit/findings/<component>-<dimension>.jsonl   (per-cell, post-verdict)
        audit/rejected.jsonl                            (verification-rejected findings)
        audit/findings.json                             (merged, verified, sorted array)
        audit/summaries.json                            (per-cell coverage summaries)
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))

def load():
    with open(os.path.join(HERE, "raw_results.json")) as f:
        return json.load(f)

def apply_verdicts(cell):
    """Return (kept_findings, rejected_findings). Assign stable ids + verification field."""
    comp, dim = cell["component"], cell["dimension"]
    verdicts = {v["seq"]: v for v in cell.get("verdicts", [])}
    kept, rejected = [], []
    for f in cell.get("findings", []):
        seq = f["seq"]
        fid = f"{comp}/{dim}/{seq:04d}"
        rec = dict(f)
        rec["id"] = fid
        rec["component"] = comp
        rec["dimension"] = dim
        rec.setdefault("occurrences", [])
        v = verdicts.get(seq)
        if v is None:
            rec["verification"] = "unverified"  # not selected in the 20% sample
        elif v["status"] == "rejected":
            rec["verification"] = "rejected"
            rec["rejection_reason"] = v.get("reason", "")
            rejected.append(rec)
            continue
        elif v["status"] == "downgraded":
            rec["verification"] = "downgraded"
            rec["original_severity"] = f["severity"]
            if v.get("corrected_severity"):
                rec["severity"] = v["corrected_severity"]
            rec["verification_note"] = v.get("reason", "")
        else:  # confirmed
            rec["verification"] = "confirmed"
            rec["verification_note"] = v.get("reason", "")
        kept.append(rec)
    return kept, rejected

def overlaps(a, b):
    return a[0] <= b[1] and b[0] <= a[1]

def merge(findings):
    """Merge findings with same file + overlapping lines + same category."""
    merged = []
    for f in findings:
        hit = None
        for m in merged:
            if (m["file"] == f["file"] and m.get("category") == f.get("category")
                    and overlaps(m["lines"], f["lines"])):
                hit = m
                break
        if hit is None:
            f["related_dimensions"] = [f["dimension"]]
            merged.append(f)
        else:
            if f["dimension"] not in hit["related_dimensions"]:
                hit["related_dimensions"].append(f["dimension"])
            # keep the more severe; keep the longer (more specific) description
            order = {"blocker": 0, "major": 1, "minor": 2, "nit": 3}
            if order[f["severity"]] < order[hit["severity"]]:
                hit["severity"] = f["severity"]
            if len(f.get("description", "")) > len(hit.get("description", "")):
                hit["description"] = f["description"]
                hit["recommendation"] = f["recommendation"]
            hit["lines"] = [min(hit["lines"][0], f["lines"][0]), max(hit["lines"][1], f["lines"][1])]
    return merged

def main():
    data = load()
    findings_dir = os.path.join(HERE, "findings")
    os.makedirs(findings_dir, exist_ok=True)

    all_kept, all_rejected, summaries = [], [], []
    for cell in data["cells"]:
        kept, rejected = apply_verdicts(cell)
        comp, dim = cell["component"], cell["dimension"]
        path = os.path.join(findings_dir, f"{comp}-{dim}.jsonl")
        with open(path, "w") as f:
            for rec in kept:
                f.write(json.dumps(rec) + "\n")
        all_kept.extend(kept)
        all_rejected.extend(rejected)
        summaries.append({"component": comp, "dimension": dim,
                          "summary": cell.get("summary"),
                          "raw_count": len(cell.get("findings", [])),
                          "kept": len(kept), "rejected": len(rejected)})

    merged = merge(all_kept)
    sev_order = {"blocker": 0, "major": 1, "minor": 2, "nit": 3}
    merged.sort(key=lambda f: (f["component"], f["dimension"], sev_order[f["severity"]], f["file"]))

    with open(os.path.join(HERE, "findings.json"), "w") as f:
        json.dump(merged, f, indent=2)
    with open(os.path.join(HERE, "rejected.jsonl"), "w") as f:
        for rec in all_rejected:
            f.write(json.dumps(rec) + "\n")
    with open(os.path.join(HERE, "summaries.json"), "w") as f:
        json.dump(summaries, f, indent=2)

    print(f"cells={len(data['cells'])} raw={sum(len(c.get('findings',[])) for c in data['cells'])} "
          f"kept={len(all_kept)} merged={len(merged)} rejected={len(all_rejected)}")

if __name__ == "__main__":
    main()
