#!/usr/bin/env python3
"""Vitrail draws on Metal alone, and this contract is what keeps the deleted API out of the tree.

The product supports exactly one target: macOS on Apple Silicon, the Metallum backend mod, and Apple
Metal. The path that used to run beside it - its device, its command encoder, its render pass, its
pipeline, its descriptor sets, its barriers, its resource allocator, its sampler creation, its shader
modules, the startup page that offered it and the CI contract that recorded it - is gone, and the
whole point of this reading is that it cannot come back quietly. A single re-imported type compiles,
runs on a machine that is not the target, and is invisible in review; the failure mode this exists
for is exactly that.

**Where the reading is taken, and the one directory it deliberately does not read.** The scope is every
source root and every document a reader is sent to: `common/src/main/resources`, `fabric/src`,
`neoforge/src`, `docs`, `.context`, `.github`, and the root-level documents and build files. `tests/`
is out of scope, and that is not an oversight: a contract that refuses a name has to be able to write
the name down in order to refuse it, and this file does so on nearly every line. Reading itself would
make the guard unsatisfiable, so the guard reads everything a *shipment* is made of and leaves the
guards alone.

**Two binary false positives, and why the reading is line-based.** `docs/images/*.jpg` are JPEGs, and
compressed bytes hit an identifier pattern at random - three of the four screenshots contain a run like
`vkq` or `VKxpux` by chance. Ripgrep's default is to skip a file holding a NUL byte, and this reading
does the same rather than carrying an exemption for a picture whose bytes are incompressible anyway.
`grep -a -iE 'vulkan'` over those files is empty, which is the check that they are genuinely clean.

**The allowlist is a marker, not a path.** Exactly one line in the tree is permitted to spell the
deleted name, and it is a line in `glsl/CompilerMacros.java` holding glslang's own SPIR-V target macro:
shader packs test `#ifdef` on it, and the include expander evaluates `#ifdef` against that table before
the compiler sees the file, so dropping the entry changes which includes a pack takes. It is a
shader-pack contract rather than a backend detail, there is no API to read the macro's name from, and
it therefore carries an inline `no-vulkan-contract-allow:` marker with its reason. A marker and not a
path, because a path exemption outlives the reason it was granted for.

**The inventory is a migration counter, and it is meant to reach zero.** A few files still reach the
game's shader-compiler package, because the pack-visible half of that integration - zeroing locals
before the reflection reads them, letting a 3D sampler through the bind-group walk, and knowing what
the pack's uniforms and samplers are - needs a seam that does not exist yet on the backend's side. They
are listed by path with the reason each one is still there. A file that is *not* listed and names the
deleted API fails this contract; a listed file that no longer names it is reported so the entry is
retired, and the list reaching empty is the migration finishing rather than a target that moves.

Run `--self-test` to prove each rule fires: it writes synthetic trees under a temporary directory and
points the same checkers at them, so a guard that cannot fail fails the self-test instead of passing
quietly.
"""

from __future__ import annotations

import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# What a shipment is made of. `tests/` is absent on purpose - see the module docstring.
SCOPE = (
    "common/src/main/java",
    "common/src/main/resources",
    "fabric/src",
    "neoforge/src",
    "docs",
    ".context",
    ".github",
)
SCOPE_FILES = (
    "README.md",
    "INSTALL.md",
    "CONTRIBUTING.md",
    "CHANGELOG.md",
    "NOTICE",
    "AGENTS.md",
    "gradle.properties",
    "build.gradle",
    "settings.gradle",
    "common/build.gradle",
    "fabric/build.gradle",
    "neoforge/build.gradle",
)

# The names that may not appear again. Both spellings of the deleted API and of the vendor portability
# layer that used to present it on Apple hardware, the packages, and the API names - all matched
# case-insensitively, so a re-imported type, a reflection string, a mixin target and a document that
# merely mentions it are the same finding.
BANNED = re.compile(
    r"("
    r"vulkan"
    r"|moltenvk"
    r"|\bVK_[A-Z0-9_]+"
    r"|\bVk[A-Z][A-Za-z0-9_]*"
    r"|preferredgraphicsapi\.vulkan"
    r"|options\.graphicsapi\.vulkan"
    r")",
    re.IGNORECASE,
)

# A line permitted to spell it, with its reason beside it.
MARKER = "no-vulkan-contract-allow:"

# Files still reaching the game's shader-compiler package, each with why. This list is a migration
# counter and the goal is for it to be empty.
INVENTORY = {
    "common/src/main/java/dev/vitrail/cache/ModuleCache.java":
        "stores and rebuilds the game's compiled module, which the pack-visible half of the compile "
        "still hands around",
    "common/src/main/java/dev/vitrail/render/ComputeShader.java":
        "appends the pack's storage images and storage buffers to the game's reflected resource list",
    "common/src/main/java/dev/vitrail/render/GpuFormats.java":
        "asks the device which formats can be stored to, filtered and blitted, which the backend does "
        "not publish yet",
    "common/src/main/java/dev/vitrail/render/SamplerReach.java":
        "reads the game's compiled module to drop the sampled images an entry point never reaches",
    "common/src/main/java/dev/vitrail/mixin/GlslCompilerMixin.java":
        "zeros the pack's locals before the reflection reads them and lets a 3D sampler through",
    "common/src/main/java/dev/vitrail/mixin/IntermediaryShaderModuleMixin.java":
        "the same two hooks on the game's module type",
    "common/src/main/java/dev/vitrail/mixin/access/IntermediaryShaderModuleAccessor.java":
        "the accessor the two hooks above read the game's module through",
    "common/src/main/java/dev/vitrail/render/GlyphIntensity.java":
        "the font-sheet intensity swizzle, which is a texture-view request the backend cannot serve "
        "yet, so the view it wants is described here",
}

# Vendored, generated or historical paths that are not this repository's own surface. Empty on
# purpose: every exemption above is a marker or an inventory entry with a reason, and a path-based
# blanket exclusion is what this contract is written to avoid.
SKIP_DIRECTORIES = {".git", "build", ".gradle", "__pycache__", ".dsh-worktrees", "node_modules"}


def scanned_files(root: Path) -> list[Path]:
    """Every text file in scope, in a stable order, with binaries left out."""
    found: list[Path] = []
    for entry in SCOPE:
        base = root / entry
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file():
                continue
            if any(part in SKIP_DIRECTORIES for part in path.parts):
                continue
            found.append(path)
    for entry in SCOPE_FILES:
        path = root / entry
        if path.is_file():
            found.append(path)
    return found


def is_text(path: Path) -> bool:
    """Whether a file is text: no NUL byte, and decodable as UTF-8.

    The same rule ripgrep applies by default, which is what keeps a JPEG's compressed bytes from
    reading as an identifier.
    """
    try:
        raw = path.read_bytes()
    except OSError:
        return False
    if b"\0" in raw:
        return False
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError:
        return False
    return True


def relative(root: Path, path: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def residue(root: Path) -> dict[str, list[tuple[int, str]]]:
    """Every line naming the deleted API, per file, with the marker lines left out."""
    found: dict[str, list[tuple[int, str]]] = {}
    for path in scanned_files(root):
        if not is_text(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            if not BANNED.search(line):
                continue
            if MARKER in line:
                continue
            found.setdefault(relative(root, path), []).append((number, line.strip()))
    return found


def unmarked_lines(root: Path) -> list[str]:
    """Marker lines that carry no reason, which is a marker nobody can judge."""
    complaints: list[str] = []
    for path in scanned_files(root):
        if not is_text(path):
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if MARKER not in line:
                continue
            reason = line.split(MARKER, 1)[1].strip()
            if len(reason) < 20:
                complaints.append(
                    f"{relative(root, path)}:{number}: a {MARKER} marker with no reason beside it, "
                    f"so the exemption cannot be judged")
    return complaints


def unexpected(root: Path) -> list[str]:
    """Residue in a file that is neither marker-exempt nor on the inventory."""
    complaints: list[str] = []
    for name, lines in sorted(residue(root).items()):
        if name in INVENTORY:
            continue
        shown = ", ".join(str(number) for number, _ in lines[:6])
        complaints.append(
            f"{name}: names the deleted API on line(s) {shown}"
            + (f" and {len(lines) - 6} more" if len(lines) > 6 else ""))
    return complaints


def retired(root: Path) -> list[str]:
    """Inventory entries that are clean now, so the counter can be made smaller."""
    named = residue(root)
    return [name for name in sorted(INVENTORY) if name not in named]


def named_files(root: Path) -> list[str]:
    """Files whose own name carries the deleted API, at any depth."""
    found: list[str] = []
    for entry in SCOPE:
        base = root / entry
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file():
                continue
            if any(part in SKIP_DIRECTORIES for part in path.parts):
                continue
            if BANNED.search(path.name):
                found.append(relative(root, path))
    return found


def mixin_entries(root: Path) -> list[str]:
    """Vulkan names left in a mixin config, which would name a class that no longer exists."""
    config = root / "common/src/main/resources/vitrail.mixins.json"
    if not config.is_file():
        return []
    text = config.read_text(encoding="utf-8")
    return [line.strip() for line in text.splitlines() if BANNED.search(line)]


def evaluate(root: Path) -> tuple[list[str], list[str], int]:
    """The findings, the inventory entries that are clean now, and how many files were read.

    Split from the printing because the self-test asks the same question about a synthetic tree many
    times over, and a checker that narrates each of those runs buries its own verdict.
    """
    problems = unexpected(root) + unmarked_lines(root) + named_files(root) + mixin_entries(root)
    return problems, retired(root), len(scanned_files(root))


def report(root: Path) -> int:
    problems, gone, count = evaluate(root)
    if problems:
        print("no-Vulkan surface contract: FAIL")
        for problem in problems:
            print("  " + problem)
        print()
        print("Vitrail draws on Metal alone. A file that needs one of these names is either a new "
              "dependency that has to be removed, or a file that has to join INVENTORY with the "
              "reason it is still there.")
        return 1

    entries = len(INVENTORY)
    print(f"no-Vulkan surface contract: PASS ({count} files read, "
          f"{entries} inventory entr{'y' if entries == 1 else 'ies'} outstanding)")
    if gone:
        # Not a failure: the entry is stale, and a counter that is not maintained is worse than none.
        print("  the inventory is now smaller than it says - these are clean, so drop them:")
        for name in gone:
            print("    " + name)
    return 0


def _write(root: Path, name: str, text: str) -> None:
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _sources(root: Path, java: str) -> None:
    _write(root, "common/src/main/java/dev/vitrail/Probe.java", java)


def self_test() -> None:
    """Each rule is asked to fire on a tree built for it, and to stay quiet on a clean one."""
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)

        # 1. A clean tree passes, and the inventory is reported as outstanding rather than missing.
        _sources(root, "package dev.vitrail;\n\n/** A clean class. */\nfinal class Probe {\n}\n")
        if evaluate(root)[0]:
            raise SystemExit("no-Vulkan self-test failed: a clean tree was reported")
        for name in INVENTORY:
            _write(root, name, "package dev.vitrail;\n\nfinal class Stub {\n}\n")
        problems, gone, _ = evaluate(root)
        if problems:
            raise SystemExit("no-Vulkan self-test failed: an inventory file with no residue was "
                             "reported as a failure rather than as a retired counter")
        if sorted(gone) != sorted(INVENTORY):
            raise SystemExit("no-Vulkan self-test failed: a clean inventory entry was not named as "
                             "retired, so the counter would never shrink")

        # 2. A re-imported type is caught, in a file that is not on the inventory.
        _sources(root, "package dev.vitrail;\n\nimport com.mojang.blaze3d.vulkan.VulkanDevice;\n"
                       "\nfinal class Probe {\n}\n")
        if not unexpected(root):
            raise SystemExit("no-Vulkan self-test failed: an imported device type was not reported")
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n}\n")

        # 3. A bare mention in prose is caught too: the point is the word, not the type.
        _sources(root, "package dev.vitrail;\n\n/** Runs on the other renderer. */\n"
                       "final class Probe {\n}\n")
        _sources(root, "package dev.vitrail;\n\n/** Runs on the other renderer, called "
                       "MoLTenVk here. */\nfinal class Probe {\n}\n")
        if not unexpected(root):
            raise SystemExit("no-Vulkan self-test failed: a bare prose mention was not reported")
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n}\n")

        # 4. A marker with a reason is allowed; a marker with nothing beside it is not.
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n"
                       "\tstatic final String M = \"vulkan\"; // no-vulkan-contract-allow: the "
                       "compiler's own target macro, a pack-visible contract\n}\n")
        if unexpected(root):
            raise SystemExit("no-Vulkan self-test failed: a marked line was reported as residue")
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n"
                       "\tstatic final String M = \"vulkan\"; // no-vulkan-contract-allow:\n}\n")
        if not unmarked_lines(root):
            raise SystemExit("no-Vulkan self-test failed: a marker with no reason was accepted")
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n}\n")

        # 5. A file whose own name carries it is caught, and a mixin entry too.
        _write(root, "common/src/main/java/dev/vitrail/VulkanProbe.java", "package dev.vitrail;\n")
        if not named_files(root):
            raise SystemExit("no-Vulkan self-test failed: a file named after the API was not reported")
        (root / "common/src/main/java/dev/vitrail/VulkanProbe.java").unlink()
        _write(root, "common/src/main/resources/vitrail.mixins.json",
               '{\n\t"client": [\n\t\t"VulkanBackendMixin"\n\t]\n}\n')
        if not mixin_entries(root):
            raise SystemExit("no-Vulkan self-test failed: a mixin config naming a deleted class was "
                             "not reported")
        _write(root, "common/src/main/resources/vitrail.mixins.json", '{\n\t"client": []\n}\n')

        # 6. An inventory entry is a counter, not a blanket exemption: it is keyed on the exact file,
        #    so a listed path is not residue while an unlisted one always is.
        listed = sorted(INVENTORY)[0]
        _sources(root, "package dev.vitrail;\n\nfinal class Probe {\n}\n")
        _write(root, listed, "package dev.vitrail;\n\nfinal class Stub {\n}\n")
        if listed in unexpected(root):
            raise SystemExit("no-Vulkan self-test failed: an inventory entry was treated as residue")
        for name in INVENTORY:
            _write(root, name, "package dev.vitrail;\n\nfinal class Stub {\n}\n")
        if evaluate(root)[0]:
            raise SystemExit("no-Vulkan self-test failed: a retired inventory entry failed the run")

    print("no-Vulkan surface contract: self-test PASS (an imported type, a prose mention, an "
          "unreasoned marker, a named file and a mixin entry are each caught, and a marked line and "
          "an inventory entry each pass)")


def main() -> int:
    if "--self-test" in sys.argv:
        self_test()
        return 0
    return report(ROOT)


if __name__ == "__main__":
    sys.exit(main())
