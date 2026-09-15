# deferred-contract

Developer-only PHASE 10 fixture for the ordered `deferred`, `deferred1`, `deferred2` family and its colour-target ping-pong.

The same `colortex0` is intentionally written three times inside the deferred stage:

1. `deferred` writes RED.
2. `deferred1` must read RED from the previous half and writes GREEN; otherwise it writes MAGENTA.
3. `deferred2` must read GREEN from the next half and writes BLUE; otherwise it writes MAGENTA.
4. `final` is only an observation window. It samples `colortex0` and does not manufacture BLUE, so it does not count as PHASE 12 acceptance.

A stale read, in-place read/write alias, missing flip, or wrong deferred-family order therefore produces RED, GREEN, MAGENTA, or clear instead of BLUE.

Real-device acceptance additionally requires Vitrail to report `deferred -> ALT`, `deferred1 -> MAIN`, `deferred2 -> ALT`, real `colortex0` sampling, Metal active, and clean shutdown.
