#!/usr/bin/env python3
"""Collect mechanical PHASE 17 evidence from one real-device Minecraft session.

This tool deliberately does not decide whether raw fallback warnings are compatible
with the Iris reference. It hashes the exact pack artifact, records Metal/device and
draw/shutdown facts, and leaves compatibility_reviewed=false for later adjudication.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Iterable

SCHEMA = "vitrail.phase17.compatibility.v1"

# These patterns intentionally key off the message text rather than a logger-name
# decoration. Production Fabric logs do not necessarily print a literal "(Vitrail)"
# prefix even though isolated fixtures historically did.
#
# Every "the frame stopped drawing <something>" is this engine giving up on part of a frame after a
# runtime error, and each one leaves the image unlike the one the pack asked for. A dead shadow
# stage is the plainest case: every shadowtex lookup answers with the far plane for the rest of the
# session, so the whole scene loses its shadows and reads as the pack having fallen back. Matching
# only the pack-wide form missed exactly that, and the `60ff5610` run, whose shadow stage died on a
# Sodium render-list overflow, was collected as a session with no fatal failure in it.
HIGH_CONFIDENCE_FATAL = (
    re.compile(r"Vitrail stopped drawing .+? after an error", re.IGNORECASE),
    re.compile(r"Failed to compile shader vitrail:pack/", re.IGNORECASE),
    re.compile(r"A fatal error has been detected by the Java Runtime Environment", re.IGNORECASE),
)

RAW_FALLBACK_PATTERNS = (
    re.compile(r"went back to the game's own shader", re.IGNORECASE),
    re.compile(r"(?:the game keeps its own shader|keeps? the game's own shader)", re.IGNORECASE),
)


class CollectionError(ValueError):
    pass


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def hash_artifact(path: Path) -> str:
    if path.is_file():
        return _sha256_file(path)
    if not path.is_dir():
        raise CollectionError(f"pack artifact does not exist: {path}")

    digest = hashlib.sha256()
    digest.update(b"vitrail-phase17-directory-v1\0")
    files = sorted((entry for entry in path.rglob("*") if entry.is_file()), key=lambda p: p.relative_to(path).as_posix())
    if not files:
        raise CollectionError("pack directory contains no files")
    for entry in files:
        if entry.is_symlink():
            raise CollectionError(f"pack directory contains a symlink: {entry.relative_to(path)}")
        relative = entry.relative_to(path).as_posix().encode("utf-8")
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        size = entry.stat().st_size
        digest.update(size.to_bytes(8, "big"))
        with entry.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    return digest.hexdigest()


def _samples(lines: Iterable[str], patterns: tuple[re.Pattern[str], ...], limit: int = 12) -> list[str]:
    found = []
    for line in lines:
        if any(pattern.search(line) for pattern in patterns):
            found.append(line.strip())
            if len(found) == limit:
                break
    return found


def _pack_markers(lines: list[str], runtime_name: str) -> list[str]:
    needle = runtime_name.lower()
    markers = []
    for line in lines:
        low = line.lower()
        if needle in low and (
            "[pack]" in low
            or "read " in low and " programs of " in low
            or "drawing " in low
            or " lays out " in low
        ):
            markers.append(line.strip())
            if len(markers) == 12:
                break
    return markers


def collect(
    *,
    log: Path,
    artifact: Path,
    family: str,
    name: str,
    version: str,
    runtime_name: str,
    vitrail_head: str,
    metallum_head: str,
    screenshot: Path | None = None,
) -> dict:
    try:
        text = log.read_text(encoding="utf-8", errors="replace")
    except OSError as exc:
        raise CollectionError(f"cannot read log: {exc}") from exc
    lines = text.splitlines()

    backend_match = re.search(r"Using graphics backend ([^,\r\n]+)", text)
    backend = backend_match.group(1).strip() if backend_match else "unknown"
    if backend != "Metal":
        raise CollectionError(f"PHASE 17 requires the Metal backend, observed {backend!r}")

    device_match = re.search(r"(?:\(metallum\)\s*)?Metal device: ([^\r\n]+)", text, re.IGNORECASE)
    if not device_match:
        device_match = re.search(r"Using graphics device: ([^\r\n]+)", text)
    if not device_match:
        raise CollectionError("Metal device line was not found")
    device = device_match.group(1).strip()

    minecraft_match = re.search(r"Vitrail [^,\r\n]+ starting on [^,\r\n]+, Minecraft ([^\s\r\n]+)", text)
    if not minecraft_match:
        raise CollectionError("Minecraft version could not be read from the Vitrail startup line")
    minecraft = minecraft_match.group(1)

    runtime_lower = runtime_name.lower()
    pack_lines = [line for line in lines if runtime_lower in line.lower()]
    loaded = any(
        "[pack]" in line.lower()
        or " programs of " in line.lower()
        or " lays out " in line.lower()
        or "drawing " in line.lower()
        for line in pack_lines
    )

    draw_patterns = (
        re.compile(rf"Drawing .* of {re.escape(runtime_name)} at render stage", re.IGNORECASE),
        re.compile(rf"Drawing {re.escape(runtime_name)} from the root for minecraft:", re.IGNORECASE),
    )
    draw_lines = [line.strip() for line in lines if any(pattern.search(line) for pattern in draw_patterns)]
    world_drawn = bool(draw_lines)

    fatal_samples = _samples(lines, HIGH_CONFIDENCE_FATAL)
    fallback_samples = _samples(lines, RAW_FALLBACK_PATTERNS)

    screenshot_hash = None
    if screenshot is not None:
        if not screenshot.is_file():
            raise CollectionError(f"screenshot does not exist: {screenshot}")
        screenshot_hash = _sha256_file(screenshot)

    return {
        "schema": SCHEMA,
        "pack": {
            "family": family,
            "name": name,
            "version": version,
            "artifact_sha256": hash_artifact(artifact),
        },
        "environment": {
            "minecraft": minecraft,
            "vitrail": vitrail_head,
            "metallum": metallum_head,
            "backend": backend,
            "device": device,
        },
        "evidence": {
            "attempted": True,
            "loaded": loaded,
            "world_drawn": world_drawn,
            "clean_shutdown": "Stopping!" in text,
            "fatal_failure": bool(fatal_samples),
            "compatibility_reviewed": False,
            "blocking_unsupported": 0,
            "meaningful_unsupported_features": 0,
            "vitrail_draws": len(draw_lines),
            "compatibility_fallbacks": 0,
            "visual_reference_checked": False,
            "visual_reference_ok": False,
            "visual_regressions": 0,
        },
        "observations": {
            "runtime_name": runtime_name,
            "pack_markers": _pack_markers(lines, runtime_name),
            "draw_markers": draw_lines[:12],
            "raw_game_owned_fallbacks": len(fallback_samples),
            "raw_fallback_samples": fallback_samples,
            "fatal_samples": fatal_samples,
            "log_sha256": _sha256_file(log),
            "screenshot_sha256": screenshot_hash,
        },
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--pack", type=Path, required=True, help="exact tested shader-pack ZIP or directory")
    parser.add_argument("--family", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--runtime-name", help="name Vitrail logs for the selected pack; defaults to --name")
    parser.add_argument("--vitrail-head", required=True)
    parser.add_argument("--metallum-head", required=True)
    parser.add_argument("--screenshot", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)

    try:
        record = collect(
            log=args.log,
            artifact=args.pack,
            family=args.family,
            name=args.name,
            version=args.version,
            runtime_name=args.runtime_name or args.name,
            vitrail_head=args.vitrail_head,
            metallum_head=args.metallum_head,
            screenshot=args.screenshot,
        )
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    except (OSError, CollectionError) as exc:
        print(f"PHASE 17 evidence collection: REFUSED: {exc}", file=sys.stderr)
        return 2

    ev = record["evidence"]
    obs = record["observations"]
    print(
        "PHASE 17 evidence collected: "
        f"loaded={str(ev['loaded']).lower()} worldDrawn={str(ev['world_drawn']).lower()} "
        f"vitrailDraws={ev['vitrail_draws']} cleanShutdown={str(ev['clean_shutdown']).lower()} "
        f"fatalFailure={str(ev['fatal_failure']).lower()} "
        f"rawFallbackObservations={obs['raw_game_owned_fallbacks']}"
    )
    print("PHASE 17 compatibility review remains pending; no pack status was assigned.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
