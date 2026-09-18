"""CI must not author commits, and must not hold the permission that would let it.

A workflow that writes to the repository is an author nobody reviewed. The companion Metallum
repository carried one -- `.github/workflows/apply-graphics-storage-image-fix.yml` -- which rewrote
two source files, committed them and pushed the result back to the branch that triggered it. Every
later push therefore re-ran it against sources it had already patched, and the run reported a
failure each time. It has been deleted there, and this repository is checked for the same shape
rather than for that file: the next one would be written the same way.

`release.yml` is the one workflow allowed to write, because publishing a release needs it. It runs
on a `v*` tag, creates a release, and authors no commit.

This is a contract test rather than a step in `commits.yml` because every static contract in this
repository lives in `tests/` and is run by `build.yml`; `commits.yml` has one subject of its own,
the shape of a commit message, and shares it with the hook a contributor installs.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github/workflows"

# What a `run:` step would execute, and the published actions that do the same without saying so.
AUTOCOMMITTING = (
    "git commit",
    "git push",
    "git-auto-commit-action",
    "create-pull-request@",
)

# Publishing a release needs the permission; nothing else in this repository does.
WRITE_ALLOWED = {"release.yml"}


def workflow_files():
    return sorted(path for path in WORKFLOWS.glob("*.y*ml") if path.is_file())


def live_lines(path):
    """Workflow prose explains commands it does not run.

    `prefix.yml` documents the hazard it detects as `git push origin origin/dev:main`, in a comment.
    That is documentation of the gesture, not the gesture, so comment lines are dropped before
    anything is matched. A guard that cannot tell those apart would refuse the file that explains
    the rule it is enforcing.
    """
    return "\n".join(
        line for line in path.read_text(encoding="utf-8").splitlines()
        if not line.lstrip().startswith("#")
    )


class WorkflowContract(unittest.TestCase):
    def test_the_workflow_set_is_not_empty(self):
        self.assertTrue(workflow_files(), "no workflow files found under .github/workflows")

    def test_no_workflow_can_author_a_commit(self):
        for path in workflow_files():
            body = live_lines(path)
            for needle in AUTOCOMMITTING:
                self.assertNotIn(
                    needle, body,
                    f"{path.name} contains `{needle}`; CI must not write to a branch. Delete the "
                    "workflow instead of letting Actions become an author.",
                )

    def test_contents_write_is_reserved_for_release_publishing(self):
        writers = {
            path.name for path in workflow_files()
            if re.search(r"^\s+contents:\s*write\s*$", path.read_text(encoding="utf-8"), re.MULTILINE)
        }
        self.assertEqual(
            writers, WRITE_ALLOWED,
            "`contents: write` is reserved for release.yml, which publishes a release on a v* tag "
            "and authors no commit",
        )

    def test_every_workflow_states_its_permissions(self):
        for path in workflow_files():
            self.assertIsNotNone(
                re.search(r"^permissions:", path.read_text(encoding="utf-8"), re.MULTILINE),
                f"{path.name} must declare `permissions:` explicitly, so it cannot inherit a "
                "repository default that happens to permit writes",
            )


if __name__ == "__main__":
    unittest.main()
