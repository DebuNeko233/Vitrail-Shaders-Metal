# PHASE 14 Wide Resources

Developer-only smoke fixture for a fragment stage that actively reads more than sixteen sampled images.

`shaders.properties` gives seventeen distinct sampler names (`wide00` through `wide16`) the same one-pixel white image. `final.fsh` samples every one of those names and only emits GREEN when all seventeen reads return white; any missing, stale, aliased, or incorrectly indexed binding makes the observer MAGENTA.

The aliases intentionally share one physical image. The contract is descriptor width, not texture allocation count: the shader exposes seventeen separately named active sampled-image resources, and Vitrail's active-resource narrowing must keep all seventeen because every name is read.

Expected hardware result: nearly solid GREEN. MAGENTA is the failure signature.

Vitrail owns the shader-pack sampler names and custom-texture directives. The backend is not told about `wideNN` names or the threshold; Metallum independently chooses its generic wide-resource execution path from reflected active resources and Metal capabilities.
