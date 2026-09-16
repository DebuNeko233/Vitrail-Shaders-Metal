#!/usr/bin/env python3
"""Classify PHASE 17 real shader-pack compatibility from explicit evidence.

The classifier is deliberately conservative: it never guesses a pack status from its
name, family, or static source alone. A final classification requires a completed,
reviewed real-device evidence record.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, NamedTuple

STATUSES = (
    "Supported",
    "Partially Supported",
    "Fallback",
    "Unsupported",
    "Broken",
)

REQUIRED_TOP = ("schema", "pack", "environment", "evidence")
REQUIRED_PACK = ("family", "name", "version", "artifact_sha256")
REQUIRED_ENV = ("minecraft", "vitrail", "metallum", "backend", "device")
REQUIRED_EVIDENCE = (
    "attempted",
    "loaded",
    "world_drawn",
    "clean_shutdown",
    "fatal_failure",
    "compatibility_reviewed",
    "blocking_unsupported",
    "meaningful_unsupported_features",
    "vitrail_draws",
    "compatibility_fallbacks",
    "visual_reference_checked",
    "visual_reference_ok",
    "visual_regressions",
)


class EvidenceError(ValueError):
    pass


def _expect_bool(obj: dict[str, Any], key: str) -> bool:
    value = obj[key]
    if type(value) is not bool:
        raise EvidenceError(f"evidence.{key} must be boolean")
    return value


def _expect_count(obj: dict[str, Any], key: str) -> int:
    value = obj[key]
    if type(value) is not int or value < 0:
        raise EvidenceError(f"evidence.{key} must be a non-negative integer")
    return value


def validate(record: dict[str, Any]) -> None:
    missing = [k for k in REQUIRED_TOP if k not in record]
    if missing:
        raise EvidenceError("missing top-level fields: " + ", ".join(missing))
    if record["schema"] != "vitrail.phase17.compatibility.v1":
        raise EvidenceError("unsupported schema; expected vitrail.phase17.compatibility.v1")

    pack = record["pack"]
    env = record["environment"]
    ev = record["evidence"]
    if not isinstance(pack, dict) or not isinstance(env, dict) or not isinstance(ev, dict):
        raise EvidenceError("pack, environment and evidence must be objects")

    for key in REQUIRED_PACK:
        if not isinstance(pack.get(key), str) or not pack[key].strip():
            raise EvidenceError(f"pack.{key} must be a non-empty string")
    digest = pack["artifact_sha256"].lower()
    if len(digest) != 64 or any(ch not in "0123456789abcdef" for ch in digest):
        raise EvidenceError("pack.artifact_sha256 must be a 64-character SHA-256 hex digest")
    for key in REQUIRED_ENV:
        if not isinstance(env.get(key), str) or not env[key].strip():
            raise EvidenceError(f"environment.{key} must be a non-empty string")
    missing = [k for k in REQUIRED_EVIDENCE if k not in ev]
    if missing:
        raise EvidenceError("missing evidence fields: " + ", ".join(missing))

    bools = {
        key: _expect_bool(ev, key)
        for key in (
            "attempted", "loaded", "world_drawn", "clean_shutdown",
            "fatal_failure", "compatibility_reviewed",
            "visual_reference_checked", "visual_reference_ok",
        )
    }
    counts = {
        key: _expect_count(ev, key)
        for key in (
            "blocking_unsupported", "meaningful_unsupported_features", "vitrail_draws",
            "compatibility_fallbacks", "visual_regressions",
        )
    }

    if not bools["attempted"]:
        raise EvidenceError("real-device session was not attempted; refusing to classify")
    if bools["visual_reference_ok"] and not bools["visual_reference_checked"]:
        raise EvidenceError("visual_reference_ok cannot be true when visual_reference_checked is false")
    if bools["world_drawn"] and not bools["loaded"]:
        raise EvidenceError("world_drawn cannot be true when loaded is false")
    if counts["vitrail_draws"] > 0 and not bools["world_drawn"]:
        raise EvidenceError("vitrail_draws requires world_drawn=true")
    if counts["compatibility_fallbacks"] > 0 and not bools["world_drawn"]:
        raise EvidenceError("compatibility_fallbacks requires world_drawn=true")


class Classification(NamedTuple):
    status: str
    reasons: tuple[str, ...]


def classify(record: dict[str, Any]) -> Classification:
    validate(record)
    ev = record["evidence"]

    if ev["fatal_failure"] or not ev["loaded"]:
        reasons = []
        if ev["fatal_failure"]:
            reasons.append("fatal runtime/chain failure was observed")
        if not ev["loaded"]:
            reasons.append("pack did not load")
        return Classification("Broken", tuple(reasons))

    if not ev["clean_shutdown"]:
        raise EvidenceError("session did not reach clean shutdown; refusing to turn an incomplete session into a pack status")

    if not ev["compatibility_reviewed"]:
        raise EvidenceError(
            "compatibility observations were not reviewed; refusing to promote raw runtime evidence to a pack status"
        )

    if ev["blocking_unsupported"] > 0:
        return Classification(
            "Unsupported",
            (f"{ev['blocking_unsupported']} required unsupported blocker(s) prevent the intended route",),
        )

    if ev["world_drawn"] and ev["vitrail_draws"] == 0 and ev["compatibility_fallbacks"] > 0:
        return Classification(
            "Fallback",
            ("world rendered only through fallback/game-owned draws; no Vitrail pack draw was observed",),
        )

    partial = []
    if ev["meaningful_unsupported_features"]:
        partial.append(f"{ev['meaningful_unsupported_features']} meaningful non-blocking unsupported feature(s)")
    if ev["compatibility_fallbacks"]:
        partial.append(f"{ev['compatibility_fallbacks']} compatibility fallback event(s)")
    if ev["visual_regressions"]:
        partial.append(f"{ev['visual_regressions']} visual regression(s)")
    if ev["visual_reference_checked"] and not ev["visual_reference_ok"]:
        partial.append("reference comparison did not match")
    if partial:
        if ev["vitrail_draws"] <= 0:
            raise EvidenceError(
                "partial-support evidence has no Vitrail draw; record fallback-only or unsupported evidence explicitly"
            )
        return Classification("Partially Supported", tuple(partial))

    if not ev["world_drawn"]:
        raise EvidenceError("no world draw was observed; refusing to classify Supported")
    if ev["vitrail_draws"] <= 0:
        raise EvidenceError("no Vitrail pack draw was observed; refusing to classify Supported")
    if not ev["visual_reference_checked"]:
        raise EvidenceError("reference visual was not checked; refusing to classify Supported")
    if not ev["visual_reference_ok"]:
        raise EvidenceError("reference visual did not pass; refusing to classify Supported")

    return Classification(
        "Supported",
        ("real Vitrail draws, clean shutdown, reviewed compatibility observations and a passing reference visual",),
    )


def result_json(record: dict[str, Any], result: Classification) -> dict[str, Any]:
    return {
        "schema": "vitrail.phase17.result.v1",
        "pack": record["pack"],
        "environment": record["environment"],
        "status": result.status,
        "reasons": list(result.reasons),
    }


def result_markdown(record: dict[str, Any], result: Classification) -> str:
    pack = record["pack"]
    env = record["environment"]
    reasons = "\n".join(f"- {reason}" for reason in result.reasons)
    return (
        f"## {pack['name']} {pack['version']} ({pack['family']})\n\n"
        f"**Status:** {result.status}\n\n"
        f"**Artifact SHA-256:** `{pack['artifact_sha256']}`\n\n"
        f"**Tested on:** Minecraft {env['minecraft']}; Vitrail {env['vitrail']}; Metallum {env['metallum']}; "
        f"{env['backend']} on {env['device']}\n\n"
        f"### Evidence\n{reasons}\n"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path, help="PHASE 17 evidence JSON")
    parser.add_argument("--json", dest="json_out", type=Path, help="write normalized result JSON")
    parser.add_argument("--markdown", dest="markdown_out", type=Path, help="write Markdown result")
    args = parser.parse_args(argv)

    try:
        record = json.loads(args.evidence.read_text(encoding="utf-8"))
        result = classify(record)
    except (OSError, json.JSONDecodeError, EvidenceError) as exc:
        print(f"PHASE 17 compatibility: REFUSED: {exc}", file=sys.stderr)
        return 2

    normalized = result_json(record, result)
    print(f"PHASE 17 compatibility: {record['pack']['name']} {record['pack']['version']} -> {result.status}")
    for reason in result.reasons:
        print(f"  - {reason}")

    if args.json_out:
        args.json_out.write_text(json.dumps(normalized, indent=2) + "\n", encoding="utf-8")
    if args.markdown_out:
        args.markdown_out.write_text(result_markdown(record, result), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
