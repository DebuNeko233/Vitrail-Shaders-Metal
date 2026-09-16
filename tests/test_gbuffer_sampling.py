"""Lock PHASE 6 GBuffer sampling across one completed pass boundary."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-sampling-contract/shaders'
PACK_PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'


class GbufferSamplingTest(unittest.TestCase):
    def test_fixture_requires_a_real_cross_pass_sample(self):
        first = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        second = (FIXTURE / 'composite1.fsh').read_text(encoding='utf-8')
        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('RENDERTARGETS:4,5', first)
        self.assertNotIn('sampler2D colortex4', first)
        self.assertNotIn('sampler2D colortex5', first)
        self.assertIn('gl_FragData[0] = vec4(texcoord.x, 0.20, 0.40, 1.0);', first)
        self.assertIn('gl_FragData[1] = vec4(0.60, texcoord.y, 0.80, 1.0);', first)

        self.assertIn('uniform sampler2D colortex4;', second)
        self.assertIn('uniform sampler2D colortex5;', second)
        self.assertIn('texture2D(colortex4, texcoord)', second)
        self.assertIn('texture2D(colortex5, texcoord)', second)
        self.assertIn('RENDERTARGETS:6,7', second)
        self.assertNotIn('RENDERTARGETS:4', second)
        self.assertNotIn('RENDERTARGETS:5', second)
        for signature in (
            'closeEnough(source4.r, texcoord.x)',
            'closeEnough(source4.g, 0.20)',
            'closeEnough(source4.b, 0.40)',
            'closeEnough(source5.r, 0.60)',
            'closeEnough(source5.g, texcoord.y)',
            'closeEnough(source5.b, 0.80)',
        ):
            self.assertIn(signature, second)
        self.assertGreaterEqual(second.count('vec4(1.0, 0.0, 1.0, 1.0)'), 2)

        self.assertIn('uniform sampler2D colortex6;', final)
        self.assertIn('uniform sampler2D colortex7;', final)
        self.assertIn('texture2D(colortex6, texcoord)', final)
        self.assertIn('texture2D(colortex7, texcoord)', final)
        self.assertNotIn('colortex4', final)
        self.assertNotIn('colortex5', final)

    def test_pack_pass_binds_scheduled_colortex_surface_before_draw(self):
        pack = PACK_PASS.read_text(encoding='utf-8')

        bindings = pack.index(
            'this.samplerBindings = this.samplers.stream().map(loaded.samplers()::binding).toList();'
        )
        bind_call = pack.index('bindSamplers(pass, targets, depthView, distantView);')
        draw = pack.index('pass.draw(VERTICES, 1, 0, 0);', bind_call)
        surface = pack.index(
            'TargetSurface surface = binding.kind() == SamplerPlan.Kind.COLORTEX', bind_call
        )
        scheduled = pack.index('targets.surface(binding.index(), binding.side())', surface)
        colortex = pack.index('case COLORTEX -> surface == null ? null : surface.view();', scheduled)
        texture = pack.index(
            'pass.bindTexture(sampler, bound == null ? targets.black() : bound,', colortex
        )

        self.assertLess(bindings, bind_call)
        self.assertLess(bind_call, draw)
        self.assertLess(surface, scheduled)
        self.assertLess(scheduled, colortex)
        self.assertLess(colortex, texture)

    def test_each_fullscreen_draw_owns_a_render_pass_boundary(self):
        pack = PACK_PASS.read_text(encoding='utf-8')

        descriptor = pack.index('RenderPassDescriptor descriptor = RenderPassDescriptor.create(this.label);')
        open_pass = pack.index('try (RenderPass pass = encoder.createRenderPass(descriptor)) {', descriptor)
        record = pack.index('record(pass, targets, depthView, distantView, quad, uniforms);', open_pass)
        method_end = pack.index('\n\t}\n', record)

        self.assertLess(descriptor, open_pass)
        self.assertLess(open_pass, record)
        self.assertGreater(method_end, record)


if __name__ == '__main__':
    unittest.main()
