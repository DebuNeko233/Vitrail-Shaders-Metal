# PHASE 8 pre-translucent acceptance contract

This fixture puts its diagnostic in `deferred`, not `composite`. The deferred pass samples only `depthtex0` while Vitrail is in the cut before world translucents, writes white where neighboring opaque-depth samples vary, green where valid samples are constant, and magenta for invalid values. `final` displays only isolated `colortex1`.

The real-device gate proves the pre-translucent timing boundary carries a live opaque-world depth image. It does not validate pre-hand depth or the exact numerical reversed-Z conversion.
