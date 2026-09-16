"""Keep the developer MRT shader-pack fixture tied to Vitrail's real routing contract."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/mrt-contract/shaders'
DRAW_BUFFERS = ROOT / 'common/src/main/java/dev/vitrail/pack/target/DrawBuffers.java'
GEOMETRY_PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/GeometryProgram.java'

TARGET_NAME = '''package dev.vitrail.pack.model;
public final class TargetName {
    public static final int MAX_TARGETS = 32;
    private TargetName() {}
}
'''

INCLUDE_EXPANDER = '''package dev.vitrail.pack.source;
import java.util.List;
public final class IncludeExpander {
    private IncludeExpander() {}
    public static final class ExpandedUnit {
        private final List<String> lines;
        public ExpandedUnit(List<String> lines) { this.lines = List.copyOf(lines); }
        public List<String> lines() { return this.lines; }
        public boolean isLive(int line) { return true; }
    }
}
'''

HARNESS = '''package dev.vitrail.pack.target;
import dev.vitrail.pack.source.IncludeExpander;
import java.nio.file.Files;
import java.nio.file.Path;
public final class DrawBuffersFixtureCheck {
    public static void main(String[] args) throws Exception {
        var unit = new IncludeExpander.ExpandedUnit(Files.readAllLines(Path.of(args[0])));
        String actual = DrawBuffers.parse(unit).toString();
        if (!actual.equals(args[1])) {
            throw new AssertionError(args[0] + " parsed as " + actual + ", expected " + args[1]);
        }
    }
}
'''


class MrtSmokeFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-mrt-fixture-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/pack/target/DrawBuffers.java', DRAW_BUFFERS.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/model/TargetName.java', TARGET_NAME),
            write('dev/vitrail/pack/source/IncludeExpander.java', INCLUDE_EXPANDER),
            write('dev/vitrail/pack/target/DrawBuffersFixtureCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def parse_fixture(self, name, expected):
        subprocess.run([
            'java', '-cp', str(self.classes),
            'dev.vitrail.pack.target.DrawBuffersFixtureCheck',
            str(FIXTURE / name), expected,
        ], check=True, capture_output=True, text=True)

    def test_terrain_fixture_forces_two_unused_output_ranks(self):
        fragment = (FIXTURE / 'gbuffers_terrain.fsh').read_text(encoding='utf-8')
        self.parse_fixture('gbuffers_terrain.fsh', '[0]')
        ranks = sorted({int(rank) for rank in re.findall(
            r'gl_FragData\s*\[\s*(\d+)\s*\]\s*=', fragment)})
        self.assertEqual([0, 1, 2], ranks)
        self.assertNotIn('DRAWBUFFERS:02', fragment)

        geometry = GEOMETRY_PROGRAM.read_text(encoding='utf-8')
        self.assertIn('while (built.size() < outputs)', geometry)
        self.assertIn('new Slot(Bound.UNUSED, null, null)', geometry)
        self.assertIn('builder.withUnusedColorTargetState(slot)', geometry)
        self.assertIn('this.attachedViews.add(null)', geometry)

    def test_fullscreen_fixture_is_four_target_mrt(self):
        fragment = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        self.parse_fixture('composite.fsh', '[0, 1, 2, 3]')
        ranks = sorted({int(rank) for rank in re.findall(
            r'gl_FragData\s*\[\s*(\d+)\s*\]\s*=', fragment)})
        self.assertEqual([0, 1, 2, 3], ranks)

        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        for target in range(4):
            self.assertIn(f'uniform sampler2D colortex{target};', final)
            self.assertIn(f'texture2D(colortex{target}, texcoord)', final)


if __name__ == '__main__':
    unittest.main()
