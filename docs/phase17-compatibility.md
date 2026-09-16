# PHASE 17 real shader-pack compatibility evidence

PHASE 17 records compatibility for real shader packs without assigning a status from a pack name, family, static source scan, or CI fixture. A final status is derived only from a completed real-device evidence record for an exact pack artifact and exact Vitrail/Metallum heads.

The roadmap matrix starts with Photon, Complementary, BSL-family, Sildur-family, and MakeUp. `tests/fixtures/phase17/catalog.json` names those rows but intentionally contains no status. The pack files themselves are not redistributed by this repository.

## Status meanings

The classifier emits exactly one of these five public statuses:

- **Supported** — a real Vitrail pack path drew, the session shut down cleanly, the tested reference visual passed, and no blocking unsupported feature, meaningful unsupported feature, compatibility fallback, or visual regression was recorded.
- **Partially Supported** — a real Vitrail pack path drew, but one or more meaningful non-blocking unsupported features, compatibility fallbacks, or visual regressions remain.
- **Fallback** — the tested world rendered through compatibility/game-owned fallback paths and no Vitrail pack draw was observed.
- **Unsupported** — one or more required unsupported blockers prevent the intended pack route.
- **Broken** — the pack did not load or the test recorded a fatal runtime/chain failure.

An incomplete session is not silently turned into one of those statuses. Missing real-device execution, missing a clean shutdown, missing a world draw for a candidate `Supported` result, or missing the reference visual check causes the classifier to refuse the record.

## Evidence record

Copy `tests/fixtures/phase17/evidence-template.json` for the shape only. The eventual device launcher is expected to fill the mechanical fields from the exact local pack artifact and `latest.log`; the operator should only be asked for evidence that cannot be inferred, such as the final visual comparison.

`pack.artifact_sha256` identifies the exact tested ZIP/directory material rather than trusting a display version. `environment` records Minecraft, Vitrail, Metallum, backend, and device. The `evidence` counters are compatibility evidence, not raw log frequency: for example, a game-owned draw that is also normal under the Iris reference is not a `compatibility_fallback`.

The classifier lives at `tests/phase17_compatibility.py`:

```sh
python3 tests/phase17_compatibility.py evidence.json \
  --json result.json \
  --markdown result.md
```

CI runs `tests/test_phase17_compatibility_contract.py`. That contract exercises all five statuses, refusal paths, exact required matrix rows, and both JSON and Markdown output. Passing CI proves only that the evidence rules are stable; it does **not** prove compatibility for any real shader pack.
