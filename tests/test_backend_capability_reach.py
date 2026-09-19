"""A capability this engine adds is asked of the backend, never of the game's wrapper.

The game hands out a `CommandEncoder` that is a new object every call and forwards to a
`CommandEncoderBackend`, which is where the command buffer lives and where every capability of this
engine is mixed in. So a capability asked of the wrapper compiles, runs and answers no for ever: the
check is not wrong code, it is code that can never be true.

That is not hypothetical. The store half of P1 and the storage boundary it narrowed were both asked of
the wrapper, and both were then measured as worth nothing - a reading that described the wrapper rather
than the mechanism. The rule this file pins is what stops that from being re-learned: a capability check
whose receiver is an encoder resolves it through the accessor first, and the accessor is the only door.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'common/src/main/java'
ACCESSOR = SOURCES / 'dev/vitrail/mixin/access/CommandEncoderAccessor.java'
BACKENDS = SOURCES / 'dev/vitrail/render/Backends.java'
BUILD = ROOT / '.github/workflows/build.yml'

CHECK = re.compile(r'(\w+)\s+instanceof\s+\w*Commands\b')


class CapabilityReach(unittest.TestCase):
    def test_every_capability_check_has_a_receiver_that_can_carry_one(self):
        offenders = []
        for path in sorted(SOURCES.rglob('*.java')):
            for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
                for receiver in CHECK.findall(line):
                    # `encoder` is the wrapper whenever a caller has one: that is the name every call
                    # site of this game uses for it, and it is the name that made the fault invisible.
                    if receiver == 'encoder' and 'Backends.encoder(' not in line:
                        offenders.append(f'{path.relative_to(ROOT)}:{number}: {line.strip()}')
        self.assertEqual([], offenders, 'a capability is asked of the wrapper:\n' + '\n'.join(offenders))

    def test_the_accessor_is_the_only_door_and_it_goes_to_the_backend(self):
        accessor = ACCESSOR.read_text(encoding='utf-8')
        self.assertIn('@Mixin(CommandEncoder.class)', accessor)
        self.assertIn('CommandEncoderBackend vitrail$backend();', accessor)
        backends = BACKENDS.read_text(encoding='utf-8')
        self.assertIn('return encoder instanceof CommandEncoderAccessor accessor ? accessor.vitrail$backend() : null;', backends)

    def test_the_resolver_says_why_it_exists(self):
        # The class comment is the only place a reader finds out what the fault was, and a later edit
        # that trims it is the edit that lets the fault come back.
        backends = BACKENDS.read_text(encoding='utf-8')
        self.assertIn('asked of the wrapper is a capability that is never there', backends)

    def test_this_file_is_named_by_the_workflow(self):
        self.assertIn('tests/test_backend_capability_reach.py', BUILD.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
