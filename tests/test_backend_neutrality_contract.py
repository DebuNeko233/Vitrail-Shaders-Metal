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

That first reading cannot see the string form, and the string form is the one that has already cost a real
regression: a `@Mixin(targets = "com.metallum.render.metal3.MetalCommandEncoder")` is resolved at runtime, so
when Metallum moved that class into its generation package the injection stopped applying, the capabilities
it carried disappeared, and the only symptom was darker shadows. The second reading therefore strips comments
**only** - a string is code here - and refuses the generation package prefixes wherever they are written,
including as a path. The stable flat surface (`com.metallum.render.MetalFrameBridge` and its siblings) is what
remains nameable, and it is nameable in a string on purpose.
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

# The generation packages. Naming one of these from the pack-facing engine - in code or in a string - would
# tie this repository to where Metallum happens to keep a class today.
GENERATION_PACKAGES = (
    "com.metallum.render.metal3.",
    "com.metallum.render.metal4.",
    "com.metallum.mtl.metal3.",
    "com.metallum.mtl.metal4.",
)

_LITERAL = r'"(?:\\.|[^"\\])*"'

# One expression, one pass, so a `//` inside a literal is not a comment and a quote inside a comment does not
# open a literal. Group 1 is the literal, which is what tells the two readings apart.
_COMMENTS_AND_LITERALS = re.compile(
    rf"({_LITERAL})"
    r"|//[^\n]*"
    r"|/\*.*?\*/",
    re.DOTALL,
)


def _blanked(match: re.Match[str]) -> str:
    return "\n" * match.group(0).count("\n")


def code_only(source: str) -> str:
    """The file without comments and string literals, so the reading is of the code and not of the prose."""
    return _COMMENTS_AND_LITERALS.sub(_blanked, source)


def literals_kept(source: str) -> str:
    """The file without comments, with string literals kept - a comment explains, a string is resolved."""
    return _COMMENTS_AND_LITERALS.sub(
        lambda match: match.group(1) if match.group(1) is not None else _blanked(match),
        source,
    )


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


def package_violations(root: Path) -> list[str]:
    """Every generation package name under this source root, as `path:line: package`.

    Comments are stripped and string literals are not, because the leak this reading exists for is a runtime
    string: a `@Mixin(targets = ...)` or a `Class.forName(...)` naming a generation class keeps compiling and
    stops working. The write is checked in both separators, since a path is the same leak spelled differently.
    """
    forms = tuple(
        form
        for package in GENERATION_PACKAGES
        for form in (package, package.replace(".", "/"))
    )
    found: list[str] = []
    for path in sorted(root.rglob("*.java")):
        code = literals_kept(path.read_text(encoding="utf-8"))
        for form in forms:
            for match in re.finditer(re.escape(form), code):
                line = code.count("\n", 0, match.start()) + 1
                found.append(f"{path.relative_to(root)}:{line}: {form}")
    return found


def scanned(root: Path) -> int:
    return len(list(root.rglob("*.java")))


def self_test() -> None:
    """Prove the rules fire, because a guard that cannot fail is not a guard.

    Both readings are checked in both directions: a synthetic file that names a command type or a generation
    package is caught, and one that names only what the seam is allowed to speak about - a resource type, or
    the stable flat surface - is not.
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

        # The second reading: the same file leaks nothing by module, and a comment may still name a package.
        if package_violations(root):
            raise SystemExit(
                "backend neutrality self-test failed: a file that names no generation package was reported"
            )

        clean.write_text(
            "package dev.vitrail.render;\n"
            "// An explanation may name com.metallum.render.metal3.MetalCommandEncoder.\n"
            "public final class Good {\n"
            "    private static final String CLASS_NAME = "
            "\"com.metallum.render.MetalFrameBridge\";\n"
            "}\n",
            encoding="utf-8",
        )
        if package_violations(root):
            raise SystemExit(
                "backend neutrality self-test failed: the stable flat surface, named in a string or in a "
                "comment, was reported"
            )

        clean.write_text(
            "package dev.vitrail.render;\n"
            "import org.spongepowered.asm.mixin.Mixin;\n"
            "@Mixin(targets = \"com.metallum.render.metal3.MetalCommandEncoder\", remap = false)\n"
            "public abstract class Good {\n}\n",
            encoding="utf-8",
        )
        found = package_violations(root)
        if not found or "com.metallum.render.metal3." not in found[0]:
            raise SystemExit(
                "backend neutrality self-test failed: a mixin target naming a generation package was not "
                "reported"
            )

        clean.write_text(
            "package dev.vitrail.render;\n"
            "public final class Good {\n"
            "    private static final String PATH = \"com/metallum/mtl/metal4/Compiler\";\n"
            "}\n",
            encoding="utf-8",
        )
        found = package_violations(root)
        if not found or "com/metallum/mtl/metal4/" not in found[0]:
            raise SystemExit(
                "backend neutrality self-test failed: a generation package written as a path was not reported"
            )

    print(
        "backend neutrality self-test: PASS (a command type and a generation package are caught, a resource "
        "type and the stable flat surface are not)"
    )


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

    leaked = package_violations(SOURCE_ROOT)
    if leaked:
        for line in leaked:
            print(line, file=sys.stderr)
        raise SystemExit(
            f"backend neutrality: {len(leaked)} generation package name(s) in common/, which must name only "
            "the stable flat surface"
        )

    print(
        f"backend neutrality: PASS ({files} sources, {len(BANNED)} command types and "
        f"{len(GENERATION_PACKAGES)} generation packages refused)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
