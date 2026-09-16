from pathlib import Path
import sys
import tempfile

POSITIVE = "Temporal fold armed:"
POSITIVE_TAIL = "reprojected by the engine's motion vectors"
HARD_FAILURES = (
    "The motion vector pass did not compile",
    "Vitrail could not allocate the motion vector image",
    "Vitrail could not allocate the uniform buffer the motion vector pass writes",
    "The temporal fold did not compile",
    "Vitrail could not allocate the temporal fold's history",
)


def read_scale(path: Path) -> int:
    for line in path.read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip().lower() == "renderscale":
            return int(value.strip())
    raise AssertionError("vitrail/pack.txt carries no renderscale= line")


def verify(log: str, pack_file: Path, temporal_file: Path) -> list[str]:
    if temporal_file.read_text(encoding="utf-8").strip().lower() != "true":
        raise AssertionError("vitrail/temporal-fold is not true")
    scale = read_scale(pack_file)
    if not 25 <= scale < 100:
        raise AssertionError(f"render scale must be 25..99 for temporal acceptance, got {scale}")
    for failure in HARD_FAILURES:
        if failure in log:
            raise AssertionError(f"hard motion/temporal failure present: {failure}")
    armed = [line for line in log.splitlines() if POSITIVE in line and POSITIVE_TAIL in line]
    if not armed:
        raise AssertionError("no successful temporal draw consuming engine motion vectors was logged")
    if "Stopping!" not in log:
        raise AssertionError("client did not reach clean Stopping!")
    return [
        f"PHASE 16 temporal setting: PASS true",
        f"PHASE 16 render scale: PASS {scale}%",
        "PHASE 16 motion-vector consumption: PASS temporal draw consumed a drawn vector image",
        "PHASE 16 temporal accumulation execution: PASS",
        "PHASE 16 clean shutdown: PASS",
    ]


def self_test() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        pack = root / "pack.txt"
        temporal = root / "temporal-fold"
        pack.write_text("pack=terrain-contract\nenabled=true\nrenderscale=75\n", encoding="utf-8")
        temporal.write_text("true\n", encoding="utf-8")
        good = "[Render thread/INFO] Temporal fold armed: 1600x900 folded from a 1200x675 world, 0.1 of each new frame, reprojected by the engine's motion vectors\nStopping!\n"
        verify(good, pack, temporal)
        warning_then_good = "Temporal fold asked for but the engine wrote no motion vectors this frame\n" + good
        verify(warning_then_good, pack, temporal)
        for failure in HARD_FAILURES:
            try:
                verify(failure + "\n" + good, pack, temporal)
            except AssertionError:
                pass
            else:
                raise AssertionError(f"hard failure was accepted: {failure}")
        temporal.write_text("false\n", encoding="utf-8")
        try:
            verify(good, pack, temporal)
        except AssertionError:
            pass
        else:
            raise AssertionError("disabled temporal setting was accepted")
        temporal.write_text("true\n", encoding="utf-8")
        pack.write_text("renderscale=100\n", encoding="utf-8")
        try:
            verify(good, pack, temporal)
        except AssertionError:
            pass
        else:
            raise AssertionError("100% render scale was accepted")
    print("PHASE 16 motion/temporal log verifier self-test: PASS")


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
        return
    if len(sys.argv) != 4:
        raise SystemExit("Usage: python3 tests/verify_phase16_temporal_log.py <latest.log> <vitrail/pack.txt> <vitrail/temporal-fold>")
    log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    for line in verify(log, Path(sys.argv[2]), Path(sys.argv[3])):
        print(line)


if __name__ == "__main__":
    main()
