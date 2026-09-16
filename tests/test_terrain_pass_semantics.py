"""Lock terrain pass semantics to the real Vitrail implementation used by Sodium draws."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
TERRAIN_PASS = ROOT / 'common/src/main/java/dev/vitrail/pack/program/TerrainPass.java'
ALPHA_TEST = ROOT / 'common/src/main/java/dev/vitrail/pack/model/AlphaTest.java'
TERRAIN_PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/TerrainProgram.java'
TERRAIN_DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/TerrainDraw.java'

RENDER_STAGE = '''package dev.vitrail.pack.model;
public enum RenderStage {
    TERRAIN_SOLID,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT
}
'''

SHADER_PROPERTIES = '''package dev.vitrail.pack.source;
public final class ShaderProperties {
    private ShaderProperties() {}
}
'''

HARNESS = '''package dev.vitrail.pack.program;

import dev.vitrail.pack.model.AlphaTest;
import dev.vitrail.pack.model.RenderStage;
import java.util.Map;

public final class TerrainPassContractCheck {
    public static void main(String[] args) {
        check(TerrainPass.SOLID, "gbuffers_terrain_solid", AlphaTest.OFF,
                false, true, false, false, RenderStage.TERRAIN_SOLID,
                TerrainPass.SHADOW_SOLID);
        check(TerrainPass.CUTOUT, "gbuffers_terrain_cutout", AlphaTest.CUTOUT,
                false, true, false, false, RenderStage.TERRAIN_CUTOUT,
                TerrainPass.SHADOW_CUTOUT);
        check(TerrainPass.TRANSLUCENT, "gbuffers_water", AlphaTest.NON_ZERO,
                true, false, true, false, RenderStage.TERRAIN_TRANSLUCENT,
                TerrainPass.SHADOW_TRANSLUCENT);

        check(TerrainPass.SHADOW_SOLID, "shadow_solid", AlphaTest.OFF,
                false, false, false, true, RenderStage.TERRAIN_SOLID, null);
        check(TerrainPass.SHADOW_CUTOUT, "shadow_cutout", AlphaTest.CUTOUT,
                false, false, false, true, RenderStage.TERRAIN_CUTOUT, null);
        check(TerrainPass.SHADOW_TRANSLUCENT, "shadow_water", AlphaTest.OFF,
                false, false, false, true, RenderStage.TERRAIN_TRANSLUCENT, null);

        AlphaTest override = new AlphaTest(AlphaTest.Function.GREATER, 0.25F);
        if (!TerrainPass.CUTOUT.alphaTest(Map.of("gbuffers_terrain", override),
                "gbuffers_terrain").equals(override)) {
            throw new AssertionError("alphaTest override must use the program that actually served the pass");
        }
        if (!TerrainPass.CUTOUT.alphaTest(Map.of("gbuffers_terrain", override),
                "gbuffers_terrain_cutout").equals(AlphaTest.CUTOUT)) {
            throw new AssertionError("an override for another served program must not leak into this pass");
        }
    }

    private static void check(TerrainPass pass, String program, AlphaTest alpha,
            boolean blended, boolean covers, boolean afterDeferred, boolean shadow,
            RenderStage stage, TerrainPass shadowPass) {
        if (!pass.program().equals(program)) {
            fail(pass, "program", program, pass.program());
        }
        if (!pass.alphaTest(Map.of(), program).equals(alpha)) {
            fail(pass, "alpha", alpha, pass.alphaTest(Map.of(), program));
        }
        if (pass.blended() != blended) {
            fail(pass, "blended", blended, pass.blended());
        }
        if (pass.covers() != covers) {
            fail(pass, "covers", covers, pass.covers());
        }
        if (pass.afterDeferred() != afterDeferred) {
            fail(pass, "afterDeferred", afterDeferred, pass.afterDeferred());
        }
        if (pass.shadow() != shadow) {
            fail(pass, "shadow", shadow, pass.shadow());
        }
        if (pass.stage() != stage) {
            fail(pass, "stage", stage, pass.stage());
        }
        if (pass.inShadow() != shadowPass) {
            fail(pass, "inShadow", shadowPass, pass.inShadow());
        }
    }

    private static void fail(TerrainPass pass, String field, Object expected, Object actual) {
        throw new AssertionError(pass + " " + field + " expected " + expected + " but got " + actual);
    }
}
'''


class TerrainPassSemanticsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-terrain-pass-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/pack/program/TerrainPass.java', TERRAIN_PASS.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/model/AlphaTest.java', ALPHA_TEST.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/model/RenderStage.java', RENDER_STAGE),
            write('dev/vitrail/pack/source/ShaderProperties.java', SHADER_PROPERTIES),
            write('dev/vitrail/pack/program/TerrainPassContractCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_terrain_pass_defaults_and_overrides(self):
        subprocess.run([
            'java', '-cp', str(self.classes),
            'dev.vitrail.pack.program.TerrainPassContractCheck',
        ], check=True, capture_output=True, text=True)

    def test_pipeline_consumes_pass_local_state(self):
        program = re.sub(r'\s+', ' ', TERRAIN_PROGRAM.read_text(encoding='utf-8'))
        self.assertIn(
            'pass.blended() ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.<BlendFunction>empty()',
            program,
        )
        self.assertIn('pass.covers(), false, pass.afterDeferred(),', program)
        self.assertIn('!pass.shadow(), depthState(pass), pass.stage(), null,', program)
        self.assertIn(
            'pass.shadow() ? new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true) : DepthStencilState.DEFAULT',
            program,
        )

    def test_scene_seed_mask_requires_both_opaque_passes(self):
        draw = re.sub(r'\s+', ' ', TERRAIN_DRAW.read_text(encoding='utf-8'))
        self.assertIn(
            'private static final List<TerrainPass> OPAQUE = List.of(TerrainPass.SOLID, TerrainPass.CUTOUT);',
            draw,
        )
        self.assertIn('for (TerrainPass pass : OPAQUE)', draw)
        self.assertIn('if (program == null || !program.covers())', draw)
        self.assertGreaterEqual(draw.count('TerrainPass drawn = drawn(pass);'), 2)


if __name__ == '__main__':
    unittest.main()
