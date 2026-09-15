"""Lock the final PHASE 9 shadow depth-mipmap acceptance contract."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/shadow-mipmap-contract/shaders"
SHADOW_TARGETS = ROOT / "common/src/main/java/dev/vitrail/render/ShadowTargets.java"
GPU_FORMATS = ROOT / "common/src/main/java/dev/vitrail/render/GpuFormats.java"
MIPMAP_REDUCTION = ROOT / "common/src/main/java/dev/vitrail/render/MipmapReduction.java"
METAL_MIXIN = ROOT / "common/src/main/java/dev/vitrail/mixin/metallum/MetalCommandEncoderMixin.java"
METAL_BRIDGE = ROOT / "common/src/main/java/dev/vitrail/mixin/metallum/MetallumDepthMipmapBridge.java"
PACK_DIRECTIVES = ROOT / "common/src/main/java/dev/vitrail/pack/target/PackDirectives.java"


def text(path):
    return path.read_text(encoding="utf-8")


def compact(path):
    return re.sub(r"\s+", " ", text(path))


class ShadowMipmapContractTest(unittest.TestCase):
    def test_fixture_requires_both_shadow_depth_chains_and_explicit_high_lod(self):
        self.assertEqual(
            {p.name for p in FIXTURE.iterdir()},
            {"shadow.vsh", "shadow.fsh", "composite.vsh", "composite.fsh", "final.vsh", "final.fsh"},
        )
        shadow = text(FIXTURE / "shadow.fsh")
        composite = text(FIXTURE / "composite.fsh")
        self.assertIn("gl_FragDepth = mix(0.20, 0.80, parity);", shadow)
        self.assertIn("const bool shadowtex0Mipmap = true;", composite)
        self.assertIn("const bool shadowtex1Mipmap = true;", composite)
        self.assertIn("texture2DLod(shadowtex0, texcoord, 4.0)", composite)
        self.assertIn("texture2DLod(shadowtex1, texcoord, 4.0)", composite)
        self.assertIn("bool reduced0 = abs(base0 - mip0) > 0.05;", composite)
        self.assertIn("bool reduced1 = abs(base1 - mip1) > 0.05;", composite)
        self.assertIn("!valid || reduced0 != reduced1", composite)
        self.assertIn("reduced0 && reduced1", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)

    def test_directives_keep_shadowtex0_and_shadowtex1_mipmap_policy_separate(self):
        directives = compact(PACK_DIRECTIVES)
        self.assertIn('case "generateShadowMipmap" -> asBool(directive, value -> { this.depthMipmap[0] = value; this.depthMipmap[1] = value;', directives)
        self.assertIn('case "shadowtexMipmap", "shadowtex0Mipmap" -> asBool(directive, value -> this.depthMipmap[0] = value);', directives)
        self.assertIn('case "shadowtex1Mipmap" -> asBool(directive, value -> this.depthMipmap[1] = value);', directives)

    def test_shadow_pair_allocates_renderable_levels_and_unlocks_lod_only_after_success(self):
        targets = compact(SHADOW_TARGETS)
        self.assertIn("GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT", targets)
        self.assertIn('createTexture(() -> "Vitrail shadowtex1", USAGE, this.depth.getFormat()', targets)
        self.assertIn("this.chainWritten[0] = MipmapReduction.generate(encoder, this.depth);", targets)
        self.assertIn("&& MipmapReduction.generate(encoder, this.noTranslucents);", targets)
        self.assertIn("return this.chainWritten[withoutTranslucents && this.copied ? 1 : 0];", targets)

        formats = compact(GPU_FORMATS)
        self.assertIn("feature(format, VK10.VK_FORMAT_FEATURE_BLIT_SRC_BIT, true)", formats)
        self.assertIn("feature(format, VK10.VK_FORMAT_FEATURE_BLIT_DST_BIT, true)", formats)

    def test_backend_capability_failure_still_clamps_shadow_sampling_to_base(self):
        reduction = compact(MIPMAP_REDUCTION)
        self.assertIn("backend instanceof MipmapCommands commands && commands.vitrail$generateMipmaps(texture)", reduction)

        mixin = compact(METAL_MIXIN)
        self.assertIn("return generateMipmaps(texture) || MetallumDepthMipmapBridge.generate(this, texture);", mixin)

        bridge = compact(METAL_BRIDGE)
        self.assertIn('private static final String CLASS_NAME = "com.metallum.render.MetalDepthMipmapBridge";', bridge)
        self.assertIn('bridge.getMethod("generate", Object.class, GpuTexture.class);', bridge)


if __name__ == "__main__":
    unittest.main()
