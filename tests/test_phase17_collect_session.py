#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODULE_PATH = HERE / "phase17_collect_session.py"
spec = importlib.util.spec_from_file_location("phase17_collect_session", MODULE_PATH)
mod = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(mod)

GOOD_LOG = """\
[10:00:00] [Render thread/INFO] (Vitrail) Vitrail 0.12.0-dev starting on Fabric 0.19.3, Minecraft 26.2
[10:00:00] [Render thread/INFO] (metallum) Metal device: Apple Fixture GPU
[10:00:00] [Render thread/INFO] (Minecraft) Using graphics backend Metal, using drivers: fixture
[10:00:01] [Worker-Main-1/INFO] (Vitrail) [pack] RealFixture 100 0 0 0
[10:00:02] [Render thread/INFO] (Vitrail) Drawing the solid chunk pass with gbuffers_terrain of RealFixture at render stage TERRAIN_SOLID, 2 uniforms and 1 samplers
[10:00:03] [Render thread/WARN] (Vitrail) A draw of the hand went back to the game's own shader because the load left no program for the hand piece.
[10:00:04] [Render thread/INFO] (Vitrail) Drawing RealFixture from the root for minecraft:overworld, at 1280x720, 1 full screen passes before the final
[10:00:05] [Render thread/INFO] (Minecraft) Stopping!
"""

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    pack = root / "pack.zip"
    log = root / "latest.log"
    shot = root / "shot.png"
    pack.write_bytes(b"exact pack bytes")
    log.write_text(GOOD_LOG, encoding="utf-8")
    shot.write_bytes(b"exact screenshot bytes")

    record = mod.collect(
        log=log,
        artifact=pack,
        family="fixture",
        name="Fixture Display",
        version="1.2.3",
        runtime_name="RealFixture",
        vitrail_head="v-head",
        metallum_head="m-head",
        screenshot=shot,
    )
    assert record["environment"]["backend"] == "Metal"
    assert record["environment"]["device"] == "Apple Fixture GPU"
    assert record["environment"]["minecraft"] == "26.2"
    assert record["evidence"]["loaded"] is True
    assert record["evidence"]["world_drawn"] is True
    assert record["evidence"]["clean_shutdown"] is True
    assert record["evidence"]["fatal_failure"] is False
    assert record["evidence"]["vitrail_draws"] == 2
    assert record["evidence"]["compatibility_reviewed"] is False
    assert record["evidence"]["compatibility_fallbacks"] == 0
    assert record["observations"]["raw_game_owned_fallbacks"] == 1
    assert len(record["pack"]["artifact_sha256"]) == 64
    assert len(record["observations"]["log_sha256"]) == 64
    assert len(record["observations"]["screenshot_sha256"]) == 64

    directory = root / "pack-dir"
    directory.mkdir()
    (directory / "shaders").mkdir()
    (directory / "shaders" / "final.fsh").write_text("fixture", encoding="utf-8")
    first = mod.hash_artifact(directory)
    second = mod.hash_artifact(directory)
    assert first == second and len(first) == 64

    non_metal = root / "opengl.log"
    non_metal.write_text(GOOD_LOG.replace("Using graphics backend Metal", "Using graphics backend OpenGL"), encoding="utf-8")
    try:
        mod.collect(
            log=non_metal,
            artifact=pack,
            family="fixture",
            name="Fixture Display",
            version="1.2.3",
            runtime_name="RealFixture",
            vitrail_head="v-head",
            metallum_head="m-head",
        )
    except mod.CollectionError as exc:
        assert "requires the Metal backend" in str(exc)
    else:
        raise AssertionError("non-Metal session must be refused")

    out = root / "collected.json"
    rc = mod.main([
        "--log", str(log),
        "--pack", str(pack),
        "--family", "fixture",
        "--name", "Fixture Display",
        "--version", "1.2.3",
        "--runtime-name", "RealFixture",
        "--vitrail-head", "v-head",
        "--metallum-head", "m-head",
        "--screenshot", str(shot),
        "--out", str(out),
    ])
    assert rc == 0
    saved = json.loads(out.read_text(encoding="utf-8"))
    assert saved["evidence"]["compatibility_reviewed"] is False

print("PHASE 17 session evidence collector contract: PASS")
