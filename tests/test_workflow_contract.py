"""CI must not author commits, and must not hold the permission that would let it.

A workflow that writes to the repository is an author nobody reviewed. The companion Metallum
repository carried one -- `.github/workflows/apply-graphics-storage-image-fix.yml` -- which rewrote
two source files, committed them and pushed the result back to the branch that triggered it. Every
later push therefore re-ran it against sources it had already patched, and the run reported a
failure each time. It has been deleted there, and this repository is checked for the same shape
rather than for that file: the next one would be written the same way.

`release.yml` is the one workflow allowed repository content write, because publishing a release
needs it. It runs on a `v*` tag, creates a release, and authors no commit.

The checks read each workflow over its whole text instead of matching one spelling of the incident.
`contents: write` is not the only way to hold repository write: `permissions: write-all` grants it
without naming contents, and a quoted value or a trailing comment defeats an anchored literal. A
commit does not have to be written `git commit` either: `git -c user.email=... commit`, a run of
spaces or a `\\` continuation runs the same command. A guard that knew only the literal spellings
would report PASS on the very file it exists to refuse, so the matcher is pinned by the tests below
that feed it those variants directly.

This is a contract test rather than a step in `commits.yml` because every static contract in this
repository lives in `tests/` and is run by `build.yml`; `commits.yml` has one subject of its own,
the shape of a commit message, and shares it with the hook a contributor installs.

It also holds the other half of that claim: a contract script that no workflow names asserts nothing
while still looking like coverage, which is the state seven scripts in `tests/` were in.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github/workflows"

# Publishing a release needs the permission; nothing else in this repository does.
WRITE_ALLOWED = {"release.yml"}

# The git options that take a separate value, so `git -c user.name=x commit` still reaches `commit`
# rather than stopping on the option's value.
GIT_OPTIONS_WITH_VALUES = ("-c", "-C", "--git-dir", "--work-tree", "--namespace", "--exec-path")
COMMITTING_SUBCOMMANDS = ("commit", "push")
# Any published action that commits, pushes or opens a request on the workflow's own behalf, under
# any owner. Naming two actions would have missed the third.
COMMITTING_ACTION = re.compile(r"(?i)\buses:\s*[^\s#]*(commit|push|create-pull-request|add-and-commit|git-auto)")


def workflow_files(directory=None):
    directory = WORKFLOWS if directory is None else Path(directory)
    return sorted(path for path in directory.glob("*.y*ml") if path.is_file())


def read_workflows(directory=None):
    directory = WORKFLOWS if directory is None else Path(directory)
    return {path.name: path.read_text(encoding="utf-8") for path in workflow_files(directory)}


def unquote(value):
    """Drop a trailing comment and the quoting a YAML scalar is allowed to carry."""
    return re.split(r"\s+#", value, maxsplit=1)[0].strip().strip("'\"")


def without_comments(text):
    """The live lines of a workflow, with continuations joined.

    Workflow prose explains commands it does not run: `prefix.yml` documents the hazard it detects
    as `git push origin origin/dev:main` in a comment, which is documentation of the gesture rather
    than the gesture, so comment lines are dropped before anything is matched. A `\\` continuation
    splits one command across two lines and is joined at the same time; a line-by-line scan would
    otherwise see neither half as a command.
    """
    joined = text.replace("\\\n", " ")
    return "\n".join(line for line in joined.splitlines() if not line.lstrip().startswith("#"))


def git_subcommands(line):
    """The git subcommand each `git` invocation on this line actually runs."""
    found = []
    for match in re.finditer(r"\bgit\b", line):
        tokens = line[match.end():].split()
        index = 0
        while index < len(tokens):
            token = tokens[index]
            if token in GIT_OPTIONS_WITH_VALUES:
                index += 2
                continue
            if token.startswith("-"):
                index += 1
                continue
            found.append(token.strip("'\";|&()"))
            break
    return found


def declared_permissions(text):
    """The workflow-level `permissions:` mapping, or None when a workflow declares none."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if re.match(r"^permissions:", line) is None:
            continue
        inline = unquote(line.split(":", 1)[1])
        if inline:
            if inline in ("{}", "read-all"):
                return {}
            if inline == "write-all":
                # Every scope, contents included, without ever naming `contents`.
                return {"contents": "write"}
            return {"unrecognised": inline}
        granted = {}
        for follower in lines[index + 1:]:
            if follower.strip() and follower[:1] not in (" ", "\t"):
                break
            key, separator, value = follower.strip().partition(":")
            if separator and key.strip():
                granted[key.strip()] = unquote(value)
        return granted
    return None


def committing_workflows(workflows):
    """Every workflow that could author a commit, as `name: what matched`."""
    found = []
    for name, text in workflows.items():
        body = without_comments(text)
        for line in body.splitlines():
            for subcommand in git_subcommands(line):
                if subcommand in COMMITTING_SUBCOMMANDS:
                    found.append(f"{name}: git {subcommand}")
        action = COMMITTING_ACTION.search(body)
        if action is not None:
            found.append(f"{name}: {action.group().strip()}")
    return found


def content_writers(workflows):
    """Every workflow that holds repository content write, however the permission is spelled."""
    return sorted(
        name for name, text in workflows.items()
        if (declared_permissions(text) or {}).get("contents") == "write"
    )


def workflows_without_permissions(workflows):
    return sorted(name for name, text in workflows.items() if declared_permissions(text) is None)


def contract_scripts_without_a_workflow(workflows):
    """Every contract script in tests/ that no workflow names, so it asserts nothing in CI.

    Wiring is per file because `build.yml` names its scripts one by one; the ones carrying a
    dedicated workflow are named there instead. `tests/phase17_*.py` are command-line tools that take
    arguments rather than contracts, so only the `test_`/`verify_` prefixed files are considered.
    """
    named = "\n".join(workflows.values())
    scripts = list((ROOT / "tests").glob("test_*.py")) + list((ROOT / "tests").glob("verify_*.py"))
    return sorted(path.name for path in scripts if f"tests/{path.name}" not in named)


def step(body, permissions="permissions:\n  contents: read\n"):
    return f"name: tmp\non:\n  pull_request:\n{permissions}jobs:\n  tmp:\n    runs-on: macos-latest\n    steps:\n{body}"


class WorkflowContract(unittest.TestCase):
    def test_the_workflow_set_is_not_empty(self):
        self.assertTrue(workflow_files(), "no workflow files found under .github/workflows")

    def test_no_workflow_can_author_a_commit(self):
        self.assertEqual(
            committing_workflows(read_workflows()), [],
            "CI must not write to a branch. Delete the workflow instead of letting Actions become "
            "an author.",
        )

    def test_contents_write_is_reserved_for_release_publishing(self):
        self.assertEqual(
            content_writers(read_workflows()), sorted(WRITE_ALLOWED),
            "repository content write is reserved for release.yml, which publishes a release on a "
            "v* tag and authors no commit",
        )

    def test_every_workflow_states_its_permissions(self):
        self.assertEqual(
            workflows_without_permissions(read_workflows()), [],
            "every workflow must declare `permissions:` explicitly, so it cannot inherit a "
            "repository default that happens to permit writes",
        )

    def test_every_contract_script_is_run_by_a_workflow(self):
        # A contract that no workflow runs asserts nothing while still looking like coverage. Seven
        # scripts were in exactly that state, reached only by a hand-run command, which is why this
        # is checked rather than trusted.
        self.assertEqual(
            contract_scripts_without_a_workflow(read_workflows()), [],
            "these scripts are named by no workflow, so their contracts are not enforced; name each "
            "in build.yml or give it a workflow of its own",
        )

    # The three checks above read the real tree, so they cannot show that the matcher recognises a
    # spelling it has not met yet. These feed it the variants a literal substring test missed.

    def test_a_commit_is_recognised_however_it_is_written(self):
        for body in (
            "      - run: git commit -m fix\n",
            "      - run: git push origin HEAD:main\n",
            "      - run: git -c user.name=bot -c user.email=bot@example.com commit -m fix\n",
            "      - run: git -c user.name=bot push origin HEAD:main\n",
            "      - run: git  push origin HEAD\n",
            "      - run: git \\\n          commit -m fix\n",
            "      - run: git add . && git commit -m fix\n",
            "      - uses: EndBug/add-and-commit@v9\n",
            "      - uses: ad-m/github-push-action@master\n",
            "      - uses: stefanzweifel/git-auto-commit-action@v5\n",
        ):
            with self.subTest(body=body):
                self.assertTrue(committing_workflows({"tmp.yml": step(body)}), body)

    def test_repository_write_is_recognised_however_it_is_spelled(self):
        for permissions in (
            "permissions:\n  contents: write\n",
            "permissions:\n  contents: \"write\"\n",
            "permissions:\n  contents: write  # publish the release\n",
            "permissions: write-all\n",
        ):
            with self.subTest(permissions=permissions):
                body = step("      - run: echo hi\n", permissions)
                self.assertEqual(content_writers({"tmp.yml": body}), ["tmp.yml"], permissions)

    def test_a_documented_command_is_not_a_command(self):
        # `prefix.yml` explains the hazard it detects. Refusing the file that documents the rule
        # would be its own defect.
        documented = "# The gesture it is guarding is one line, `git push origin origin/dev:main`\n"
        self.assertEqual(committing_workflows({"prefix.yml": documented}), [])

    def test_the_git_calls_this_repository_already_makes_stay_legal(self):
        for subcommand in (
            "fetch --no-tags origin main",
            "log --oneline origin/dev..HEAD",
            "log -1 --format=%B HEAD",
            "diff --name-only HEAD",
            "rev-list --reverse main..HEAD",
            "merge-base --is-ancestor a b",
        ):
            with self.subTest(subcommand=subcommand):
                self.assertEqual(
                    committing_workflows({"tmp.yml": step(f"      - run: git {subcommand}\n")}), [],
                    subcommand,
                )

    def test_reading_a_permission_is_not_holding_it(self):
        # Only `contents` decides repository content write: a workflow may still need to label a
        # pull request, and `{}` (or `read-all`) grants nothing at all.
        for permissions in (
            "permissions:\n  pull-requests: write\n",
            "permissions: {}\n",
            "permissions: read-all\n",
            "permissions:\n  contents: read\n",
        ):
            with self.subTest(permissions=permissions):
                body = step("      - run: echo hi\n", permissions)
                self.assertEqual(content_writers({"tmp.yml": body}), [], permissions)


if __name__ == "__main__":
    unittest.main()
