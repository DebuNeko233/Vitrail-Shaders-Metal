from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONVENTION = ROOT / "tests/fixtures/shaderpacks/dimension-convention-contract/shaders"
PROPERTIES = ROOT / "tests/fixtures/shaderpacks/dimension-properties-contract/shaders"
DIMENSION_SET = ROOT / "common/src/main/java/dev/vitrail/pack/source/DimensionSet.java"
PACK_PLACE = ROOT / "common/src/main/java/dev/vitrail/render/PackPlace.java"
PACK_CHOICE = ROOT / "common/src/main/java/dev/vitrail/render/PackChoice.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class DimensionRoutingContract(unittest.TestCase):
    def assert_final_color(self, folder: Path, vec4: str):
        self.assertTrue((folder / "final.vsh").is_file(), folder)
        fragment = text(folder / "final.fsh")
        self.assertIn(f"gl_FragColor = {vec4};", fragment)
        self.assertNotIn("sampler2D", fragment)

    def test_conventional_world_directories_are_distinct_and_root_fails_loudly(self):
        self.assertFalse((CONVENTION / "dimension.properties").exists())
        self.assert_final_color(CONVENTION, "vec4(1.0, 0.0, 1.0, 1.0)")
        self.assert_final_color(CONVENTION / "world0", "vec4(0.0, 1.0, 0.0, 1.0)")
        self.assert_final_color(CONVENTION / "world-1", "vec4(1.0, 0.0, 0.0, 1.0)")
        self.assert_final_color(CONVENTION / "world1", "vec4(0.0, 0.0, 1.0, 1.0)")

    def test_dimension_properties_uses_custom_folder_names_custom_id_and_catchall(self):
        props = text(PROPERTIES / "dimension.properties")
        self.assertIn("dimension.surface = overworld", props)
        self.assertIn("dimension.under = the_nether", props)
        self.assertIn("dimension.moon = vitrail:moon", props)
        self.assertIn("dimension.catchall = *", props)
        for forbidden in ("world0", "world-1", "world1"):
            self.assertFalse((PROPERTIES / forbidden).exists())
        self.assert_final_color(PROPERTIES, "vec4(1.0, 0.0, 1.0, 1.0)")
        self.assert_final_color(PROPERTIES / "surface", "vec4(0.0, 1.0, 1.0, 1.0)")
        self.assert_final_color(PROPERTIES / "under", "vec4(1.0, 1.0, 0.0, 1.0)")
        self.assert_final_color(PROPERTIES / "catchall", "vec4(1.0, 1.0, 1.0, 1.0)")
        self.assert_final_color(PROPERTIES / "moon", "vec4(1.0, 0.5, 0.0, 1.0)")

    def test_dimension_set_locks_convention_properties_and_fallback_semantics(self):
        source = text(DIMENSION_SET)
        self.assertIn('"world0", "minecraft:overworld"', source)
        self.assertIn('"world-1", "minecraft:the_nether"', source)
        self.assertIn('"world1", "minecraft:the_end"', source)
        self.assertIn('private static final String ANY = "*";', source)
        self.assertIn("if (!declared)", source)
        self.assertIn('places.put(ANY, "world0");', source)
        self.assertIn("places.put(normalise(world), folder);", source)
        self.assertIn('return named != null ? named : this.places.getOrDefault(ANY, "");', source)
        self.assertIn('return world.indexOf(\':\') < 0 ? "minecraft:" + world : world;', source)

    def test_custom_dimension_exact_id_wins_before_skybox_fallback(self):
        source = text(PACK_PLACE)
        self.assertIn("String world = level.dimension().identifier().toString();", source)
        self.assertIn("if (dimensions == null || !dimensions.declares(world))", source)
        self.assertIn("DimensionType.Skybox.END", source)
        self.assertIn("DimensionType.Skybox.OVERWORLD", source)
        self.assertIn("return dimensions != null && !dimensions.place(world()).equals(loadedPlace);", source)

    def test_dimension_directory_change_reloads_the_whole_chain(self):
        source = text(PACK_CHOICE)
        self.assertIn("boolean moved = PackPlace.moved();", source)
        self.assertIn("if (!stale && !moved)", source)
        self.assertIn("a dimension replaces the root rather than layering", source)
        self.assertIn("String place = PackPlace.place(opened.source());", source)
        self.assertIn("String world = PackPlace.world();", source)
        self.assertIn("PackProgram.loadChain(opened, place", source)


if __name__ == "__main__":
    unittest.main()
