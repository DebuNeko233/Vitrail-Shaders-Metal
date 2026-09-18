"""Lock PHASE 6 GBuffer colour writes from fragment outputs to concrete attachments."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-write-contract/shaders'
PACK_PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'


class GbufferWriteTest(unittest.TestCase):
    def test_fixture_writes_four_spatial_channel_signatures(self):
        composite = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        self.assertIn('RENDERTARGETS:4,5,6,7', composite)
        for target in range(4, 8):
            self.assertIn(f'const bool colortex{target}Clear = true;', composite)
            self.assertIn(
                f'const vec4 colortex{target}ClearColor = vec4(0.05, 0.05, 0.05, 1.0);',
                composite,
            )

        expected_writes = (
            'gl_FragData[0] = vec4(0.80, 0.20, texcoord.x, 0.40);',
            'gl_FragData[1] = vec4(0.20, 0.80, texcoord.y, 0.60);',
            'gl_FragData[2] = vec4(texcoord.x, 0.20, 0.80, 0.70);',
            'gl_FragData[3] = vec4(0.90, texcoord.y, 0.10, 0.80);',
        )
        for write in expected_writes:
            self.assertIn(write, composite)

        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        for target in range(4, 8):
            self.assertIn(f'uniform sampler2D colortex{target};', final)
            self.assertIn(f'texture2D(colortex{target}, texcoord)', final)
        for signature in (
            'closeEnough(w4.b, texcoord.x)',
            'closeEnough(w5.b, texcoord.y)',
            'closeEnough(w6.r, texcoord.x)',
            'closeEnough(w7.g, texcoord.y)',
        ):
            self.assertIn(signature, final)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', final)

    def test_fullscreen_pass_uses_write_all_and_draws_into_same_attachment_list(self):
        pack = PACK_PASS.read_text(encoding='utf-8')

        state = pack.index('builder.withColorTargetState(slot, new ColorTargetState(')
        write_all = pack.index('ColorTargetState.WRITE_ALL));', state)
        self.assertLess(state, write_all)

        build_views = pack.index('for (ChainPlan.Attachment attachment : this.attachments) {')
        resolve_view = pack.index('this.attachedViews.add(view(targets, attachment));', build_views)
        descriptor = pack.index('RenderPassDescriptor descriptor = RenderPassDescriptor.create(this.label);', resolve_view)
        attach = pack.index('descriptor.withColorAttachment(view, emptyInsteadOfLoad', descriptor)
        open_pass = pack.index('encoder.createRenderPass(descriptor)', attach)
        set_pipeline = pack.index('pass.setPipeline(this.pipeline);', open_pass)
        draw = pack.index('pass.draw(VERTICES, 1, 0, 0);', set_pipeline)

        self.assertLess(build_views, resolve_view)
        self.assertLess(resolve_view, descriptor)
        self.assertLess(descriptor, attach)
        self.assertLess(attach, open_pass)
        self.assertLess(open_pass, set_pipeline)
        self.assertLess(set_pipeline, draw)


if __name__ == '__main__':
    unittest.main()
