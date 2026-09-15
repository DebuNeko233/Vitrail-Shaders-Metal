from pathlib import Path
import json
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHADERS = ROOT / "tests/fixtures/shaderpacks/phase16-pbr-contract/shaders"
RESOURCES = ROOT / "tests/fixtures/resourcepacks/phase16-pbr-resources"
GEOMETRY = ROOT / "common/src/main/java/dev/vitrail/render/GeometryProgram.java"
PBR_ATLASES = ROOT / "common/src/main/java/dev/vitrail/render/pbr/PbrAtlases.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise AssertionError(f"not a PNG: {path}")
    return struct.unpack(">II", data[16:24])


class Phase16PbrContract(unittest.TestCase):
    def test_resource_pack_targets_current_26_2_format_and_one_stone_sprite(self):
        meta = json.loads(text(RESOURCES / "pack.mcmeta"))["pack"]
        self.assertEqual(88, meta["min_format"])
        self.assertEqual(88, meta["max_format"])
        textures = RESOURCES / "assets/minecraft/textures/block"
        for name in ("stone.png", "stone_n.png", "stone_s.png"):
            self.assertEqual((16, 16), png_size(textures / name))

    def test_terrain_shader_requires_albedo_marker_before_judging_pbr_maps(self):
        fragment = text(SHADERS / "gbuffers_terrain.fsh")
        self.assertIn("uniform sampler2D gtexture;", fragment)
        self.assertIn("uniform sampler2D normals;", fragment)
        self.assertIn("uniform sampler2D specular;", fragment)
        self.assertIn("bool markerStone = distance(albedo, marker) < 0.025;", fragment)
        self.assertIn("vec3(0.2, 0.4, 0.6)", fragment)
        self.assertIn("vec3(0.8, 0.3019608, 0.1019608)", fragment)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", fragment)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", fragment)

    def test_production_geometry_resolves_material_samplers_from_bound_image(self):
        geometry = text(GEOMETRY)
        atlases = text(PBR_ATLASES)
        self.assertIn("dev.vitrail.render.pbr.PbrAtlases", geometry)
        self.assertIn("dev.vitrail.render.pbr.PbrTextures", geometry)
        self.assertIn("PbrMap", geometry)
        self.assertIn("PbrAtlas.read(atlas, texture, sprites, resources, labPbr())", atlases)
        self.assertIn("built.follows(atlas)", atlases)
        self.assertIn("return built.view(map);", atlases)

    def test_fixture_itself_is_backend_neutral(self):
        fixture_text = "\n".join(
            text(path) for path in SHADERS.iterdir() if path.is_file()
        )
        self.assertNotIn("Metal", fixture_text)
        self.assertNotIn("MTL", fixture_text)


if __name__ == "__main__":
    unittest.main()
