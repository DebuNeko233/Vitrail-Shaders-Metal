from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/wide-resources-contract/shaders"
PACK_TEXTURES = ROOT / "common/src/main/java/dev/vitrail/pack/texture/PackTextures.java"
PACK_IMAGES = ROOT / "common/src/main/java/dev/vitrail/render/PackImages.java"
SAMPLER_REACH = ROOT / "common/src/main/java/dev/vitrail/render/SamplerReach.java"
WIDE_HANDLING = ROOT / "common/src/main/java/dev/vitrail/render/WideSamplerSets.java"
# The backend's shader-module seam, which is where a declared-but-unreached sampler is removed
# from the compiled module. It lives in the companion checkout, which is a required dependency.
SEAM = (ROOT.parent / "metallum/src/main/java/com/metallum/mixin/render/ShaderModuleHookMixin.java")


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
        self.assertIn('CUSTOM_PREFIX = "customTexture."', textures)
        self.assertIn("key.startsWith(CUSTOM_PREFIX)", textures)
        self.assertIn("declared.supplied()", images)
        # Vitrail owns the semantic half - which declared names the entry point never reaches - and
        # answers it from the SPIR-V it is handed; the removal from the module is the backend's, on
        # the other side of the shader-module seam, so the two files are asserted together.
        self.assertIn("spvc_compiler_get_active_interface_variables", reach)
        self.assertIn("public static List<String> unreached(", reach)
        self.assertNotIn("removeIf", reach)
        self.assertIn("removeIf", SEAM.read_text(encoding="utf-8"))
        fixture_text = "\n".join(text(path) for path in (FIXTURE / "final.vsh", FIXTURE / "final.fsh", FIXTURE / "shaders.properties"))
        self.assertNotIn("ArgumentBuffer", fixture_text)
        self.assertNotIn("Metal", fixture_text)

    def test_the_wide_set_decision_is_the_backends_and_not_vitrails(self):
        """How a wide resource set is bound is the backend's call, and Vitrail no longer makes it.

        A stage reading more samplers than a Metal argument buffer has slots for used to be handled
        here: this engine recognised the portability layer, read two driver limits, took the push
        flag off a set layout and allocated a set instead, so that the driver would bind the layout
        through an argument buffer. None of that vocabulary survives - there is no set layout, no
        push and no driver limit to read - and the decision now belongs where the binding is built.
        What Vitrail keeps is the semantic half, which is the reached-versus-declared reading
        `SamplerReach` performs, and that is what the assertions above pin.
        """
        self.assertFalse(WIDE_HANDLING.exists(),
                         "the wide-set workaround is back: binding a wide resource set is the "
                         "backend's decision, taken from the logical resource layout, and an engine "
                         "that reads driver limits to make it is one that will make it wrongly on "
                         "the next backend")


if __name__ == "__main__":
    unittest.main()
