"Lock PHASE 8 depthtex1, depthtex2 and pre-translucent routing as separate batched checkpoints."
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests/fixtures/shaderpacks"
SAMPLERS = ROOT / "common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java"
PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"
DEPTH = ROOT / "common/src/main/java/dev/vitrail/render/PackDepth.java"
CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"


class DepthBatchContractTest(unittest.TestCase):
    def read_fixture(self, name, program):
        fixture = FIXTURES / name / "shaders"
        vertex = (fixture / f"{program}.vsh").read_text(encoding="utf-8")
        fragment = (fixture / f"{program}.fsh").read_text(encoding="utf-8")
        final_v = (fixture / "final.vsh").read_text(encoding="utf-8")
        final_f = (fixture / "final.fsh").read_text(encoding="utf-8")
        self.assertEqual(
            {f"{program}.vsh", f"{program}.fsh", "final.vsh", "final.fsh"},
            {path.name for path in fixture.iterdir()},
        )
        self.assertIn("gl_Position = ftransform();", vertex)
        self.assertIn("gl_Position = ftransform();", final_v)
        self.assertIn("uniform sampler2D colortex1;", final_f)
        self.assertIn("const bool colortex1Clear = true;", fragment)
        self.assertIn("/* DRAWBUFFERS:1 */", fragment)
        self.assertIn("variation > 0.0000001", fragment)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", fragment)
        return fragment

    def test_depthtex1_fixture_isolated_and_visually_distinct(self):
        fragment = self.read_fixture("depthtex1-contract", "composite")
        self.assertIn("uniform sampler2D depthtex1;", fragment)
        self.assertGreaterEqual(fragment.count("texture2D(depthtex1"), 3)
        self.assertNotIn("depthtex0", fragment)
        self.assertNotIn("depthtex2", fragment)
        self.assertIn("vec4(1.0, 1.0, 0.0, 1.0)", fragment)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", fragment)

    def test_depthtex2_fixture_isolated_and_visually_distinct(self):
        fragment = self.read_fixture("depthtex2-contract", "composite")
        self.assertIn("uniform sampler2D depthtex2;", fragment)
        self.assertGreaterEqual(fragment.count("texture2D(depthtex2"), 3)
        self.assertNotIn("depthtex0", fragment)
        self.assertNotIn("depthtex1", fragment)
        self.assertIn("vec4(0.0, 1.0, 1.0, 1.0)", fragment)
        self.assertIn("vec4(1.0, 0.0, 0.0, 1.0)", fragment)

    def test_pre_translucent_fixture_runs_in_deferred_and_samples_depthtex0(self):
        fragment = self.read_fixture("pre-translucent-contract", "deferred")
        self.assertIn("uniform sampler2D depthtex0;", fragment)
        self.assertGreaterEqual(fragment.count("texture2D(depthtex0"), 3)
        self.assertNotIn("depthtex1", fragment)
        self.assertNotIn("depthtex2", fragment)
        self.assertIn("vec4(1.0, 1.0, 1.0, 1.0)", fragment)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", fragment)

    def test_depthtex1_and_depthtex2_are_opaque_copy_names_with_distinct_pre_hand_rule(self):
        samplers = SAMPLERS.read_text(encoding="utf-8")
        pack_pass = PASS.read_text(encoding="utf-8")

        self.assertIn(
            'private static final Set<String> DEPTH = Set.of("depthtex0", "depthtex1", "depthtex2", "gdepthtex");',
            samplers,
        )
        start = samplers.index("public static boolean depthCopy(String name)")
        end = samplers.index("public static boolean preHandCopy(String name)")
        depth_copy = samplers[start:end]
        self.assertIn('name.equals("depthtex1") || name.equals("depthtex2")', depth_copy)

        pre_start = samplers.index("public static boolean preHandCopy(String name)")
        pre_hand = samplers[pre_start:pre_start + 500]
        self.assertIn('return name.equals("depthtex2");', pre_hand)

        self.assertIn("if (SamplerPlan.depthCopy(sampler))", pack_pass)
        self.assertIn("if (SamplerPlan.preHandCopy(sampler))", pack_pass)
        self.assertIn("GpuTextureView preHand = targets.depth().preHand();", pack_pass)
        self.assertIn("GpuTextureView opaque = targets.depth().opaque();", pack_pass)
        self.assertIn("return depthView == null ? targets.white() : depthView;", pack_pass)

    def test_opaque_copy_is_taken_before_world_translucents(self):
        depth = DEPTH.read_text(encoding="utf-8")
        chain = CHAIN.read_text(encoding="utf-8")

        self.assertIn("private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;", depth)
        self.assertIn("boolean takeOpaque(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView live,", depth)
        self.assertIn("this.targets.depth().takeOpaque(device.createCommandEncoder(), device, this.quad,", chain)
        self.assertIn(
            "drawRange(device, ready, world, end, this.targets.depth().opaque(),",
            chain,
        )
        self.assertIn("Cut.BEFORE_TRANSLUCENTS", chain)

    def test_pre_translucent_callback_reaches_the_opaque_depth_cut(self):
        chain = CHAIN.read_text(encoding="utf-8")

        self.assertIn("public static void drawBeforeTranslucents()", chain)
        self.assertIn("chain.drawEarly(device);", chain)
        self.assertIn("this.targets.depth().takeOpaque(", chain)
        self.assertIn(
            "drawRange(device, ready, world, end, this.targets.depth().opaque(),",
            chain,
        )
        self.assertIn("Cut.BEFORE_TRANSLUCENTS", chain)


if __name__ == "__main__":
    unittest.main()
