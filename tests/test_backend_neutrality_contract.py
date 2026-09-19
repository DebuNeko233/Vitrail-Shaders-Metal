#!/usr/bin/env python3
"""Vitrail's half of the architecture guard: `common/` does not know which command API is executing.

Vitrail owns shader-pack semantics, scheduling, the target plan and fallback. Metallum owns Metal, and
which command generation is executing - Metal 3's `MTLCommandBuffer` or Metal 4's `MTL4CommandBuffer` - is
Metallum's business alone. The two repositories meet at narrow semantic interfaces, and a native command
type reaching `common/` would be the end of that: the pack-facing side would compile against one
generation's command API, and the other generation would be a second implementation of an interface that
is no longer neutral.

So this contract reads every source under `common/src/main/java` and fails on a command-generation type
name. Resources are deliberately not on the list - `MTLTexture`, `MTLBuffer`, a sampler state, a pipeline
state, a fence, `CAMetalLayer` - because those are concepts both generations share, and the seam is
allowed to speak about resources. What it may not speak about is how a command buffer is made, begun,
encoded into or committed.

A comment or a javadoc may name anything: the point of a comment is to explain, and the reading is of the
code, with comments and string literals removed first. That is also what keeps a reflection string such as
the `MetallumApi` method name it looks up from reading as a type.
"""

from __future__ import annotations

import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = ROOT / "common/src/main/java"

# The command-generation types. Anything whose presence in `common/` would tie the engine to one
# generation's command API.
BANNED = (
    "MTLCommandQueue",
    "MTLCommandBuffer",
    "MTLCommandEncoder",
    "MTLRenderCommandEncoder",
    "MTLComputeCommandEncoder",
    "MTLBlitCommandEncoder",
    "MTL4CommandQueue",
    "MTL4CommandBuffer",
    "MTL4CommandAllocator",
    "MTL4CommandEncoder",
    "MTL4RenderCommandEncoder",
    "MTL4ComputeCommandEncoder",
    "MTL4ArgumentTable",
    "MTL4CommitOptions",
    "MTL4CommitFeedback",
)

_COMMENT_AND_LITERAL = re.compile(
    r"//[^\n]*"
    r"|/\*.*?\*/"
    r'|"(?:\\.|[^"\\])*"',
    re.DOTALL,
)


def code_only(source: str) -> str:
    """The file without comments and string literals, so the reading is of the code and not of the prose."""
    return _COMMENT_AND_LITERAL.sub(lambda match: "\n" * match.group(0).count("\n"), source)


def violations(root: Path) -> list[str]:
    """Every command-generation type name under this source root, as `path:line: type`."""
    found: list[str] = []
    for path in sorted(root.rglob("*.java")):
        code = code_only(path.read_text(encoding="utf-8"))
        for type_name in BANNED:
            for match in re.finditer(rf"\b{re.escape(type_name)}\b", code):
                line = code.count("\n", 0, match.start()) + 1
                found.append(f"{path.relative_to(root)}:{line}: {type_name}")
    return found


def scanned(root: Path) -> int:
    return len(list(root.rglob("*.java")))


def self_test() -> None:
    """Prove the rule fires, because a guard that cannot fail is not a guard.

    Both directions are checked: a synthetic file that names a command type is caught, and a synthetic file
    that names the resource types the seam is allowed to speak about is not.
    """
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        violating = root / "dev/vitrail/render/Bad.java"
        violating.parent.mkdir(parents=True, exist_ok=True)
        violating.write_text(
            "package dev.vitrail.render;\n"
            "public final class Bad {\n"
            "    private long buffer;\n"
            "    // A comment may name MTLCommandBuffer, because a comment explains.\n"
            "    private String name = \"MTL4CommandBuffer\";\n"
            "}\n"
            "// class Javadoc: MTLRenderCommandEncoder\n",
            encoding="utf-8",
        )
        if violations(root):
            raise SystemExit(
                "backend neutrality self-test failed: a file that only names a command type in a comment or "
                "a string was reported"
            )

        violating.write_text(
            "package dev.vitrail.render;\n"
            "import com.mojang.blaze3d.MTLCommandBuffer;\n"
            "public final class Bad {\n    private MTLCommandBuffer commands;\n}\n",
            encoding="utf-8",
        )
        found = violations(root)
        if not found or "MTLCommandBuffer" not in found[0]:
            raise SystemExit("backend neutrality self-test failed: an imported command type was not reported")

        clean = root / "dev/vitrail/render/Good.java"
        clean.write_text(
            "package dev.vitrail.render;\n"
            "import com.mojang.blaze3d.GpuTexture;\n"
            "/** Both command generations hand this back, which is why it may be named here. */\n"
            "public final class Good {\n    private GpuTexture texture;\n}\n",
            encoding="utf-8",
        )
        for line in violations(root):
            if "Good.java" in line:
                raise SystemExit(f"backend neutrality self-test failed: a compliant file was reported: {line}")

    print("backend neutrality self-test: PASS (a command type is caught, a resource type is not)")


def main() -> int:
    if "--self-test" in sys.argv:
        self_test()
        return 0

    files = scanned(SOURCE_ROOT)
    if files < 40:
        raise SystemExit(
            f"only {files} sources under {SOURCE_ROOT}, which is too few to be the engine's common source"
        )

    found = violations(SOURCE_ROOT)
    if found:
        for line in found:
            print(line, file=sys.stderr)
        raise SystemExit(
            f"backend neutrality: {len(found)} command-generation type name(s) in common/, which must not "
            "know which command API is executing"
        )

    print(f"backend neutrality: PASS ({files} sources, {len(BANNED)} command types refused)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
