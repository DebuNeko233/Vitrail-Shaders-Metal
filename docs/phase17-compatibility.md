# PHASE 17 real shader-pack compatibility evidence

PHASE 17 records compatibility for real shader packs without assigning a status from a pack name, family, static source scan, or CI fixture. A final status is derived only from a completed real-device evidence record for an exact pack artifact and exact Vitrail/Metallum heads.

The roadmap matrix starts with Photon, Complementary, BSL-family, Sildur-family, and MakeUp. `tests/fixtures/phase17/catalog.json` names those rows but intentionally contains no status. The pack files themselves are not redistributed by this repository.

## Status meanings

The classifier emits exactly one of these five public statuses:

- **Supported** - a real Vitrail pack path drew, the session shut down cleanly, compatibility observations were reviewed, the tested reference visual passed, and no blocking unsupported feature, meaningful unsupported feature, compatibility fallback, or visual regression was recorded.
- **Partially Supported** - a real Vitrail pack path drew, but one or more meaningful non-blocking unsupported features, compatibility fallbacks, or visual regressions remain.
- **Fallback** - the tested world rendered through compatibility/game-owned fallback paths and no Vitrail pack draw was observed.
- **Unsupported** - one or more required unsupported blockers prevent the intended pack route.
- **Broken** - the pack did not load or the test recorded a fatal runtime/chain failure.

An incomplete session is not silently turned into one of those statuses. Missing real-device execution, missing a clean shutdown, an unreviewed compatibility observation set, missing a world draw for a candidate `Supported` result, or missing the reference visual check causes the classifier to refuse the record. A fatal session or a pack that did not load can still be recorded as `Broken` from mechanical evidence.

## Mechanical session collection

`tests/phase17_collect_session.py` turns one exact real-device session into a draft evidence record. It hashes the tested shader-pack ZIP or directory, hashes the log and optional screenshot, confirms Metal and the device, reads the Minecraft version, records Vitrail draw markers, clean shutdown and high-confidence fatal markers, and preserves raw game-owned fallback observations.

Raw fallback observations are intentionally **not** copied into `compatibility_fallbacks`. Optional programs that Iris also leaves to the game are not compatibility failures. The collector therefore writes `compatibility_reviewed=false` and does not assign a pack status.

Example:

```sh
python3 tests/phase17_collect_session.py \
  --log latest.log \
  --pack pack.zip \
  --family photon \
  --name Photon \
  --version exact-tested-version \
  --runtime-name exact-name-in-vitrail-log \
  --vitrail-head exact-vitrail-head \
  --metallum-head exact-metallum-head \
  --screenshot screenshot.png \
  --out evidence.json
```

The device launcher is expected to fill those arguments. The operator should not need to hash files, grep logs, or edit JSON by hand.

## Classification

Copy `tests/fixtures/phase17/evidence-template.json` only to inspect the schema. The classifier lives at `tests/phase17_compatibility.py`:

```sh
python3 tests/phase17_compatibility.py evidence.json \
  --json result.json \
  --markdown result.md
```

`pack.artifact_sha256` identifies the exact tested material rather than trusting a display version. `environment` records Minecraft, Vitrail, Metallum, backend, and device. The evidence counters are compatibility findings, not raw log frequency.

CI runs the classifier and session-collector contracts. Those contracts exercise all five statuses, refusal paths, exact required matrix rows, deterministic artifact hashing, Metal/session parsing, raw-fallback separation, and JSON/Markdown output. Passing CI proves only that the evidence rules and collector are stable; it does **not** prove compatibility for any real shader pack.
