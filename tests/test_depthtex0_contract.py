"Lock PHASE 8 depthtex0 scene-depth routing without closing later depth checkpoints."
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/depthtex0-contract/shaders"
SAMPLERS = ROOT / "common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java"
PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"
DEPTH = ROOT / "common/src/main/java/dev/vitrail/render/PackDepth.java"
CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"


class Depthtex0ContractTest(unittest.TestCase):
    def test_fixture_samples_only_depthtex0_and_exposes_live_variation(self):
        composite_v = (FIXTURE / "composite.vsh").read_text(encoding="utf-8")
        composite_f = (FIXTURE / "composite.fsh").read_text(encoding="utf-8")
        final_v = (FIXTURE / "final.vsh").read_text(encoding="utf-8")
        final_f = (FIXTURE / "final.fsh").read_text(encoding="utf-8")

        self.assertIn("gl_Position = ftransform();", composite_v)
        self.assertIn("uniform sampler2D depthtex0;", composite_f)
        self.assertGreaterEqual(composite_f.count("texture2D(depthtex0"), 3)
        self.assertNotIn("depthtex1", composite_f)
        self.assertNotIn("depthtex2", composite_f)
        self.assertIn("variation > 0.0000001", composite_f)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite_f)
        self.assertIn("vec4(0.0, 1.0, 1.0, 1.0)", composite_f)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite_f)
        self.assertIn("const bool colortex1Clear = true;", composite_f)
        self.assertIn("/* DRAWBUFFERS:1 */", composite_f)
        self.assertIn("gl_Position = ftransform();", final_v)
        self.assertIn("uniform sampler2D colortex1;", final_f)
        self.assertEqual(
            {"composite.vsh", "composite.fsh", "final.vsh", "final.fsh"},
            {path.name for path in FIXTURE.iterdir()},
        )

    def test_depthtex0_is_live_depth_not_an_opaque_copy_alias(self):
        samplers = SAMPLERS.read_text(encoding="utf-8")
        pack_pass = PASS.read_text(encoding="utf-8")

        self.assertIn(
            'private static final Set<String> DEPTH = Set.of("depthtex0", "depthtex1", "depthtex2", "gdepthtex");',
            samplers,
        )
        start = samplers.index("public static boolean depthCopy(String name)")
        end = samplers.index("public static boolean preHandCopy(String name)")
        depth_copy = samplers[start:end]
        self.assertNotIn('"depthtex0"', depth_copy)
        self.assertIn('name.equals("depthtex1") || name.equals("depthtex2")', depth_copy)

        self.assertIn("if (SamplerPlan.depthCopy(sampler))", pack_pass)
        self.assertIn("return depthView == null ? targets.white() : depthView;", pack_pass)
        self.assertIn("case DEPTH -> depth(binding.sampler(), targets, depthView);", pack_pass)

    def test_composite_side_depthtex0_is_converted_scene_depth(self):
        depth = DEPTH.read_text(encoding="utf-8")
        chain = CHAIN.read_text(encoding="utf-8")

        self.assertIn("private static final GpuFormat FORMAT = GpuFormat.R32_FLOAT;", depth)
        self.assertIn("texture(InSampler, ofTexCoord).r", depth)
        self.assertIn("boolean takeScene(CommandEncoder encoder, GpuDevice device, GpuBuffer quad, GpuTextureView live,", depth)
        self.assertIn("this.sceneDepth = this.targets.depth().takeScene(", chain)
        self.assertIn("keepScene(device, ready.depthView(), ready.main().width, ready.main().height)", chain)
        self.assertIn(
            "drawRange(device, ready, deferredEnd(), this.programs.size(), this.targets.depth().scene(),",
            chain,
        )


if __name__ == "__main__":
    unittest.main()
