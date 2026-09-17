"""Lock reference-unbacked vertex diagnostics without changing mesh ABIs."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
ENTITY_VERTEX = ROOT / 'common/src/main/java/dev/vitrail/glsl/EntityVertex.java'
ENTITY_BRIDGE = ROOT / 'common/src/main/java/dev/vitrail/render/EntityInputDiagnostics.java'
DIAGNOSTICS = ROOT / 'common/src/main/java/dev/vitrail/render/VertexInputDiagnostics.java'
PARTICLE = ROOT / 'common/src/main/java/dev/vitrail/render/ParticleProgram.java'
WEATHER = ROOT / 'common/src/main/java/dev/vitrail/render/WeatherProgram.java'


class ReferenceVertexInputDiagnosticsTest(unittest.TestCase):
    def test_entity_midblock_stays_out_of_real_entity_vertex_abi(self):
        vertex = ENTITY_VERTEX.read_text(encoding='utf-8')
        diagnostics = DIAGNOSTICS.read_text(encoding='utf-8')
        bridge = ENTITY_BRIDGE.read_text(encoding='utf-8')

        self.assertIn(
            'public static final Set<String> ANSWERED = Set.of("mc_Entity", "mc_midTexCoord", "at_tangent");',
            vertex,
        )
        self.assertIn(
            'public static final List<String> APPENDED = List.of(IDENTIFIERS, MID_TEX_COORD, TANGENT);',
            vertex,
        )
        entity_answers = vertex.split('public static final Set<String> ANSWERED =', 1)[1].split(';', 1)[0]
        self.assertNotIn('"at_midBlock"', entity_answers)
        self.assertIn(
            'private static final Set<String> ENTITY_REFERENCE_DEFAULTS = Set.of("at_midBlock");',
            diagnostics,
        )
        self.assertIn(
            'return new Inputs(EntityVertex.ANSWERED, ENTITY_REFERENCE_DEFAULTS);', diagnostics
        )
        self.assertIn(
            'return VertexInputDiagnostics.entity(element).diagnosticAnswered();', bridge
        )

    def test_reference_defaults_are_separate_from_real_answers(self):
        diagnostics = DIAGNOSTICS.read_text(encoding='utf-8')

        self.assertIn(
            'record Inputs(Set<String> answered, Set<String> referenceDefaults)', diagnostics
        )
        self.assertIn('Set<String> diagnosticAnswered()', diagnostics)
        self.assertIn('Set<String> accepted = new HashSet<>(this.answered);', diagnostics)
        self.assertIn('accepted.addAll(this.referenceDefaults);', diagnostics)
        self.assertIn('does not claim value-for-value parity', diagnostics)
        self.assertIn('Vitrail supplies deterministic synthesized', diagnostics)

    def test_particle_and_weather_use_reference_unbacked_extensions_only_for_diagnostics(self):
        diagnostics = DIAGNOSTICS.read_text(encoding='utf-8')
        particle = PARTICLE.read_text(encoding='utf-8')
        weather = WEATHER.read_text(encoding='utf-8')

        self.assertIn(
            'Set.of("mc_Entity", "mc_midTexCoord", "at_tangent");', diagnostics
        )
        self.assertIn('return new Inputs(Set.of(), PARTICLE_REFERENCE_DEFAULTS);', diagnostics)
        self.assertIn(
            'private static final VertexInputDiagnostics.Inputs INPUTS = VertexInputDiagnostics.particle();',
            particle,
        )
        self.assertIn(
            'private static final VertexInputDiagnostics.Inputs INPUTS = VertexInputDiagnostics.weather();',
            weather,
        )
        self.assertIn('NAMESPACE, INPUTS.diagnosticAnswered(), false,', particle)
        self.assertIn('NAMESPACE, INPUTS.diagnosticAnswered(), false,', weather)
        self.assertNotIn('private static final Set<String> ANSWERED', particle)
        self.assertNotIn('private static final Set<String> ANSWERED', weather)


if __name__ == '__main__':
    unittest.main()
