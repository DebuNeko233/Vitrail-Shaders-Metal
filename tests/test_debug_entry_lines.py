#!/usr/bin/env python3
"""The F3 screen names the Metal API and what MetalFX made of the device.

The three facts a report about a picture needs - which generation of the API is running, whether the
scaler is there, and what the scale is set to - are read through the same narrow interface the runtime
path uses, so a capture and a log cannot disagree. This pins the reading, the pairing and the seam:
nothing here may name a Metal type, because these lines are the pack-facing side's and a native handle
reaching it is the fault `AGENTS.md` forbids.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTRY = (ROOT / "common/src/main/java/dev/vitrail/screen/VitrailDebugEntry.java").read_text(encoding="utf-8")
STATUS = (ROOT / "common/src/main/java/dev/vitrail/render/MetallumStatus.java").read_text(encoding="utf-8")
STAGES = (ROOT / "common/src/main/java/dev/vitrail/platform/EngineStages.java").read_text(encoding="utf-8")


def require(label: str, text: str, needles: tuple[str, ...]) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"debug entry: {label}: missing " + ", ".join(missing))


require("the generation is read, not guessed", STATUS, (
    'readString("metalApiGeneration")',
    'readString("metalFxStatus")',
    "public static String metalApiGeneration() {",
    "public static String metalFxStatus() {",
))
require("the two lines are shown together", ENTRY, (
    "MetallumStatus.metalApiGeneration()",
    'PREFIX + "Metal API: " + metalApi',
    'PREFIX + "MetalFX: " + metalFxLine()',
    "if (!metalApi.isEmpty()) {",
))
require("the scaler's line carries the scale in force", ENTRY, (
    "String status = MetallumStatus.metalFxStatus();",
    "int scale = PackChoice.renderScale();",
    'scale + "% render scale"',
    '"native"',
))
require("the same readers are said once in the log", STAGES, (
    'Vitrail.logger().info("Metal API: {}, MetalFX: {}", metalApi,',
    "String metalApi = MetallumStatus.metalApiGeneration();",
))

if "MTL" in ENTRY:
    raise SystemExit("debug entry: a Metal type name reached the pack-facing side")

print("Vitrail debug entry contract: PASS")
