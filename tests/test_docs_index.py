#!/usr/bin/env python3
"""Every page under `docs/` is routed from the index, and every link in it resolves.

`docs/README.md` is the router: a reader who does not already know the file names has nowhere else to
start. Its value is that it is complete, and its risk is that nothing notices when it stops being - a
page added and not listed is a page only its author knows about, and a page renamed out from under a
link is a reader landing on nothing in the one place they went for directions.

Checked BOTH ways, because one direction alone is a word count: every page in `docs/` is named in the
index, and every link in every page is resolved. `--self-test` builds a tree that is wrong in exactly
those two ways and requires the check to refuse it, which is what `test_backend_neutrality_contract.py`
does for its own matcher.
"""
from __future__ import annotations

import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
INDEX = DOCS / "README.md"

# `](path)` and `](path#anchor)`. A link with a scheme is somebody else's page and is not this contract's.
LINK = re.compile(r"\]\(([^)\s]+?)(?:#[^)\s]*)?\)")


def pages(docs: Path) -> list[Path]:
    return sorted(path for path in docs.rglob("*.md") if path != docs / "README.md")


def problems(docs: Path) -> list[str]:
    index = docs / "README.md"
    if not index.is_file():
        return [f"{index} is missing, so the pages have no router"]

    text = index.read_text(encoding="utf-8")
    found: list[str] = []
    for page in pages(docs):
        if page.name not in text:
            found.append(f"{page.relative_to(docs)} is in docs/ and is named nowhere in the index")
    for source in [index] + pages(docs):
        for target in LINK.findall(source.read_text(encoding="utf-8")):
            if "://" in target or target.startswith(("#", "mailto:")):
                continue
            if not (source.parent / target).exists() and not (ROOT / target).exists():
                # Relative to `docs/`, because the self-test runs this over a tree outside the repository and a
                # path that cannot be made relative to ROOT raises instead of reporting the problem.
                found.append(f"{source.relative_to(docs)} links to {target}, which does not exist")
    return found


def self_test() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        docs = Path(temporary) / "docs"
        (docs / "internals").mkdir(parents=True)
        (docs / "listed.md").write_text("# Listed\n", encoding="utf-8")
        (docs / "internals" / "unlisted.md").write_text("# Unlisted\n", encoding="utf-8")
        (docs / "README.md").write_text("# Index\n\n[listed](listed.md)\n[gone](vanished.md)\n", encoding="utf-8")

        found = problems(docs)
        if len(found) != 2 or not any("unlisted.md" in one for one in found) \
                or not any("vanished.md" in one for one in found):
            for one in found:
                print(f"  {one}", file=sys.stderr)
            raise SystemExit(f"docs index self-test: {len(found)} problems found in a tree with exactly two, so "
                             f"one direction is measuring something else")

        (docs / "internals" / "unlisted.md").unlink()
        (docs / "README.md").write_text("# Index\n\n[listed](listed.md)\n", encoding="utf-8")
        if problems(docs):
            raise SystemExit(f"docs index self-test: a complete index with a resolving link was refused: "
                             f"{problems(docs)}")
    print("docs index self-test: PASS")


if __name__ == "__main__":
    if "--self-test" in sys.argv[1:]:
        self_test()
        raise SystemExit(0)

    found = problems(DOCS)
    if found:
        raise SystemExit("docs index: " + "; ".join(found))
    print(f"Vitrail docs index contract: PASS ({len(pages(DOCS))} pages routed, every link resolves)")
