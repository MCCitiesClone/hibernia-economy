#!/usr/bin/env python3
"""Update finding lifecycle in findings.json.
Usage: close.py <status> <commit> "<note>" id1 id2 ...
  status: resolved|rejected|deferred|in-progress|reverified|decision-needed|external
"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
status, commit, note = sys.argv[1], sys.argv[2], sys.argv[3]
ids = set(sys.argv[4:])
f = json.load(open(os.path.join(HERE, "findings.json")))
seen = set()
for x in f:
    if x["id"] in ids:
        x["status"] = status
        r = x.get("resolution") or {}
        if commit and commit != "-":
            r.setdefault("commits", [])
            if commit not in r["commits"]:
                r["commits"].append(commit)
        if note and note != "-":
            r["note"] = note
        if status == "rejected":
            r["reject_reason"] = note
        x["resolution"] = r
        seen.add(x["id"])
missing = ids - seen
json.dump(f, open(os.path.join(HERE, "findings.json"), "w"), indent=2)
from collections import Counter
print("updated:", sorted(seen))
if missing: print("NOT FOUND:", sorted(missing))
print("status tally:", dict(Counter(x["status"] for x in f)))
