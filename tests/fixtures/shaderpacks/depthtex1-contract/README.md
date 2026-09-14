# PHASE 8 depthtex1 acceptance contract

This fixture isolates the post-world `depthtex1` sampler. `composite` samples only `depthtex1`, writes yellow where neighboring opaque-depth samples vary, blue where valid samples are constant, and magenta for values outside the pack depth window. `final` displays only isolated `colortex1`.

The real-device gate proves that the `depthtex1` name reaches a live converted opaque-depth image on Metal. It does not prove the later pre-hand distinction, and it does not validate exact numerical depth conversion.
