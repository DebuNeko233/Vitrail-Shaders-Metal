# deferred-depth-contract

Developer-only PHASE 10 fixture for depth sampling across the ordered `deferred`, `deferred1`, `deferred2` family.

Every deferred pass samples `depthtex0`. For a deferred pass Vitrail binds the opaque-world depth snapshot, already converted into the pack's forward depth window. The fixture also carries one bit of local-depth variation through `colortex0`, so all three passes must agree on the same depth image:

1. `deferred` writes GREEN for locally constant valid depth, CYAN for local variation, MAGENTA for invalid depth.
2. `deferred1` reads that marker and samples `depthtex0` again. It writes BLUE/CYAN only when its depth classification agrees with `deferred`; disagreement or invalid depth is MAGENTA.
3. `deferred2` repeats the check. It writes RED for locally constant depth and YELLOW for local variation; any broken prior marker, invalid depth or disagreement is MAGENTA.
4. `final` only presents the latest `colortex0`; it does not manufacture the success colours and does not count as PHASE 12 acceptance.

Real-device acceptance requires both RED and YELLOW regions, low MAGENTA, all three deferred programs in the before-translucents cut, `depthtex0` recorded as a real world-depth sampler, Metal active and clean shutdown.
