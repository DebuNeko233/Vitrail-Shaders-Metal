# deferred-composite-contract

Developer-only PHASE 10A fixture for the ordered full-screen chain and its colour-target ping-pong.

The same `colortex0` is intentionally written three times:

1. `deferred` writes RED.
2. `composite` must read that RED from the previous half and writes GREEN; otherwise it writes MAGENTA.
3. `composite1` must read that GREEN from the next half and writes BLUE; otherwise it writes MAGENTA.
4. `final` does not manufacture a success colour. It only samples `colortex0`, so a correct chain reaches the screen as BLUE.

This makes pass order and target-half selection observable independently of scene geometry. A stale read,
an in-place read/write alias, a missing flip, or a final pass sampling the wrong half produces RED, GREEN,
MAGENTA, or the clear colour instead of BLUE.

Real-device acceptance additionally requires the Vitrail log to report the expected write sides
`deferred -> ALT`, `composite -> MAIN`, `composite1 -> ALT`, both composite samplers reaching a real
colour target, Metal active, and a clean `Stopping!`.
