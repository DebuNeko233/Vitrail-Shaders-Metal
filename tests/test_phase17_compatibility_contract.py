#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODULE_PATH = HERE / "phase17_compatibility.py"
spec = importlib.util.spec_from_file_location("phase17_compatibility", MODULE_PATH)
mod = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(mod)


def base_record():
    return {
        "schema": "vitrail.phase17.compatibility.v1",
        "pack": {"family": "fixture", "name": "fixture-pack", "version": "1.0", "artifact_sha256": "a" * 64},
        "environment": {
            "minecraft": "26.2",
            "vitrail": "fixture-head",
            "metallum": "fixture-head",
            "backend": "Metal",
            "device": "Apple Silicon",
        },
        "evidence": {
            "attempted": True,
            "loaded": True,
            "world_drawn": True,
            "clean_shutdown": True,
            "fatal_failure": False,
            "blocking_unsupported": 0,
            "meaningful_unsupported_features": 0,
            "vitrail_draws": 10,
            "compatibility_fallbacks": 0,
            "visual_reference_checked": True,
            "visual_reference_ok": True,
            "visual_regressions": 0,
        },
    }


def expect(status, **updates):
    record = base_record()
    record["evidence"].update(updates)
    got = mod.classify(record)
    assert got.status == status, (status, got)


def expect_refused(fragment, **updates):
    record = base_record()
    record["evidence"].update(updates)
    try:
        mod.classify(record)
    except mod.EvidenceError as exc:
        assert fragment in str(exc), str(exc)
    else:
        raise AssertionError("classification should have been refused")


expect("Supported")
expect("Broken", fatal_failure=True)
expect("Unsupported", blocking_unsupported=1, vitrail_draws=0)
expect("Fallback", vitrail_draws=0, compatibility_fallbacks=9)
expect("Partially Supported", meaningful_unsupported_features=1)
expect("Partially Supported", compatibility_fallbacks=2)
expect("Partially Supported", visual_regressions=1, visual_reference_ok=False)
expect_refused("reference visual was not checked", visual_reference_checked=False, visual_reference_ok=False)
expect_refused("real-device session was not attempted", attempted=False)
expect_refused("world_drawn cannot be true", loaded=False)
expect_refused("session did not reach clean shutdown", clean_shutdown=False)

catalog = json.loads((HERE / "fixtures" / "phase17" / "catalog.json").read_text(encoding="utf-8"))
assert catalog["schema"] == "vitrail.phase17.catalog.v1"
assert [entry["id"] for entry in catalog["families"]] == [
    "photon", "complementary", "bsl", "sildur", "makeup"
]
for entry in catalog["families"]:
    assert set(entry) == {"id", "label"}, entry
    assert "status" not in entry, entry

template = json.loads((HERE / "fixtures" / "phase17" / "evidence-template.json").read_text(encoding="utf-8"))
assert template["schema"] == "vitrail.phase17.compatibility.v1"
try:
    mod.classify(template)
except mod.EvidenceError as exc:
    assert "real-device session was not attempted" in str(exc)
else:
    raise AssertionError("untested evidence template must not produce a compatibility status")

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    evidence = root / "evidence.json"
    json_out = root / "result.json"
    md_out = root / "result.md"
    evidence.write_text(json.dumps(base_record()), encoding="utf-8")
    rc = mod.main([str(evidence), "--json", str(json_out), "--markdown", str(md_out)])
    assert rc == 0
    normalized = json.loads(json_out.read_text(encoding="utf-8"))
    assert normalized["status"] == "Supported"
    markdown = md_out.read_text(encoding="utf-8")
    assert "**Status:** Supported" in markdown
    assert "Metallum fixture-head; Metal on Apple Silicon" in markdown

print("PHASE 17 compatibility classifier contract: PASS")
