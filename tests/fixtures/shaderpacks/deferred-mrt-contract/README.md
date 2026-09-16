# deferred-mrt-contract

PHASE 10 Deferred MRT fixture. `deferred`, `deferred1` and `deferred2` each write both colortex0 and colortex1. The later passes must sample both previous halves before progressing the state machine. Final only presents colortex0 on the left and colortex1 on the right. Success is BLUE left + RED right; stale/missing/wrong-half MRT becomes MAGENTA.
