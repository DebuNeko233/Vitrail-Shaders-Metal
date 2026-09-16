"""Lock the batched PHASE 9 shadow entities/depth/color acceptance contracts."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests/fixtures/shaderpacks"
ENTITY_DRAW = ROOT / "common/src/main/java/dev/vitrail/render/EntityDraw.java"
SHADOW_TARGETS = ROOT / "common/src/main/java/dev/vitrail/render/ShadowTargets.java"
PACK_DIRECTIVES = ROOT / "common/src/main/java/dev/vitrail/pack/target/PackDirectives.java"
FALLBACKS = ROOT / "common/src/main/java/dev/vitrail/pack/model/ProgramFallbacks.java"
TERRAIN_DRAW = ROOT / "common/src/main/java/dev/vitrail/render/TerrainDraw.java"


def text(path):
    return path.read_text(encoding="utf-8")


def compact(path):
    return re.sub(r"\s+", " ", text(path))


class ShadowBatchContractTest(unittest.TestCase):
    def test_shadow_entities_fixture_owns_direct_entity_route(self):
        root = FIXTURES / "shadow-entities-contract/shaders"
        self.assertEqual(
            {p.name for p in root.iterdir()},
            {"shadow.vsh", "shadow.fsh", "shadow_entities.vsh", "shadow_entities.fsh",
             "composite.vsh", "composite.fsh", "final.vsh", "final.fsh"},
        )
        direct = text(root / "shadow_entities.fsh")
        vertex = text(root / "shadow_entities.vsh")
        terrain = text(root / "shadow.fsh")
        self.assertIn("entityAbiOk", direct)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", direct)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", direct)
        self.assertIn("mc_midTexCoord", vertex)
        self.assertIn("at_tangent", vertex)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", terrain)

        draw = compact(ENTITY_DRAW)
        self.assertIn("private static volatile boolean shadowFeatures;", draw)
        self.assertIn("if (shadowFeatures) { return SHADOW_ELEMENTS.get(pipeline); }", draw)
        self.assertIn('new Element(mob.pipeline(), "shadow_" + mob.element(), SHADOW_ENTITIES,', draw)
        self.assertIn("RenderStage.ENTITIES, false", draw)
        self.assertIn('private static final String SHADOW_ENTITIES = "shadow_entities";', draw)

        fallbacks = compact(FALLBACKS)
        self.assertIn('parents.put("shadow_entities", "shadow");', fallbacks)

    def test_shadow_depth_fixture_proves_pre_translucent_copy_semantics(self):
        root = FIXTURES / "shadow-depth-contract/shaders"
        self.assertEqual(
            {p.name for p in root.iterdir()},
            {"shadow.vsh", "shadow.fsh", "composite.vsh", "composite.fsh",
             "final.vsh", "final.fsh"},
        )
        composite = text(root / "composite.fsh")
        self.assertIn("uniform sampler2D shadowtex0;", composite)
        self.assertIn("uniform sampler2D shadowtex1;", composite)
        self.assertIn("const bool shadowtex0Nearest = true;", composite)
        self.assertIn("const bool shadowtex1Nearest = true;", composite)
        self.assertIn("float delta = opaque - complete;", composite)
        self.assertIn("vec4(0.0, 1.0, 1.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 1.0, 1.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)

        targets = compact(SHADOW_TARGETS)
        self.assertIn("void copyWithoutTranslucents(CommandEncoder encoder)", targets)
        self.assertIn('createTexture(() -> "Vitrail shadowtex1"', targets)
        self.assertIn("encoder.copyTextureToTexture(this.depth, this.noTranslucents", targets)
        self.assertIn("this.copied = true;", targets)
        self.assertIn("return this.copied ? this.noTranslucentsView : depth();", targets)

        terrain = compact(TERRAIN_DRAW)
        self.assertIn("self.targets.shadow().copyWithoutTranslucents(device.createCommandEncoder());", terrain)

    def test_shadow_color_fixture_keeps_two_attachment_slots_distinct(self):
        root = FIXTURES / "shadow-color-contract/shaders"
        self.assertEqual(
            {p.name for p in root.iterdir()},
            {"shadow.vsh", "shadow.fsh", "composite.vsh", "composite.fsh",
             "final.vsh", "final.fsh"},
        )
        shadow = text(root / "shadow.fsh")
        composite = text(root / "composite.fsh")
        self.assertIn("/* DRAWBUFFERS:01 */", shadow)
        self.assertIn("shadowcolor0Clear = true", shadow)
        self.assertIn("shadowcolor1Clear = true", shadow)
        self.assertIn("gl_FragData[0] = vec4(1.0, 0.0, 0.0, 1.0);", shadow)
        self.assertIn("gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);", shadow)
        self.assertIn("uniform sampler2D shadowcolor0;", composite)
        self.assertIn("uniform sampler2D shadowcolor1;", composite)
        self.assertIn("vec4(1.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)

        directives = compact(PACK_DIRECTIVES)
        self.assertIn("public static final int SHADOW_COLOURS = 2;", directives)
        self.assertIn("private void shadowColour(ConstDirectives.Directive directive)", directives)
        self.assertIn("ShadowColour(TargetFormat.Resolution format, boolean clear,", directives)

    def test_mipmaps_remain_a_separate_checkpoint(self):
        # This batch intentionally closes only entity routing, depth moments, and color attachments.
        # The shadow-depth mip chain is a backend capability gate of its own.
        for name in ("shadow-entities-contract", "shadow-depth-contract", "shadow-color-contract"):
            all_text = "\n".join(text(p) for p in (FIXTURES / name).rglob("*.*") if p.is_file())
            self.assertNotIn("shadowtex0Mipmap", all_text)
            self.assertNotIn("shadowtex1Mipmap", all_text)
            self.assertNotIn("generateShadowMipmap", all_text)


if __name__ == "__main__":
    unittest.main()
