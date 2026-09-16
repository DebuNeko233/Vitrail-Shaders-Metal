# PHASE 14 Wide Resources

Developer-only smoke fixture for a fragment stage that actively reads thirty-three sampled images. That is deliberately beyond both Metal's sixteen direct sampler slots and a tempting artificial thirty-two-resource replacement ceiling.

`shaders.properties` gives thirty-three distinct sampler names (`wide00` through `wide32`) the same one-pixel white image. `final.fsh` samples every one of those names and only emits GREEN when all thirty-three reads return white; any missing, stale, aliased, or incorrectly indexed binding makes the observer MAGENTA.

The aliases intentionally share one physical image. The contract is descriptor width, not texture allocation count: the shader exposes thirty-three separately named active sampled-image resources, and Vitrail's active-resource narrowing must keep all thirty-three because every name is read.

Expected hardware result: nearly solid GREEN. MAGENTA is the failure signature.

Vitrail owns the shader-pack sampler names and custom-texture directives. The backend is not told about `wideNN` names or the threshold; Metallum independently chooses its generic wide-resource execution path from reflected active resources and Metal capabilities.
