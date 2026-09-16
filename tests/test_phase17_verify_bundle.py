#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODULE_PATH = HERE / "phase17_verify_bundle.py"
spec = importlib.util.spec_from_file_location("phase17_verify_bundle", MODULE_PATH)
mod = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(mod)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def evidence(log: bytes, screenshot: bytes) -> dict:
    return {
        "schema": "vitrail.phase17.compatibility.v1",
        "pack": {
            "family": "fixture",
            "name": "Fixture Pack",
            "version": "1.0",
            "artifact_sha256": "a" * 64,
        },
        "environment": {
            "minecraft": "26.2",
            "vitrail": "v-head",
            "metallum": "m-head",
            "backend": "Metal",
            "device": "Apple Fixture GPU",
        },
        "evidence": {
            "attempted": True,
            "loaded": True,
            "world_drawn": True,
            "clean_shutdown": True,
            "fatal_failure": False,
            "compatibility_reviewed": False,
            "blocking_unsupported": 0,
            "meaningful_unsupported_features": 0,
            "vitrail_draws": 2,
            "compatibility_fallbacks": 0,
            "visual_reference_checked": False,
            "visual_reference_ok": False,
            "visual_regressions": 0,
        },
        "observations": {
            "log_sha256": sha256(log),
            "screenshot_sha256": sha256(screenshot),
        },
    }


def write_bundle(path: Path, record: dict, log: bytes, screenshot: bytes, *, extra: tuple[str, bytes] | None = None) -> None:
    root = "fixture-20260916-120000"
    payloads = {
        "evidence.json": (json.dumps(record, indent=2) + "\n").encode("utf-8"),
        "latest.log": log,
        "screenshot.png": screenshot,
    }
    if extra is not None:
        payloads[extra[0]] = extra[1]
    with tarfile.open(path, mode="w:gz") as archive:
        for name, data in payloads.items():
            info = tarfile.TarInfo(f"{root}/{name}")
            info.size = len(data)
            archive.addfile(info, io.BytesIO(data))


def expect_refused(bundle: Path, fragment: str) -> None:
    try:
        mod.verify_bundle(bundle)
    except mod.BundleError as exc:
        assert fragment in str(exc), str(exc)
    else:
        raise AssertionError("invalid review bundle must be refused")


with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    log = b"fixture log\nStopping!\n"
    screenshot = b"fixture png bytes"
    record = evidence(log, screenshot)

    good = root / "good.tar.gz"
    write_bundle(good, record, log, screenshot)
    verified = mod.verify_bundle(good)
    assert verified["pack"]["name"] == "Fixture Pack"
    assert verified["evidence"]["compatibility_reviewed"] is False
    assert mod.main([str(good)]) == 0

    altered_log = root / "altered-log.tar.gz"
    write_bundle(altered_log, record, log + b"tampered", screenshot)
    expect_refused(altered_log, "latest.log SHA-256 mismatch")

    leaked_pack = root / "leaked-pack.tar.gz"
    write_bundle(leaked_pack, record, log, screenshot, extra=("shaderpack.zip", b"must not be bundled"))
    expect_refused(leaked_pack, "unexpected shaderpack.zip")

    traversal = root / "traversal.tar.gz"
    with tarfile.open(traversal, mode="w:gz") as archive:
        data = b"bad"
        info = tarfile.TarInfo("../evidence.json")
        info.size = len(data)
        archive.addfile(info, io.BytesIO(data))
    expect_refused(traversal, "unsafe bundle member path")

print("PHASE 17 review bundle verifier contract: PASS")
