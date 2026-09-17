"""Lock pack-local macro redefinition normalisation against strict GLSL preprocessors."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXPANDER = ROOT / 'common/src/main/java/dev/vitrail/pack/source/IncludeExpander.java'


class MacroRedefinitionContractTest(unittest.TestCase):
    def test_repeated_pack_macro_is_undefined_before_later_definition(self):
        source = EXPANDER.read_text(encoding='utf-8')
        track = source.split('private String track(String line, State state)', 1)[1].split(
            '/**\n\t * One flattened unit', 1
        )[0]

        self.assertIn('private final Set<String> sourceDefines = new HashSet<>();', source)
        self.assertIn('state.sourceDefines.remove(name);', track)
        self.assertIn('if (!state.sourceDefines.add(name))', track)
        self.assertIn('state.emit("#undef " + name, true);', track)
        self.assertNotIn('diagonal3', track)

    def test_environment_defines_are_not_blanket_undefined(self):
        source = EXPANDER.read_text(encoding='utf-8')
        track = source.split('private String track(String line, State state)', 1)[1].split(
            '/**\n\t * One flattened unit', 1
        )[0]
        self.assertIn('Only names first defined by this pack are normalised here', track)
        self.assertNotIn('state.defines.containsKey(name)', track)


if __name__ == '__main__':
    unittest.main()
