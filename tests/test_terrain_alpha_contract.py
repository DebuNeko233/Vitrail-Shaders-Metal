"""Lock terrain alpha-test injection and coverage ordering contracts."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ALPHA_TEST = ROOT / 'common/src/main/java/dev/vitrail/pack/model/AlphaTest.java'
TRANSLATOR = ROOT / 'common/src/main/java/dev/vitrail/glsl/GlslTranslator.java'
EMITTER = ROOT / 'common/src/main/java/dev/vitrail/glsl/Emitter.java'
GEOMETRY_PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/GeometryProgram.java'

HARNESS = '''package dev.vitrail.pack.model;

public final class AlphaTestContractCheck {
    public static void main(String[] args) {
        require(AlphaTest.OFF.discard("ofFragData0.a").equals(""), "solid must not discard");
        require(AlphaTest.CUTOUT.discard("ofFragData0.a").equals(
                "if (!(ofFragData0.a > 0.5)) { discard; }"),
                "terrain cutout must use Iris/Sodium's half threshold");
        require(AlphaTest.NON_ZERO.discard("ofFragData0.a").equals(
                "if (!(ofFragData0.a > 0.0001)) { discard; }"),
                "translucent terrain must use Iris's non-zero threshold");
        require(new AlphaTest(AlphaTest.Function.NEVER, 0.0F)
                .discard("ofFragData0.a").equals("discard;"), "NEVER must always discard");
        require(new AlphaTest(AlphaTest.Function.GEQUAL, 0.25F)
                .discard("a").equals("if (!(a >= 0.25)) { discard; }"),
                "comparison must stay in the negated Iris form");
        require(AlphaTest.parse("off").orElseThrow().equals(AlphaTest.OFF), "off parse");
        require(AlphaTest.parse("GL_GREATER 0.25").orElseThrow()
                .equals(new AlphaTest(AlphaTest.Function.GREATER, 0.25F)), "override parse");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
'''


def compact(path):
    return re.sub(r'\s+', ' ', path.read_text(encoding='utf-8'))


class TerrainAlphaContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-terrain-alpha-')
        root = Path(cls.temp.name)

        alpha = root / 'dev/vitrail/pack/model/AlphaTest.java'
        alpha.parent.mkdir(parents=True, exist_ok=True)
        alpha.write_text(ALPHA_TEST.read_text(encoding='utf-8'), encoding='utf-8')

        harness = root / 'dev/vitrail/pack/model/AlphaTestContractCheck.java'
        harness.write_text(HARNESS, encoding='utf-8')

        cls.classes = root / 'classes'
        subprocess.run([
            'javac', '-d', str(cls.classes), str(alpha), str(harness),
        ], check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_real_alpha_test_thresholds_and_discard_text(self):
        subprocess.run([
            'java', '-cp', str(self.classes), 'dev.vitrail.pack.model.AlphaTestContractCheck',
        ], check=True, capture_output=True, text=True)

    def test_translator_only_injects_fixed_function_alpha_on_legacy_slot_zero(self):
        source = compact(TRANSLATOR)
        self.assertIn(
            'if (this.stage != ProgramStage.FRAGMENT || !this.alphaTest.tests() || !this.legacySlotZero) { return; }',
            source,
        )
        self.assertIn('Output first = this.packOutputs.get(0);', source)
        self.assertIn('if (first != null && !first.type().equals("vec4")) { return; }', source)
        self.assertIn('this.alphaEpilogue = this.packMainName >= 0;', source)

        # The legacy flag is raised by the fixed-function output spellings, not merely because a
        # pack declared location zero itself. This keeps a self-declared MRT payload's alpha from
        # being mistaken for cutout coverage.
        self.assertIn('if (token.identifier("gl_FragColor"))', source)
        self.assertIn('this.legacySlotZero |= slot == 0;', source)
        plan = source.index('private void planAlphaEpilogue()')
        own_output = source.index('Output first = this.packOutputs.get(0);', plan)
        self.assertGreater(own_output, plan)

    def test_geometry_diagnostic_distinguishes_reference_parity_from_injection_failure(self):
        source = compact(GEOMETRY_PROGRAM)
        self.assertIn('if (fragment.text().contains("ofFragData0"))', source)
        self.assertIn('its legacy draw-buffer-zero', source)
        self.assertIn('output could not be given the test', source)
        self.assertIn('its fragment stage writes no legacy draw-buffer-zero output. The reference', source)
        self.assertIn('does not inject that test into pack-declared outputs either', source)

    def test_wrapper_runs_pack_then_discard_then_coverage(self):
        source = compact(EMITTER)
        start = source.index('String wrapper(Set<String> varyings, Set<String> shadowed)')
        end = source.index('private boolean owesInitialisers()', start)
        wrapper = source[start:end]

        body = wrapper.index('+ body')
        discard = wrapper.index('this.alphaTest.discard(outputName(0, shadowed) + ".a")')
        coverage = wrapper.index('+ (this.covers ? " " + COVERAGE + " = " + writtenDepth() + ";" : "")')
        self.assertLess(body, discard)
        self.assertLess(discard, coverage)
        self.assertIn('(wrapsFragment() ? GlslTranslator.ORDER_OUTPUTS + "(); " : "")', wrapper)

    def test_coverage_depth_initialisation_happens_before_pack_body_only_when_needed(self):
        source = compact(EMITTER)
        self.assertIn(
            'return this.covers && this.namesFragDepth ? "gl_FragDepth = gl_FragCoord.z; " : "";',
            source,
        )
        self.assertIn(
            'return this.namesFragDepth ? "gl_FragDepth" : "gl_FragCoord.z";',
            source,
        )


if __name__ == '__main__':
    unittest.main()
