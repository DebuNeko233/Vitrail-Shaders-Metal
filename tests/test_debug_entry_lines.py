#!/usr/bin/env python3
"""The F3 screen names the Metal API, and the scale only where there is one.

Which generation of the API is running is the fact a report about a picture is usually missing, and the
scale in force is the other - but a scale of 100 per cent is not a fact worth a line, so the line is
absent rather than saying "native". Both are read through the same narrow interface the runtime path
uses, so a capture and the log cannot disagree, and nothing here may name a Metal type: these lines are
the pack-facing side's, and a native handle reaching it is the fault `AGENTS.md` forbids.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTRY = (ROOT / "common/src/main/java/dev/vitrail/screen/VitrailDebugEntry.java").read_text(encoding="utf-8")
STATUS = (ROOT / "common/src/main/java/dev/vitrail/render/MetallumStatus.java").read_text(encoding="utf-8")
STAGES = (ROOT / "common/src/main/java/dev/vitrail/platform/EngineStages.java").read_text(encoding="utf-8")


def require(label: str, text: str, needles: tuple[str, ...]) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"debug entry: {label}: missing " + ", ".join(missing))


require("the generation in use is read, not guessed", STATUS, (
    'readString("metalApiGeneration")',
    "public static String metalApiGeneration() {",
))
require("what the device could run is a separate reading", STATUS, (
    'readString("deviceMetalApiGeneration")',
    "public static String deviceMetalApiGeneration() {",
))
require("the API in use is the line, and the capability only where it differs", ENTRY, (
    "MetallumStatus.metalApiGeneration()",
    "String deviceApi = MetallumStatus.deviceMetalApiGeneration();",
    'PREFIX + "Metal API: " + metalApi + capability',
    "if (!metalApi.isEmpty()) {",
))
require("the capability is a parenthesised suffix and never the answer", ENTRY, (
    "String capability = deviceApi.isEmpty() || deviceApi.equals(metalApi)",
    ': " (device supports " + deviceApi + ")";',
))
# The line said "Metal 4" for a session whose every frame was Metal 3's, because it showed the device's newest
# family under a label a reader takes as what is running. Both facts have to be read before the line is built,
# so that neither can be mistaken for the other.
if ENTRY.index("String deviceApi") > ENTRY.index('PREFIX + "Metal API: "'):
    raise SystemExit("debug entry: the device's capability is read after the line is built")
require("the scale is shown only where there is one", ENTRY, (
    "int renderScale = PackChoice.renderScale();",
    "if (renderScale < 100) {",
    'PREFIX + "MetalFX: " + renderScale + "% scale"',
))
require("the log names the generation, which is all that is known that early", STAGES, (
    'Vitrail.logger().info("Metal API: {}", metalApi);',
))

if "MTL" in ENTRY:
    raise SystemExit("debug entry: a Metal type name reached the pack-facing side")
if "available - " in ENTRY:
    raise SystemExit("debug entry: the scaler's own sentence is back on the screen")

print("Vitrail debug entry contract: PASS")
