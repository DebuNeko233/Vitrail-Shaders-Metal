from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/wide-resources-contract/shaders"
PACK_TEXTURES = ROOT / "common/src/main/java/dev/vitrail/pack/texture/PackTextures.java"
PACK_IMAGES = ROOT / "common/src/main/java/dev/vitrail/render/PackImages.java"
SAMPLER_REACH = ROOT / "common/src/main/java/dev/vitrail/render/SamplerReach.java"
WIDE_VULKAN = ROOT / "common/src/main/java/dev/vitrail/render/WideSamplerSets.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class WideResourcesContract(unittest.TestCase):
    def test_fixture_exposes_thirty_three_distinct_active_sampled_images(self):
        fragment = text(FIXTURE / "final.fsh")
        names = re.findall(r"uniform\s+sampler2D\s+(wide\d\d)\s*;", fragment)
        self.assertEqual(names, [f"wide{i:02d}" for i in range(33)])
        for name in names:
            self.assertEqual(fragment.count(f"texture2D({name}, probe)"), 1, name)
            self.assertIn(f"isWhite(s{name[-2:]})", fragment)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", fragment)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", fragment)

    def test_each_sampler_has_a_real_custom_texture_binding(self):
        properties = text(FIXTURE / "shaders.properties")
        declarations = re.findall(r"^customTexture\.(wide\d\d)\s*=\s*white\.png\s*$", properties, re.MULTILINE)
        self.assertEqual(declarations, [f"wide{i:02d}" for i in range(33)])
        self.assertTrue((FIXTURE / "white.png").is_file())
        self.assertGreater((FIXTURE / "white.png").stat().st_size, 0)

    def test_vitrail_keeps_sampler_semantics_backend_neutral(self):
        textures = text(PACK_TEXTURES)
        images = text(PACK_IMAGES)
        reach = text(SAMPLER_REACH)
        vulkan = text(WIDE_VULKAN)
        self.assertIn('CUSTOM_PREFIX = "customTexture."', textures)
        self.assertIn("key.startsWith(CUSTOM_PREFIX)", textures)
        self.assertIn("declared.supplied()", images)
        self.assertIn("samplers.removeIf", reach)
        self.assertIn("spvc_compiler_get_active_interface_variables", reach)
        self.assertIn("MoltenVK", vulkan)
        self.assertIn("VulkanCommandEncoder", vulkan)
        fixture_text = "\n".join(text(path) for path in (FIXTURE / "final.vsh", FIXTURE / "final.fsh", FIXTURE / "shaders.properties"))
        self.assertNotIn("ArgumentBuffer", fixture_text)
        self.assertNotIn("Metal", fixture_text)


if __name__ == "__main__":
    unittest.main()
