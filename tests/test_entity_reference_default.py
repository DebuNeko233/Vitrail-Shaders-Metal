"""Lock entity reference-default diagnostics without changing the entity vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENTITY_VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/EntityVertex.java'
ENTITY_PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/EntityProgram.java'
ENTITY_DIAGNOSTICS = ROOT / 'common/src/main/java/dev/vitrail/render/EntityInputDiagnostics.java'


class EntityReferenceDefaultTest(unittest.TestCase):
    def test_midblock_stays_out_of_real_entity_vertex_answers(self):
        vertex = ENTITY_VERTEX.read_text(encoding='utf-8')

        self.assertIn(
            'public static final Set<String> ANSWERED = Set.of("mc_Entity", "mc_midTexCoord", "at_tangent");',
            vertex,
        )
        self.assertNotIn(
            'public static final Set<String> ANSWERED = Set.of("mc_Entity", "mc_midTexCoord", "at_tangent", "at_midBlock");',
            vertex,
        )
        self.assertIn(
            'public static final List<String> APPENDED = List.of(IDENTIFIERS, MID_TEX_COORD, TANGENT);',
            vertex,
        )

    def test_midblock_is_reference_default_only_for_regular_entity_rows(self):
        diagnostics = ENTITY_DIAGNOSTICS.read_text(encoding='utf-8')
        program = ENTITY_PROGRAM.read_text(encoding='utf-8')

        self.assertIn(
            'private static final Set<String> REFERENCE_DEFAULTS = Set.of("at_midBlock");',
            diagnostics,
        )
        self.assertIn('Set<String> compatible = new HashSet<>(EntityVertex.ANSWERED);', diagnostics)
        self.assertIn('compatible.addAll(REFERENCE_DEFAULTS);', diagnostics)
        self.assertIn('if (element.glint() || element.text())', diagnostics)
        self.assertIn('if (element.lines())', diagnostics)
        self.assertIn('return LinesVertex.ANSWERED;', diagnostics)
        self.assertIn('return EntityInputDiagnostics.answered(element);', program)
        self.assertIn(
            'element.element(), NAMESPACE, answered(element), shadow,',
            program,
        )


if __name__ == '__main__':
    unittest.main()
