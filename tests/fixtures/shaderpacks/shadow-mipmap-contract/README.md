# shadow-mipmap-contract

PHASE 9 shadow depth-mipmap smoke pack.

The shadow fragment writes a one-pixel alternating depth pattern into the real shadow depth attachment. Both `shadowtex0Mipmap` and `shadowtex1Mipmap` are requested. The composite pass compares level zero with explicit LOD 4 from both depth names:

- **GREEN**: both `shadowtex0` and `shadowtex1` have a high-LOD sample that differs from their own level-zero sample, proving both generated chains are actually readable.
- **BLUE**: both pairs are valid and both selected high-LOD texels happen to match their level-zero texels. A mixture of GREEN and BLUE is expected after nearest reduction.
- **MAGENTA**: a sampled depth escaped the `[0, 1]` window, or only one of the two shadow depth names shows mip reduction. Either is a hard failure.

If mip generation fails or both samplers remain clamped to level zero, the diagnostic stays BLUE and the screenshot verifier fails because GREEN is absent. If only one chain works, the terrain becomes MAGENTA and fails independently.
