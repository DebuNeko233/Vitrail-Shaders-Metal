# PHASE 15 Compute / Storage

Developer-only acceptance fixture for the shader-pack compute/storage path.

Two compute programs hang off `composite` and run in Iris order immediately before that render pass. `composite.csh` writes a named SSBO and a one-pixel custom storage image. `composite_a.csh` must observe both writes, using an SSBO load plus `imageLoad`, and then writes GREEN to the same image with `imageStore`; any stale or incorrectly bound resource becomes MAGENTA instead. The following `composite.fsh` samples the image alias and writes the result into `colortex0`, and `final.fsh` presents that diagnostic.

The fixture deliberately dispatches one 1x1x1 workgroup so the verdict is about resource binding and ordering rather than coverage or atomics. It contains no Metal-specific names or binding indices beyond the GLSL bindings owned by the shader pack.

Expected hardware result: nearly solid GREEN. MAGENTA means the compute chain, SSBO, storage image, `imageLoad`/`imageStore`, or compute-to-render visibility failed.
