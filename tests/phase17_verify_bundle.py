#!/usr/bin/env python3
"""Verify the integrity and shape of one PHASE 17 real-pack review bundle.

The verifier intentionally does not classify compatibility and does not require the
shader-pack artifact to be present. It validates the evidence schema, proves that the
bundled log and screenshot match their recorded SHA-256 values, and rejects extra
files so a shader-pack artifact cannot silently leak into the review bundle.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import tarfile
from pathlib import Path, PurePosixPath
from typing import BinaryIO

HERE = Path(__file__).resolve().parent
COMPATIBILITY_PATH = HERE / "phase17_compatibility.py"
spec = importlib.util.spec_from_file_location("phase17_compatibility", COMPATIBILITY_PATH)
compatibility = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(compatibility)

REQUIRED_FILES = frozenset({"evidence.json", "latest.log", "screenshot.png"})
MAX_EVIDENCE_BYTES = 2 * 1024 * 1024


class BundleError(ValueError):
    pass


def _safe_parts(name: str) -> tuple[str, ...]:
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts:
        raise BundleError(f"unsafe bundle member path: {name!r}")
    parts = tuple(part for part in path.parts if part not in ("", "."))
    if not parts:
        raise BundleError("bundle contains an empty member path")
    return parts


def _sha256_stream(stream: BinaryIO) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def _expected_digest(value: object, label: str) -> str:
    if not isinstance(value, str):
        raise BundleError(f"{label} must be a SHA-256 string")
    digest = value.lower()
    if len(digest) != 64 or any(ch not in "0123456789abcdef" for ch in digest):
        raise BundleError(f"{label} must be a 64-character SHA-256 hex digest")
    return digest


def verify_bundle(bundle: Path) -> dict:
    if not bundle.is_file():
        raise BundleError(f"review bundle does not exist: {bundle}")

    try:
        archive = tarfile.open(bundle, mode="r:gz")
    except (OSError, tarfile.TarError) as exc:
        raise BundleError(f"cannot open review bundle: {exc}") from exc

    with archive:
        files: dict[str, tarfile.TarInfo] = {}
        root: str | None = None
        for member in archive.getmembers():
            parts = _safe_parts(member.name)
            if member.issym() or member.islnk():
                raise BundleError(f"links are not allowed in review bundles: {member.name}")
            if member.isdir():
                continue
            if not member.isfile():
                raise BundleError(f"unsupported bundle member type: {member.name}")
            if len(parts) != 2:
                raise BundleError(f"review files must live directly under one bundle directory: {member.name}")
            member_root, filename = parts
            if root is None:
                root = member_root
            elif member_root != root:
                raise BundleError("review bundle contains more than one top-level directory")
            if filename in files:
                raise BundleError(f"duplicate review file: {filename}")
            files[filename] = member

        present = frozenset(files)
        if present != REQUIRED_FILES:
            missing = sorted(REQUIRED_FILES - present)
            extra = sorted(present - REQUIRED_FILES)
            details = []
            if missing:
                details.append("missing " + ", ".join(missing))
            if extra:
                details.append("unexpected " + ", ".join(extra))
            raise BundleError("invalid review bundle contents: " + "; ".join(details))

        evidence_member = files["evidence.json"]
        if evidence_member.size > MAX_EVIDENCE_BYTES:
            raise BundleError("evidence.json is unexpectedly large")
        evidence_stream = archive.extractfile(evidence_member)
        if evidence_stream is None:
            raise BundleError("cannot read evidence.json")
        try:
            record = json.loads(evidence_stream.read().decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise BundleError(f"invalid evidence.json: {exc}") from exc
        if not isinstance(record, dict):
            raise BundleError("evidence.json must contain an object")
        try:
            compatibility.validate(record)
        except compatibility.EvidenceError as exc:
            raise BundleError(f"invalid PHASE 17 evidence: {exc}") from exc

        observations = record.get("observations")
        if not isinstance(observations, dict):
            raise BundleError("evidence observations must be an object")
        expected_log = _expected_digest(observations.get("log_sha256"), "observations.log_sha256")
        expected_screenshot = _expected_digest(
            observations.get("screenshot_sha256"), "observations.screenshot_sha256"
        )

        for filename, expected in (("latest.log", expected_log), ("screenshot.png", expected_screenshot)):
            stream = archive.extractfile(files[filename])
            if stream is None:
                raise BundleError(f"cannot read {filename}")
            actual = _sha256_stream(stream)
            if actual != expected:
                raise BundleError(f"{filename} SHA-256 mismatch: evidence records {expected}, bundle has {actual}")

    return record


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="PHASE 17 .tar.gz review bundle")
    args = parser.parse_args(argv)

    try:
        record = verify_bundle(args.bundle)
    except BundleError as exc:
        print(f"PHASE 17 review bundle: REFUSED: {exc}")
        return 2

    pack = record["pack"]
    env = record["environment"]
    print(f"PHASE 17 review bundle: PASS: {pack['name']} {pack['version']} ({pack['family']})")
    print(f"  Vitrail={env['vitrail']} Metallum={env['metallum']} Backend={env['backend']} Device={env['device']}")
    print("  evidence.json, latest.log and screenshot.png are present and hash-consistent")
    print("  compatibility status remains unassigned until review")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
