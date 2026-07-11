#!/usr/bin/env python3
"""Phase 0 — add remediation lifecycle to findings.json and assign lanes.
Non-source change: only annotates the audit ledger."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
f = json.load(open(os.path.join(HERE, "findings.json")))

# Lane D — decisions/external (verbatim from the remediation prompt).
LANE_D = {
    "chestshop/structure/0003": "canHold: implement a real ceiling or delete the dead guard — product call.",
    "global/database/0002": "DROP TABLE firm_sale — confirm no retention requirement for legacy rows.",
    "global/dependencies/0004": "Cut hibernia-framework 1.2.0 release and pin — external (framework repo).",
    "treasury-api-plugin/structure/0004": "Interface+impl convention for ALL injected services (decide once; applies with business/pa/0007, chestshop/pa/0008).",
    "business/plugin-architecture/0007": "Same interface+impl convention decision.",
    "chestshop/plugin-architecture/0008": "Same interface+impl convention decision.",
    "business/structure/0004": "Standardise firm addressing on int firmId — large API-breaking; approve scope or defer.",
    "economy-explorer/testing/0005": "Run Flyway in test harness vs add CI drift check — pick one strategy (also treasury-rest-api/testing/0009).",
    "treasury-rest-api/testing/0009": "Same test-harness Flyway-vs-drift-check decision.",
    "global/dependencies/0002": "vitest 2→3 major upgrade — approve for Wave 6 upgrade lane.",
    "treasury/behaviour/0001": "Salary dedup clock — low-confidence, downgraded; decide fix vs formally reject.",
}
EXTERNAL = {"global/dependencies/0004"}

# Lane C — large refactors (component barriers).
LANE_C = {"chestshop/structure/0001", "chestshop/structure/0002"}
# business/structure/0004 is Lane C only if approved; currently Lane D (defer/approve).

# Lane A — mechanical (unambiguous, no design choice).
MECH_CATEGORIES = ("hardcoded", "message-key", "dry", "duplicat", "index", "order-by",
                   "static-final", "dead", "workflow", "javadoc", "unused")

def lane_for(x):
    fid = x["id"]
    if fid in LANE_D:
        return "D"
    if fid in LANE_C:
        return "C"
    cat = (x.get("category") or "").lower()
    if x.get("effort") == "trivial":
        return "A"
    if any(k in cat for k in MECH_CATEGORIES):
        return "A"
    return "B"

for x in f:
    x["lane"] = lane_for(x)
    if x["id"] in LANE_D:
        x["status"] = "external" if x["id"] in EXTERNAL else "decision-needed"
        x["resolution"] = {"decision": LANE_D[x["id"]], "branch": None, "commits": [], "test": None}
    else:
        x["status"] = "pending"
        x["resolution"] = {"branch": None, "commits": [], "test": None, "reject_reason": None}

json.dump(f, open(os.path.join(HERE, "findings.json"), "w"), indent=2)
from collections import Counter
print("lanes:", dict(Counter(x["lane"] for x in f)))
print("status:", dict(Counter(x["status"] for x in f)))
