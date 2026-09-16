from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DIRECT = ROOT / "tests/fixtures/shaderpacks/final-direct-contract/shaders"
CHAIN = ROOT / "tests/fixtures/shaderpacks/final-chain-contract/shaders"
PACK_PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"
PACK_CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"


class FinalContract(unittest.TestCase):
    def test_direct_fixture_is_final_only_and_green(self):
        self.assertEqual({p.name for p in DIRECT.iterdir()}, {"final.vsh", "final.fsh"})
        fragment = (DIRECT / "final.fsh").read_text(encoding="utf-8")
        self.assertIn("gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0)", fragment)
        self.assertNotIn("sampler2D", fragment)

    def test_chain_fixture_requires_latest_colortex0_in_final(self):
        composite = (CHAIN / "composite.fsh").read_text(encoding="utf-8")
        final = (CHAIN / "final.fsh").read_text(encoding="utf-8")
        self.assertIn("/* DRAWBUFFERS:0 */", composite)
        self.assertIn("texcoord.x < 0.5", composite)
        self.assertIn("vec4(1.0, 0.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("uniform sampler2D colortex0", final)
        self.assertIn("texture2D(colortex0, texcoord)", final)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", final)
        self.assertIn("vec4(1.0, 1.0, 0.0, 1.0)", final)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", final)

    def test_production_final_is_the_game_target_not_a_pack_attachment(self):
        source = PACK_PASS.read_text(encoding="utf-8")
        self.assertIn("this.last = this.attachments.isEmpty();", source)
        self.assertIn("private static final GpuFormat SCREEN_FORMAT = GpuFormat.RGBA8_UNORM;", source)
        self.assertIn("ColorTargetState.WRITE_COLOR", source)
        self.assertIn("void drawFinal(CommandEncoder encoder, GpuTextureView into", source)
        self.assertIn("encoder.createRenderPass(this.label, into, Optional.empty())", source)
        self.assertIn("writes the game's target and has to be", source)
        self.assertIn("drawn through drawFinal", source)

    def test_pack_chain_routes_only_the_last_pass_to_main_view(self):
        source = PACK_CHAIN.read_text(encoding="utf-8")
        self.assertIn("if (pass == this.last)", source)
        self.assertIn("pass.drawFinal(encoder, ready.mainView(), this.targets", source)
        self.assertIn("the composites and the final once it is done", source)


if __name__ == "__main__":
    unittest.main()
