"""The pack's custom images are read, and a line this engine cannot honour is skipped.

Complementary Reimagined 5.9.1 declares the volumes Advanced Colored Lighting floods light through with
Iris's `image.<name>` directive, and nothing in this engine read it: the pack's `uniform sampler3D
floodfill_sampler` declarations never reached its programs while every use of them did, which is 56 compile
errors in one session's log. This pins the reading of that line, not the allocation of the volume.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PARSER = (ROOT / "common/src/main/java/dev/vitrail/pack/target/CustomImages.java").read_text(encoding="utf-8")
BUILD = ROOT / ".github/workflows/build.yml"


def require(label: str, text: str, needles: tuple[str, ...]) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"custom images: {label}: missing " + ", ".join(missing))


require("the directive is recognised by its prefix", PARSER, (
    'public static final String KEY_PREFIX = "image.";',
    "key.startsWith(KEY_PREFIX)",
))

# The pack's own three lines, field for field: sampler name, format class, format, data type, mipmap, clear,
# and the three dimensions. Nine fields is the whole form.
require("the nine fields are read in the pack's order", PARSER, (
    "private static final int FIELDS = 9;",
    "if (fields.size() != FIELDS) {",
    "Boolean mipmap = flag(fields.get(4));",
    "Boolean clear = flag(fields.get(5));",
    "int width = dimension(fields.get(6));",
    "int height = dimension(fields.get(7));",
    "int depth = dimension(fields.get(8));",
    "public record Volume(String name, String sampler, String formatClass, String format, String dataType,",
))

# A line that does not fit is skipped, never half-honoured: the same rule the constant directives are read
# under, and the reason is the same - a volume at the wrong size is a wrong picture that reads as a pack defect.
require("an unreadable line is skipped rather than guessed at", PARSER, (
    "return Optional.empty();",
    "private static final int MAX_DIMENSION = 4096;",
    "return size >= 1 && size <= MAX_DIMENSION ? size : 0;",
    'case "true" -> Boolean.TRUE;',
    'case "false" -> Boolean.FALSE;',
))

# And it is wired into the pass that reads the pack's properties, which is the whole point of reading it.
require("this contract is named by the workflow", BUILD.read_text(encoding="utf-8"), (
    "tests/test_custom_images.py",
))

print("Vitrail custom images contract: PASS")
