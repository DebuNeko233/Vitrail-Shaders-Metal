"""Lock PHASE 6 GBuffer output-location routing to draw-buffer rank."""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-location-contract/shaders'
DRAW_BUFFERS = ROOT / 'common/src/main/java/dev/vitrail/pack/target/DrawBuffers.java'
CHAIN_PLAN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ChainPlan.java'
PACK_PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'

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
public final class GbufferLocationCheck {
    public static void main(String[] args) throws Exception {
        var unit = new IncludeExpander.ExpandedUnit(Files.readAllLines(Path.of(args[0])));
        String actual = DrawBuffers.parse(unit).toString();
        if (!actual.equals(args[1])) {
            throw new AssertionError(args[0] + " parsed as " + actual + ", expected " + args[1]);
        }
    }
}
'''


class GbufferAttachmentLocationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='vitrail-gbuffer-location-')
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
            write('dev/vitrail/pack/target/GbufferLocationCheck.java', HARNESS),
        ]
        cls.classes = root / 'classes'
        subprocess.run(['javac', '-d', str(cls.classes), *sources], check=True,
                       capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_fixture_uses_nonidentity_target_numbers_in_output_rank_order(self):
        fragment = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        subprocess.run([
            'java', '-cp', str(self.classes),
            'dev.vitrail.pack.target.GbufferLocationCheck',
            str(FIXTURE / 'composite.fsh'), '[4, 1, 7, 2]',
        ], check=True, capture_output=True, text=True)

        ranks = [int(rank) for rank in re.findall(
            r'gl_FragData\s*\[\s*(\d+)\s*\]\s*=', fragment)]
        self.assertEqual([0, 1, 2, 3], ranks)
        self.assertIn('RENDERTARGETS:4,1,7,2', fragment)

        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        for target in (4, 1, 7, 2):
            self.assertIn(f'uniform sampler2D colortex{target};', final)
            self.assertIn(f'texture2D(colortex{target}, texcoord)', final)

    def test_chain_and_fullscreen_pass_preserve_directive_rank(self):
        chain = CHAIN_PLAN.read_text(encoding='utf-8')
        pack = PACK_PASS.read_text(encoding='utf-8')

        # ChainPlan walks TargetPlan.writes(program) in list order and appends one attachment per
        # entry. The colortex number is payload; it is not a native attachment location.
        walk = chain.index('for (int index : writes)')
        append = chain.index('attachments.add(new Attachment(index, bound.get().write(index)))', walk)
        self.assertLess(walk, append)

        # PackPass assigns pipeline colour state by the attachment's RANK, while using target() only
        # to resolve the format. Thus RENDERTARGETS:4,1,7,2 still occupies native locations 0..3.
        self.assertIn('for (int slot = 0; slot < this.attachments.size(); slot++)', pack)
        self.assertIn('int index = this.attachments.get(slot).target();', pack)
        self.assertIn('builder.withColorTargetState(slot, new ColorTargetState(', pack)
        self.assertIn('targets.blend(program, slot)', pack)
        self.assertNotIn('targets.blend(program), Optional.empty()', pack)

        # Descriptor views are emitted by the same attachment list order, keeping pipeline slot and
        # render-pass attachment location aligned.
        self.assertIn('for (ChainPlan.Attachment attachment : this.attachments)', pack)
        self.assertIn('this.attachedViews.add(view(targets, attachment));', pack)
        self.assertIn('for (GpuTextureView view : this.attachedViews)', pack)
        # The location an attachment is handed in at is still the list's order, whatever action the
        # pass asks for beside it, so a pipeline slot and an attachment location stay aligned.
        self.assertIn('descriptor.withColorAttachment(view,', pack)
        self.assertIn(': targets.takeClear(view));', pack)


if __name__ == '__main__':
    unittest.main()
