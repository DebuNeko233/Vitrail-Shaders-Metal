"""Lock raw sampler3D atlas filtering to the pack's .mcmeta semantics."""
from pathlib import Path
import math
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACK_TEXTURES = ROOT / 'common/src/main/java/dev/vitrail/pack/texture/PackTextures.java'
VOLUME_ATLAS = ROOT / 'common/src/main/java/dev/vitrail/pack/texture/VolumeAtlas.java'
VOLUME_FLATTENING = ROOT / 'common/src/main/java/dev/vitrail/glsl/VolumeFlattening.java'


def braced(text, marker, start=0):
    marker_at = text.index(marker, start)
    opening = text.index('{', marker_at)
    depth = 0
    for at in range(opening, len(text)):
        if text[at] == '{':
            depth += 1
        elif text[at] == '}':
            depth -= 1
            if depth == 0:
                return text[opening + 1:at], at + 1
    raise AssertionError(f'unclosed block after {marker!r}')


class VolumeFilterSemanticsTest(unittest.TestCase):
    def test_pack_filter_mode_reaches_volume_atlas_metadata(self):
        textures = PACK_TEXTURES.read_text(encoding='utf-8')
        atlas = VOLUME_ATLAS.read_text(encoding='utf-8')

        wiring = 'VolumeAtlas.of(texture.raw().orElseThrow(), texture.clamp(), texture.blur())'
        self.assertIn(wiring, textures)
        self.assertIn('VolumeAtlas.of(raw.orElseThrow(), texture.clamp(), texture.blur())', textures)
        self.assertIn('private final boolean linear;', atlas)
        self.assertIn('public boolean linear()', atlas)
        self.assertIn('return this.linear;', atlas)

    def test_nearest_depth_chooses_one_slice_while_linear_depth_interpolates(self):
        source = VOLUME_FLATTENING.read_text(encoding='utf-8')
        helper, _ = braced(source, 'static List<String> helper(')
        nearest, nearest_end = braced(helper, 'if (!atlas.linear())')
        linear, _ = braced(helper, 'else', nearest_end)

        self.assertIn('int ofSlice = clamp(int(floor(ofQ.z * ', nearest)
        self.assertEqual(1, nearest.count('texture(ofMap'))
        self.assertNotIn('mix(', nearest)
        self.assertNotIn('ofNear', nearest)
        self.assertNotIn('ofFar', nearest)

        self.assertIn('int ofNear', linear)
        self.assertIn('int ofFar', linear)
        self.assertEqual(2, linear.count('texture(ofMap'))
        self.assertEqual(1, linear.count('return mix('))

    def test_nearest_depth_boundary_maps_one_to_last_slice(self):
        def nearest_slice(z, depth):
            return min(max(math.floor(z * depth), 0), depth - 1)

        self.assertEqual(0, nearest_slice(0.0, 4))
        self.assertEqual(0, nearest_slice(0.249999, 4))
        self.assertEqual(1, nearest_slice(0.25, 4))
        self.assertEqual(3, nearest_slice(0.999999, 4))
        self.assertEqual(3, nearest_slice(1.0, 4))


if __name__ == '__main__':
    unittest.main()
