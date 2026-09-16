"""Lock PHASE 6 GBuffer target formats from pack directive to device attachment."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-format-contract/shaders'
TARGET_FORMAT = ROOT / 'common/src/main/java/dev/vitrail/pack/model/TargetFormat.java'
CONST_DIRECTIVES = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ConstDirectives.java'
GPU_FORMATS = ROOT / 'common/src/main/java/dev/vitrail/render/GpuFormats.java'
COLOR_TARGETS = ROOT / 'common/src/main/java/dev/vitrail/render/ColorTargets.java'
PACK_PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'

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
import dev.vitrail.pack.model.TargetFormat;
import java.nio.file.Files;
import java.nio.file.Path;
public final class GbufferFormatCheck {
    public static void main(String[] args) throws Exception {
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            var read = ConstDirectives.readLine(line);
            if (read.isEmpty() || !read.get().name().endsWith("Format")) continue;
            var directive = read.get();
            var resolved = TargetFormat.resolve(directive.value());
            System.out.println(directive.name() + "=" + resolved.used() + ":" + resolved.reason());
        }
    }
}
'''


class GbufferFormatTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-gbuffer-format-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/pack/model/TargetFormat.java', TARGET_FORMAT.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/target/ConstDirectives.java', CONST_DIRECTIVES.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/source/IncludeExpander.java', INCLUDE_EXPANDER),
            write('dev/vitrail/pack/target/GbufferFormatCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_fixture_declares_four_distinguishable_formats(self):
        result = subprocess.run([
            'java', '-cp', str(self.classes),
            'dev.vitrail.pack.target.GbufferFormatCheck',
            str(FIXTURE / 'composite.fsh'),
        ], check=True, capture_output=True, text=True)
        self.assertEqual([
            'colortex4Format=R8_UNORM:EXACT',
            'colortex5Format=RG8_UNORM:EXACT',
            'colortex6Format=RGBA16_FLOAT:EXACT',
            'colortex7Format=RGB10A2_UNORM:EXACT',
        ], result.stdout.strip().splitlines())

        fragment = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        self.assertIn('RENDERTARGETS:4,5,6,7', fragment)
        self.assertIn('vec4(0.5005, 0.25, 0.75, 0.25)', fragment)
        self.assertIn('vec4(0.20, 0.40, 0.60, 0.51)', fragment)

        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        for target in range(4, 8):
            self.assertIn(f'uniform sampler2D colortex{target};', final)
            self.assertIn(f'texture2D(colortex{target}, texcoord)', final)
        self.assertIn('abs(rgba16f.r - 0.5005) < 0.0005', final)
        self.assertIn('abs(rgb10a2.a - 0.6666667) < 0.02', final)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', final)

    def test_declared_format_drives_texture_and_pipeline_format(self):
        gpu = GPU_FORMATS.read_text(encoding='utf-8')
        targets = COLOR_TARGETS.read_text(encoding='utf-8')
        pack = PACK_PASS.read_text(encoding='utf-8')

        for mapping in (
            'case R8_UNORM -> GpuFormat.R8_UNORM;',
            'case RG8_UNORM -> GpuFormat.RG8_UNORM;',
            'case RGBA16_FLOAT -> GpuFormat.RGBA16_FLOAT;',
            'case RGB10A2_UNORM -> GpuFormat.RGB10A2_UNORM;',
        ):
            self.assertIn(mapping, gpu)

        self.assertIn('TargetFormat format = directives.format(index).used();', targets)
        self.assertIn('this.formats.put(index, GpuFormats.of(format));', targets)
        self.assertIn('GpuFormat format = this.formats.get(index);', targets)
        self.assertIn('new TargetSurface("Vitrail " + name, format, mipped, storage, width,', targets)

        self.assertIn('int index = this.attachments.get(slot).target();', pack)
        self.assertIn('GpuFormat format = targets.format(index);', pack)
        self.assertIn('builder.withColorTargetState(slot, new ColorTargetState(', pack)


if __name__ == '__main__':
    unittest.main()
