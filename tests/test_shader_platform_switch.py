"""One developer switch, and it only ever omits the platform symbol.

Complementary Reimagined gates its Advanced Colored Lighting and its world-space reflections on
`!defined MC_OS_MAC`, because Iris on macOS has never served the custom images those paths are built on. That is
a statement about Iris and not about Metal, so whether this engine's own custom-image pipe carries them can only
be asked with the pack on the branch its author wrote for the platforms it serves. This pins the switch that
makes that possible: off unless asked for, and omitting the symbol rather than claiming another platform.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFINES = (ROOT / "common/src/main/java/dev/vitrail/pack/option/EngineDefines.java").read_text(encoding="utf-8")
BUILD = ROOT / ".github/workflows/build.yml"


def require(label: str, text: str, needles: tuple[str, ...]) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"shader platform switch: {label}: missing " + ", ".join(missing))


require("the switch exists, is named for what it does and is off unless asked for", DEFINES, (
    'Boolean.getBoolean("vitrail.shaderPlatformNonMac")',
    "environment.os() == Os.MAC",
    "Off by default",
))

# It omits the symbol; it does not pose another platform's. Claiming MC_OS_LINUX would switch on every
# Linux-gated path in the pack as well, which is a bigger lie than the one needed.
guarded = DEFINES.split('Boolean.getBoolean("vitrail.shaderPlatformNonMac")', 1)[1].split("}", 1)[0]
if "MC_OS_LINUX" in guarded or "MC_OS_WINDOWS" in guarded:
    raise SystemExit("shader platform switch: the switch poses another platform's symbol instead of omitting it")

require("the reason travels with it, so a later edit knows what it is holding", DEFINES, (
    "a statement about Iris and not about Metal",
    "bug reports have to be read with that in mind",
))

require("this contract is named by the workflow", BUILD.read_text(encoding="utf-8"), (
    "tests/test_shader_platform_switch.py",
))

print("Vitrail shader platform switch contract: PASS")
