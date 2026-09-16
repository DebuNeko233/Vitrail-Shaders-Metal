"""Lock the Sodium terrain vertex ABI and material transport contracts."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
TERRAIN_VERTEX = ROOT / 'common/src/main/java/dev/vitrail/sodium/TerrainVertex.java'
TERRAIN_MESH = ROOT / 'common/src/main/java/dev/vitrail/sodium/TerrainMesh.java'
SODIUM_VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/SodiumVertex.java'
CHUNK_VERTEX_MIXIN = ROOT / 'common/src/main/java/dev/vitrail/mixin/sodium/ChunkVertexMixin.java'
BLOCK_RENDERER_MIXIN = ROOT / 'common/src/main/java/dev/vitrail/mixin/sodium/BlockRendererMixin.java'
FLUID_RENDERER_MIXIN = ROOT / 'common/src/main/java/dev/vitrail/mixin/sodium/DefaultFluidRendererMixin.java'

CHUNK_VERTEX_ENCODER = '''package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format;

import dev.vitrail.sodium.TerrainVertex;

public final class ChunkVertexEncoder {
    private ChunkVertexEncoder() {}

    public static final class Vertex implements TerrainVertex {
        private int blockId;
        private int blockOrigin;

        @Override
        public int vitrailBlockId() {
            return this.blockId;
        }

        @Override
        public void vitrailBlockId(int id) {
            this.blockId = id;
        }

        @Override
        public int vitrailBlockOrigin() {
            return this.blockOrigin;
        }

        @Override
        public void vitrailBlockOrigin(int packed) {
            this.blockOrigin = packed;
        }
    }
}
'''

HARNESS = '''package dev.vitrail.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;

public final class TerrainVertexContractCheck {
    public static void main(String[] args) {
        int packed = TerrainVertex.pack(17, -1, 34, 300);
        require(TerrainVertex.origin(packed, 0) == 1, "x must be section-local");
        require(TerrainVertex.origin(packed, 1) == 15, "negative y must keep its low four bits");
        require(TerrainVertex.origin(packed, 2) == 2, "z must be section-local");
        require(TerrainVertex.emission(packed) == 44, "emission must keep its low byte");

        ChunkVertexEncoder.Vertex[] vertices = new ChunkVertexEncoder.Vertex[4];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new ChunkVertexEncoder.Vertex();
        }

        if (TerrainVertex.stamp(vertices, 0x12345678) != vertices) {
            throw new AssertionError("stamp must preserve Sodium's scratch array");
        }
        if (TerrainVertex.stampOrigin(vertices, packed) != vertices) {
            throw new AssertionError("stampOrigin must preserve Sodium's scratch array");
        }
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            require(vertex.vitrailBlockId() == 0x12345678, "every corner must carry the block id");
            require(vertex.vitrailBlockOrigin() == packed, "every corner must carry block origin/emission");
        }

        TerrainVertex.stamp(vertices, 0);
        TerrainVertex.stampOrigin(vertices, 0);
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            require(vertex.vitrailBlockId() == 0, "a reused scratch quad must be clearable");
            require(vertex.vitrailBlockOrigin() == 0, "a reused scratch quad origin must be clearable");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


def compact(path):
    return re.sub(r'\s+', ' ', path.read_text(encoding='utf-8'))


class TerrainVertexAbiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-terrain-vertex-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/sodium/TerrainVertex.java', TERRAIN_VERTEX.read_text(encoding='utf-8')),
            write('net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder.java',
                  CHUNK_VERTEX_ENCODER),
            write('dev/vitrail/sodium/TerrainVertexContractCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_vertex_helpers_preserve_material_data(self):
        subprocess.run([
            'java', '-cp', str(self.classes), 'dev.vitrail.sodium.TerrainVertexContractCheck',
        ], check=True, capture_output=True, text=True)

    def test_mesh_appends_one_word_extras_after_sodium_layout(self):
        mesh = compact(TERRAIN_MESH)
        self.assertIn('private final ChunkVertexType inner = ChunkMeshFormats.COMPACT;', mesh)
        self.assertIn('if (extra.format().blockSize() != Integer.BYTES)', mesh)
        self.assertIn(
            'builder.addAttribute(element.name(), element.offset(), element.format().blockSize(), element.format(), 1);',
            mesh,
        )
        self.assertIn('int at = base.getVertexSize();', mesh)
        self.assertIn(
            'builder.addAttribute(extra.attribute(), at, extra.format().blockSize(), extra.format(), 1);',
            mesh,
        )
        self.assertIn('at += Integer.BYTES;', mesh)

        expected = [
            'BLOCK_ID(SodiumVertex.BLOCK_ID, GpuFormat.R32_UINT)',
            'MID_TEX_COORD(SodiumVertex.MID_TEX_COORD, GpuFormat.RG16_UINT)',
            'MID_BLOCK(SodiumVertex.MID_BLOCK, GpuFormat.RGBA8_SINT)',
            'TANGENT_FRAME(SodiumVertex.TANGENT_FRAME, GpuFormat.R32_UINT)',
            'TINT_AND_AO(SodiumVertex.TINT_AND_AO, GpuFormat.RGBA8_UNORM)',
        ]
        positions = [mesh.index(item) for item in expected]
        self.assertEqual(positions, sorted(positions), 'TerrainMesh.Extra order is a vertex ABI')

    def test_shader_attribute_order_matches_mesh_extra_order(self):
        vertex = compact(SODIUM_VERTEX)
        self.assertIn(
            'List.of("a_Position", "a_Color", "a_TexCoord", "a_LightAndData", BLOCK_ID, MID_TEX_COORD, MID_BLOCK, TANGENT_FRAME, TINT_AND_AO);',
            vertex,
        )
        self.assertIn(
            'return ATTRIBUTES.stream() .filter(attribute -> !OURS.contains(attribute) || reads.contains(attribute)) .toList();',
            vertex,
        )

    def test_translucent_sorter_copy_preserves_custom_vertex_words(self):
        mixin = compact(CHUNK_VERTEX_MIXIN)
        self.assertIn('@Inject(method = "copyVertexTo", at = @At("TAIL"), require = 1)', mixin)
        self.assertIn(
            '((TerrainVertex) to).vitrailBlockId(((TerrainVertex) from).vitrailBlockId());', mixin,
        )
        self.assertIn(
            '((TerrainVertex) to).vitrailBlockOrigin(((TerrainVertex) from).vitrailBlockOrigin());', mixin,
        )

    def test_block_quads_stamp_direct_and_sorted_paths(self):
        mixin = compact(BLOCK_RENDERER_MIXIN)
        self.assertIn('ChunkMeshBufferBuilder;push(', mixin)
        self.assertIn('TranslucentGeometryCollector;appendQuad(', mixin)
        self.assertGreaterEqual(mixin.count('return vitrail$stamp(vertices);'), 2)
        self.assertIn('this.vitrail$stampedId = state == null ? BlockStateIds.NONE : BlockStateIds.packed(state);', mixin)
        self.assertIn('TerrainVertex.pack(this.pos.getX(), this.pos.getY(), this.pos.getZ(),', mixin)
        self.assertIn('TerrainVertex.stampOrigin(vertices, this.vitrail$stampedOrigin);', mixin)
        self.assertIn('return TerrainVertex.stamp(vertices, this.vitrail$stampedId);', mixin)

    def test_fluid_quads_keep_fluid_identity_on_direct_and_sorted_paths(self):
        mixin = compact(FLUID_RENDERER_MIXIN)
        self.assertIn('@Inject(method = "render", at = @At("HEAD"), require = 1)', mixin)
        self.assertIn('FluidState fluidState', mixin)
        self.assertIn('fluidState.createLegacyBlock()', mixin)
        self.assertIn('this.vitrail$id = BlockStateIds.packedFluid(fluidBlock);', mixin)
        self.assertIn('ChunkMeshBufferBuilder;push(', mixin)
        self.assertIn('TranslucentGeometryCollector;appendQuad(', mixin)
        stamp = 'return TerrainVertex.stamp(TerrainVertex.stampOrigin(vertices, this.vitrail$origin), this.vitrail$id);'
        self.assertEqual(mixin.count(stamp), 2)


if __name__ == '__main__':
    unittest.main()
