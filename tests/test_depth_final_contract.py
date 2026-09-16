"Lock the final PHASE 8 pre-hand split and exact reversed-Z depth conversion contracts."
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PRE = ROOT / "tests/fixtures/shaderpacks/pre-hand-contract/shaders"
CONVERSION = ROOT / "tests/fixtures/shaderpacks/depth-conversion-contract/shaders"
SAMPLERS = ROOT / "common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java"
PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"
CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"
DEPTH = ROOT / "common/src/main/java/dev/vitrail/render/PackDepth.java"
CLIP = ROOT / "common/src/main/java/dev/vitrail/uniform/ClipSpace.java"


class DepthFinalContractTest(unittest.TestCase):
    def test_pre_hand_fixture_forces_a_real_hand_depth_split(self):
        hand_v = (PRE / "gbuffers_hand.vsh").read_text(encoding="utf-8")
        hand_f = (PRE / "gbuffers_hand.fsh").read_text(encoding="utf-8")
        composite = (PRE / "composite.fsh").read_text(encoding="utf-8")

        self.assertIn("gl_Position = ftransform();", hand_v)
        self.assertIn("gl_Position.z = -gl_Position.w;", hand_v)
        self.assertIn("attribute vec2 mc_midTexCoord;", hand_v)
        self.assertIn("attribute vec4 at_tangent;", hand_v)
        self.assertIn("texture2D(gtexture, texcoord)", hand_f)
        self.assertIn("handAbiOk < 0.50", hand_f)
        self.assertIn("uniform sampler2D depthtex1;", composite)
        self.assertIn("uniform sampler2D depthtex2;", composite)
        self.assertIn("float withHand = texture2D(depthtex1", composite)
        self.assertIn("float beforeHand = texture2D(depthtex2", composite)
        self.assertIn("withHand <= 0.005 && beforeHand > 0.01", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(0.0, 1.0, 1.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)

    def test_pre_hand_runtime_keeps_depthtex1_and_depthtex2_distinct(self):
        samplers = SAMPLERS.read_text(encoding="utf-8")
        pack_pass = PASS.read_text(encoding="utf-8")
        chain = CHAIN.read_text(encoding="utf-8")

        self.assertIn('return name.equals("depthtex1") || name.equals("depthtex2");', samplers)
        self.assertIn('return name.equals("depthtex2");', samplers)
        self.assertIn("GpuTextureView preHand = targets.depth().preHand();", pack_pass)
        self.assertIn("GpuTextureView opaque = targets.depth().opaque();", pack_pass)
        self.assertIn("public static void markPreHandDepth()", chain)
        self.assertIn("!HandDraw.draws()", chain)
        self.assertIn("chain.targets.depth().takePreHand(", chain)
        self.assertIn("this.targets.depth().takeOpaque(", chain)

    def test_conversion_fixture_hits_both_affine_endpoints(self):
        hand_v = (CONVERSION / "gbuffers_hand.vsh").read_text(encoding="utf-8")
        composite = (CONVERSION / "composite.fsh").read_text(encoding="utf-8")
        clip = CLIP.read_text(encoding="utf-8")
        depth = DEPTH.read_text(encoding="utf-8")

        self.assertIn("gl_Position.z = -gl_Position.w;", hand_v)
        self.assertIn("uniform sampler2D depthtex1;", composite)
        self.assertIn("depth <= 0.005", composite)
        self.assertIn("depth >= 0.995", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 1.0, 1.0, 1.0)", composite)
        self.assertIn(
            "REVERSED = new Vector4f(-0.5F, 0.5F, -1.0F, 1.0F);",
            clip,
        )
        self.assertIn("private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;", depth)
        self.assertIn("ofFragData0 = vec4(%s * texture(InSampler, ofTexCoord).r + %s);", depth)
        self.assertIn("ClipSpace.REVERSED.z, ClipSpace.REVERSED.w", depth)
        self.assertIn("getClampToEdge(FilterMode.NEAREST)", depth)

    def test_each_fixture_is_self_contained(self):
        expected = {
            "gbuffers_hand.vsh", "gbuffers_hand.fsh",
            "composite.vsh", "composite.fsh",
            "final.vsh", "final.fsh",
        }
        self.assertEqual(expected, {path.name for path in PRE.iterdir()})
        self.assertEqual(expected, {path.name for path in CONVERSION.iterdir()})


if __name__ == "__main__":
    unittest.main()
