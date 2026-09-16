"""Lock Sodium terrain-pass routing and the unknown-pass fallback boundary."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SODIUM_PASSES = ROOT / 'common/src/main/java/dev/vitrail/sodium/SodiumPasses.java'
CHUNK_RENDERER_MIXIN = ROOT / 'common/src/main/java/dev/vitrail/mixin/sodium/MixinDefaultChunkRenderer.java'

TERRAIN_PASS = '''package dev.vitrail.pack.program;
public final class TerrainPass {
    public static final TerrainPass SOLID = new TerrainPass("SOLID");
    public static final TerrainPass CUTOUT = new TerrainPass("CUTOUT");
    public static final TerrainPass TRANSLUCENT = new TerrainPass("TRANSLUCENT");
    private final String name;
    private TerrainPass(String name) { this.name = name; }
    @Override public String toString() { return this.name; }
}
'''

SODIUM_RENDER_PASS = '''package net.caffeinemc.mods.sodium.client.render.chunk.terrain;
public class TerrainRenderPass {}
'''

DEFAULT_PASSES = '''package net.caffeinemc.mods.sodium.client.render.chunk.terrain;
public final class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass();
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass();
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass();
    private DefaultTerrainRenderPasses() {}
}
'''

HARNESS = '''package dev.vitrail.sodium;
import dev.vitrail.pack.program.TerrainPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

public final class SodiumPassContractCheck {
    public static void main(String[] args) {
        require(SodiumPasses.of(DefaultTerrainRenderPasses.SOLID) == TerrainPass.SOLID, "solid");
        require(SodiumPasses.of(DefaultTerrainRenderPasses.CUTOUT) == TerrainPass.CUTOUT, "cutout");
        require(SodiumPasses.of(DefaultTerrainRenderPasses.TRANSLUCENT) == TerrainPass.TRANSLUCENT,
                "translucent");
        require(SodiumPasses.of(new TerrainRenderPass()) == null, "unknown pass must stay with Sodium");
        require(SodiumPasses.of(null) == null, "null must not be guessed as a known pass");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


class SodiumPassRoutingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-sodium-pass-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/sodium/SodiumPasses.java', SODIUM_PASSES.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/program/TerrainPass.java', TERRAIN_PASS),
            write('net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.java',
                  SODIUM_RENDER_PASS),
            write('net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.java',
                  DEFAULT_PASSES),
            write('dev/vitrail/sodium/SodiumPassContractCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_router_only_claims_known_singletons(self):
        subprocess.run([
            'java', '-cp', str(self.classes), 'dev.vitrail.sodium.SodiumPassContractCheck',
        ], check=True, capture_output=True, text=True)

    def test_renderer_preserves_original_pass_when_vitrail_has_no_descriptor(self):
        source = re.sub(r'\s+', ' ', CHUNK_RENDERER_MIXIN.read_text(encoding='utf-8'))
        self.assertIn('TerrainPass ours = SodiumPasses.of(pass);', source)
        self.assertIn('RenderPassDescriptor descriptor = ours == null ? null : TerrainDraw.descriptor(ours, colour, depth);', source)
        self.assertIn('return descriptor == null ? original.call(encoder, label, colour, clearColour, depth, clearDepth) : GeometryHold.open(encoder, descriptor);', source)


if __name__ == '__main__':
    unittest.main()
