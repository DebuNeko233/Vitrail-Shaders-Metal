# shadow-mipmap-contract

PHASE 9 shadow depth-mipmap smoke pack.

The shadow fragment writes a one-pixel alternating depth pattern into the real shadow depth attachment. Both `shadowtex0Mipmap` and `shadowtex1Mipmap` are requested. The composite pass compares level zero with explicit LOD 4 from both depth names:

- **GREEN**: at least one high-LOD sample differs from its level-zero sample, proving the generated chain is actually readable.
- **BLUE**: the pair is valid but the selected source texels happen to match. A mixture of GREEN and BLUE is expected after nearest reduction.
- **MAGENTA**: a sampled depth escaped the `[0, 1]` window and is a hard failure.

If mip generation fails or the sampler remains clamped to level zero, the diagnostic stays BLUE and the screenshot verifier fails because GREEN is absent.
