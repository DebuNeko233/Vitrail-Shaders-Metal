"""Contract for native-Metal handling of statically reversed smoothstep edges."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
TRANSLATOR = ROOT / "common/src/main/java/dev/vitrail/glsl/GlslTranslator.java"
EMITTER = ROOT / "common/src/main/java/dev/vitrail/glsl/Emitter.java"
VENDOR = ROOT / "common/src/main/java/dev/vitrail/glsl/VendorExtensions.java"
METAL = ROOT / "common/src/main/java/dev/vitrail/mixin/metallum/MetalBackendMixin.java"
VULKAN = ROOT / "common/src/main/java/dev/vitrail/mixin/VulkanBackendMixin.java"


class ReversedSmoothstepContractTest(unittest.TestCase):
    def test_backend_fact_is_published_and_cache_keyed(self):
        vendor = VENDOR.read_text(encoding="utf-8")
        self.assertIn("public static void serveMetal(boolean metalBackend)", vendor)
        self.assertIn('return metal ? driver + ";Metal" : driver;', vendor)
        self.assertIn("VendorExtensions.serveMetal(true);", METAL.read_text(encoding="utf-8"))
        self.assertIn("VendorExtensions.serveMetal(false);", VULKAN.read_text(encoding="utf-8"))

    def test_only_static_native_metal_reverse_calls_are_rewritten(self):
        source = TRANSLATOR.read_text(encoding="utf-8")
        self.assertIn('name.equals("smoothstep") && VendorExtensions.metal()', source)
        self.assertIn("!this.declaredNames.contains(name) && reversedLiteralSmoothstep(index)", source)
        self.assertIn("edge0 > edge1", source)
        self.assertIn("this.tokens.get(first).kind() != Kind.NUMBER", source)
        self.assertIn("this.tokens.get(second).kind() != Kind.NUMBER", source)

    def test_helper_spells_defined_hermite_expression(self):
        source = EMITTER.read_text(encoding="utf-8")
        self.assertIn("GlslTranslator.REVERSED_SMOOTHSTEP", source)
        self.assertIn("clamp((ofX - ofEdge0) / (ofEdge1 - ofEdge0), 0.0, 1.0)", source)
        self.assertIn("return ofT * ofT * (3.0 - 2.0 * ofT);", source)
        self.assertIn('"dvec4"', source)
        self.assertIn("(double ofEdge0, double ofEdge1,", source)

    def test_implementation_has_no_pack_or_cloud_special_case(self):
        slices = (
            TRANSLATOR.read_text(encoding="utf-8")
            + EMITTER.read_text(encoding="utf-8")
            + VENDOR.read_text(encoding="utf-8")
        )
        self.assertNotIn("Photon", slices)
        self.assertNotIn("CLOUDS_CUMULUS", slices)


if __name__ == "__main__":
    unittest.main()
