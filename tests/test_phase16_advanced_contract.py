from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/phase16-advanced-contract/shaders"
TARGET_PLAN = ROOT / "common/src/main/java/dev/vitrail/pack/target/TargetPlan.java"
PACK_PASS = ROOT / "common/src/main/java/dev/vitrail/render/PackPass.java"


def text(name: str) -> str:
    return (FIXTURE / name).read_text(encoding="utf-8")


class Phase16AdvancedContract(unittest.TestCase):
    def test_raw_volume_pair_locks_nearest_and_linear_depth_semantics(self):
        properties = text("shaders.properties")
        self.assertIn(
            "customTexture.phase16Nearest=phase16_nearest.bin TEXTURE_3D RGBA8 1 1 2 RGBA UNSIGNED_BYTE",
            properties,
        )
        self.assertIn(
            "customTexture.phase16Linear=phase16_linear.bin TEXTURE_3D RGBA8 1 1 2 RGBA UNSIGNED_BYTE",
            properties,
        )
        self.assertEqual(bytes([255, 0, 0, 255, 0, 0, 255, 255]), (FIXTURE / "phase16_nearest.bin").read_bytes())
        self.assertEqual((FIXTURE / "phase16_nearest.bin").read_bytes(), (FIXTURE / "phase16_linear.bin").read_bytes())
        self.assertIn('"blur":false', text("phase16_nearest.bin.mcmeta"))
        self.assertIn('"blur":true', text("phase16_linear.bin.mcmeta"))
        shader = text("composite.fsh")
        self.assertIn("uniform sampler3D phase16Nearest;", shader)
        self.assertIn("uniform sampler3D phase16Linear;", shader)
        self.assertIn("vec3(0.5, 0.5, 0.5)", shader)
        self.assertIn("nearestDepth.b > 0.90", shader)
        self.assertIn("abs(linearDepth.r - 0.5) < 0.12", shader)

    def test_custom_noise_is_pack_supplied_and_checked_one_period_apart(self):
        self.assertIn("texture.noise=noise.png", text("shaders.properties"))
        self.assertTrue((FIXTURE / "noise.png").is_file())
        shader = text("composite.fsh")
        self.assertIn("uniform sampler2D noisetex;", shader)
        self.assertIn("vec2(0.25, 0.25)", shader)
        self.assertIn("vec2(1.25, 0.25)", shader)
        self.assertIn("distance(noiseA, noiseB) < 0.02", shader)

    def test_non_identity_geometry_mrt_maps_physical_blend_directives_to_attachment_ranks(self):
        properties = text("shaders.properties")
        self.assertIn("blend.gbuffers_terrain_solid.colortex3=ONE ZERO", properties)
        self.assertIn("blend.gbuffers_terrain_solid.colortex1=ZERO ZERO", properties)
        self.assertNotIn("blend.composite", properties)

        write = text("gbuffers_terrain_solid.fsh")
        self.assertIn("/* DRAWBUFFERS:31 */", write)
        self.assertIn("gl_FragData[0] = source;", write)
        self.assertIn("gl_FragData[1] = source;", write)

        plan = TARGET_PLAN.read_text(encoding="utf-8")
        pack = PACK_PASS.read_text(encoding="utf-8")
        self.assertIn("int rank = writes.indexOf(index.getAsInt());", plan)
        self.assertIn("functions[rank] = BlendMode.parse(directive.value()).orElseThrow();", plan)
        self.assertIn("targets.blend(program, slot)", pack)

        judge = text("composite1.fsh")
        self.assertIn("uniform sampler2D colortex3;", judge)
        self.assertIn("uniform sampler2D colortex1;", judge)
        self.assertIn("distance(blended, vec3(0.75, 0.25, 0.5)) < 0.04", judge)
        self.assertIn("length(zeroed) < 0.04", judge)
        self.assertIn("untouched ? vec4(0.0, 0.0, 0.0, 1.0)", judge)
        self.assertFalse((FIXTURE / "composite2.fsh").exists())
        self.assertFalse((FIXTURE / "composite2.vsh").exists())

    def test_shadow_comparison_is_hardware_road_and_scene_independent(self):
        shader = text("composite1.fsh")
        self.assertIn("uniform sampler2DShadow shadowtex0;", shader)
        self.assertIn("vec3(0.5, 0.5, -1.0)", shader)
        self.assertIn("vec3(0.5, 0.5, 2.0)", shader)
        self.assertIn("compareLow > 0.95 && compareHigh < 0.05", shader)
        self.assertTrue((FIXTURE / "shadow.vsh").is_file())
        self.assertTrue((FIXTURE / "shadow.fsh").is_file())

    def test_final_image_has_four_independent_quarters_and_fixture_is_backend_neutral(self):
        judge = text("composite1.fsh")
        for edge in ("0.25", "0.50", "0.75"):
            self.assertIn(f"texcoord.x < {edge}", judge)
        all_text = "\n".join(path.read_text(encoding="utf-8", errors="ignore") for path in FIXTURE.iterdir() if path.is_file())
        self.assertNotIn("Metal", all_text)
        self.assertNotIn("MTL", all_text)


if __name__ == "__main__":
    unittest.main()
