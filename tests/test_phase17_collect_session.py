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

# Match the production Fabric log shape: Vitrail and Metallum messages do not
# necessarily carry a literal logger-name decoration such as "(Vitrail)".
GOOD_LOG = """\
[10:00:00] [Render thread/INFO]: Vitrail 0.12.0-dev starting on Fabric 0.19.3, Minecraft 26.2
[10:00:00] [Render thread/INFO]: Metal device: Apple Fixture GPU
[10:00:00] [Render thread/INFO]: Using graphics backend Metal, using drivers: fixture
[10:00:01] [Worker-Main-1/INFO]: [pack] RealFixture 100 0 0 0
[10:00:02] [Render thread/INFO]: Drawing the solid chunk pass with gbuffers_terrain of RealFixture at render stage TERRAIN_SOLID, 2 uniforms and 1 samplers
[10:00:03] [Render thread/WARN]: A draw of the hand went back to the game's own shader because the load left no program for the hand piece.
[10:00:04] [Render thread/INFO]: Drawing RealFixture from the root for minecraft:overworld, at 1280x720, 1 full screen passes before the final
[10:00:05] [Render thread/INFO]: Stopping!
"""

BROKEN_LOG = """\
[10:00:00] [Render thread/INFO]: Vitrail 0.12.0-dev starting on Fabric 0.19.3, Minecraft 26.2
[10:00:00] [Render thread/INFO]: Metal device: Apple Fixture GPU
[10:00:00] [Render thread/INFO]: Using graphics backend Metal, using drivers: fixture
[10:00:01] [Worker-Main-1/INFO]: [pack] BrokenFixture 100 0 0 0
[10:00:02] [Render thread/ERROR]: Vitrail stopped drawing this pack after an error
java.lang.IllegalStateException: Failed to compile shader vitrail:pack/3/world0/prepare/vertex
Caused by: com.mojang.blaze3d.shaders.ShaderCompileException: Couldn't parse GLSL: syntax error
[10:00:03] [Render thread/INFO]: Stopping!
"""

# One stage giving up while the pack itself keeps drawing, loaded and shutting down cleanly. This is
# the shape the `60ff5610` real-device run had, and the message is that run's own.
STAGE_FAILURE_LOG = """\
[10:00:00] [Render thread/INFO]: Vitrail 0.12.0-dev starting on Fabric 0.19.3, Minecraft 26.2
[10:00:00] [Render thread/INFO]: Metal device: Apple Fixture GPU
[10:00:00] [Render thread/INFO]: Using graphics backend Metal, using drivers: fixture
[10:00:01] [Worker-Main-1/INFO]: [pack] StageFixture 100 0 0 0
[10:00:02] [Render thread/INFO]: Drawing the solid chunk pass with gbuffers_terrain of StageFixture at render stage TERRAIN_SOLID, 2 uniforms and 1 samplers
[10:00:03] [Render thread/INFO]: Drawing StageFixture from the root for minecraft:overworld, at 1280x720, 1 full screen passes before the final
[10:00:04] [Render thread/ERROR]: Vitrail stopped drawing the shadow map after an error in the stage, so every shadowtex lookup of the pack reads the far plane
java.lang.ArrayIndexOutOfBoundsException: Render list is full
[10:00:05] [Render thread/INFO]: Stopping!
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

    broken = root / "broken.log"
    broken.write_text(BROKEN_LOG, encoding="utf-8")
    broken_record = mod.collect(
        log=broken,
        artifact=pack,
        family="fixture",
        name="Broken Fixture",
        version="1.2.3",
        runtime_name="BrokenFixture",
        vitrail_head="v-head",
        metallum_head="m-head",
    )
    assert broken_record["evidence"]["loaded"] is True
    assert broken_record["evidence"]["world_drawn"] is False
    assert broken_record["evidence"]["fatal_failure"] is True
    assert broken_record["evidence"]["clean_shutdown"] is True
    assert len(broken_record["observations"]["fatal_samples"]) == 2

    stage = root / "stage.log"
    stage.write_text(STAGE_FAILURE_LOG, encoding="utf-8")
    stage_record = mod.collect(
        log=stage,
        artifact=pack,
        family="fixture",
        name="Stage Fixture",
        version="1.2.3",
        runtime_name="StageFixture",
        vitrail_head="v-head",
        metallum_head="m-head",
    )
    # Everything a session is normally judged by is healthy here: the pack loaded, the world drew, the
    # client shut down cleanly, and no pack-wide failure was logged. The one stage that gave up is
    # still fatal, because every shadowtex lookup answers with the far plane for the rest of the
    # session, so the image is not the one the pack asked for. Matching only the pack-wide message
    # collected this as a clean session.
    assert stage_record["evidence"]["loaded"] is True
    assert stage_record["evidence"]["world_drawn"] is True
    assert stage_record["evidence"]["clean_shutdown"] is True
    assert stage_record["evidence"]["fatal_failure"] is True
    assert len(stage_record["observations"]["fatal_samples"]) == 1

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
