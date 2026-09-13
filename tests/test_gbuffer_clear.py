"""Lock PHASE 6 GBuffer clear policy and per-target clear plumbing."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-clear-contract/shaders'
TARGET_FORMAT = ROOT / 'common/src/main/java/dev/vitrail/pack/model/TargetFormat.java'
CONST_DIRECTIVES = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ConstDirectives.java'
TARGET_DIRECTIVES = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetDirectives.java'
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

TARGET_NAME = '''package dev.vitrail.pack.model;
import java.util.Optional;
import java.util.OptionalInt;
public final class TargetName {
    public record Suffixed(int index, String suffix) {}
    private TargetName() {}
    public static Optional<Suffixed> split(String name) {
        if (!name.startsWith("colortex")) return Optional.empty();
        int at = "colortex".length();
        int end = at;
        while (end < name.length() && Character.isDigit(name.charAt(end))) end++;
        if (end == at) return Optional.empty();
        return Optional.of(new Suffixed(Integer.parseInt(name.substring(at, end)), name.substring(end)));
    }
    public static OptionalInt index(String name) {
        if (!name.startsWith("colortex")) return OptionalInt.empty();
        String tail = name.substring("colortex".length());
        if (tail.isEmpty()) return OptionalInt.empty();
        for (int i = 0; i < tail.length(); i++) if (!Character.isDigit(tail.charAt(i))) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(tail));
    }
    public static String canonical(int index) { return "colortex" + index; }
}
'''

TARGET_SIZE = '''package dev.vitrail.pack.model;
import java.util.Map;
import java.util.Optional;
public final class TargetSize {
    private TargetSize() {}
    public static Optional<TargetSize> parse(String value, Map<String, String> defines) { return Optional.empty(); }
    public static TargetSize ofScreen() { return new TargetSize(); }
}
'''

SHADER_PROPERTIES = '''package dev.vitrail.pack.source;
import java.util.Map;
public final class ShaderProperties {
    public Map<String, String> sizeBuffers(Map<String, String> defines) { return Map.of(); }
}
'''

HARNESS = '''package dev.vitrail.pack.target;
import dev.vitrail.pack.source.IncludeExpander;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
public final class GbufferClearCheck {
    public static void main(String[] args) throws Exception {
        var unit = new IncludeExpander.ExpandedUnit(Files.readAllLines(Path.of(args[0])));
        var directives = TargetDirectives.builder().accept("composite.fsh", unit).build();
        for (int index = 4; index <= 7; index++) {
            var c = directives.clearColour(index);
            System.out.printf(Locale.ROOT, "%d:%s:%.1f,%.1f,%.1f,%.1f%n",
                    index, directives.clears(index), c.r(), c.g(), c.b(), c.a());
        }
        var noClear = TargetDirectives.builder().accept("synthetic.fsh",
                new IncludeExpander.ExpandedUnit(List.of("const bool colortex8Clear = false;"))).build();
        System.out.println("8:" + noClear.clears(8));
    }
}
'''


class GbufferClearTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-gbuffer-clear-')
        root = Path(cls.temp.name)

        def write(path, text):
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            return str(target)

        sources = [
            write('dev/vitrail/pack/model/TargetFormat.java', TARGET_FORMAT.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/model/TargetName.java', TARGET_NAME),
            write('dev/vitrail/pack/model/TargetSize.java', TARGET_SIZE),
            write('dev/vitrail/pack/source/IncludeExpander.java', INCLUDE_EXPANDER),
            write('dev/vitrail/pack/source/ShaderProperties.java', SHADER_PROPERTIES),
            write('dev/vitrail/pack/target/ConstDirectives.java', CONST_DIRECTIVES.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/target/TargetDirectives.java', TARGET_DIRECTIVES.read_text(encoding='utf-8')),
            write('dev/vitrail/pack/target/GbufferClearCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_fixture_declares_independent_clear_colours_and_policy(self):
        result = subprocess.run([
            'java', '-cp', str(self.classes),
            'dev.vitrail.pack.target.GbufferClearCheck',
            str(FIXTURE / 'composite.fsh'),
        ], check=True, capture_output=True, text=True)
        self.assertEqual([
            '4:true:1.0,0.0,0.0,1.0',
            '5:true:0.0,1.0,0.0,1.0',
            '6:true:0.0,0.0,1.0,1.0',
            '7:true:1.0,1.0,1.0,1.0',
            '8:false',
        ], result.stdout.strip().splitlines())

        composite = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        self.assertIn('RENDERTARGETS:4,5', composite)
        self.assertIn('discard;', composite)
        for target in range(4, 8):
            self.assertIn(f'const bool colortex{target}Clear = true;', composite)
            self.assertIn(f'const vec4 colortex{target}ClearColor', composite)

        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        for target in range(4, 8):
            self.assertIn(f'uniform sampler2D colortex{target};', final)
            self.assertIn(f'texture2D(colortex{target}, texcoord)', final)

    def test_runtime_routes_clear_debt_by_target_and_attachment(self):
        targets = COLOR_TARGETS.read_text(encoding='utf-8')
        pack = PACK_PASS.read_text(encoding='utf-8')

        self.assertIn('if (!full && !this.plan.directives().clears(index)) {', targets)
        self.assertIn(': this.clearColours.get(index);', targets)
        self.assertIn('defer(this.mainSide.get(index), colour);', targets)
        self.assertIn('defer(this.altSide.get(index), colour);', targets)
        self.assertIn('Vector4fc colour = this.pendingClears.remove(view.texture());', targets)
        self.assertIn('descriptor.withColorAttachment(one.view(), Optional.of(one.colour()));', targets)

        load_clear = pack.index('descriptor.withColorAttachment(view, targets.takeClear(view));')
        flush_check = pack.index('if (targets.hasPendingClears()) {', load_clear)
        flush = pack.index('targets.flushPending(encoder);', flush_check)
        open_pass = pack.index('encoder.createRenderPass(descriptor)', flush)
        self.assertLess(load_clear, flush_check)
        self.assertLess(flush_check, flush)
        self.assertLess(flush, open_pass)


if __name__ == '__main__':
    unittest.main()
