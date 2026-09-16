"""Lock the remaining PHASE 10 Deferred MRT and mipmap acceptance contracts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'tests/fixtures/shaderpacks'
PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'
CHAIN = ROOT / 'common/src/main/java/dev/vitrail/render/PackChain.java'
DIRECTIVES = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetDirectives.java'

class DeferredTailContractTest(unittest.TestCase):
    def shader(self, fixture, name):
        return (FIXTURES / fixture / 'shaders' / name).read_text(encoding='utf-8')

    def exact(self, fixture):
        root = FIXTURES / fixture / 'shaders'
        self.assertEqual({p.name for p in root.iterdir()}, {
            'deferred.vsh','deferred.fsh','deferred1.vsh','deferred1.fsh',
            'deferred2.vsh','deferred2.fsh','final.vsh','final.fsh'})

    def test_mrt_fixture_carries_two_targets_through_all_deferred_passes(self):
        self.exact('deferred-mrt-contract')
        for name in ('deferred.fsh','deferred1.fsh','deferred2.fsh'):
            source=self.shader('deferred-mrt-contract',name)
            self.assertIn('/* DRAWBUFFERS:01 */',source)
            self.assertIn('gl_FragData[0]',source)
            self.assertIn('gl_FragData[1]',source)
        for name in ('deferred1.fsh','deferred2.fsh','final.fsh'):
            source=self.shader('deferred-mrt-contract',name)
            self.assertIn('uniform sampler2D colortex0;',source)
            self.assertIn('uniform sampler2D colortex1;',source)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)',self.shader('deferred-mrt-contract','deferred2.fsh'))
        self.assertIn('texcoord.x < 0.5 ? left : right',self.shader('deferred-mrt-contract','final.fsh'))

    def test_mipmap_fixture_requires_two_reader_specific_rebuilds(self):
        self.exact('deferred-mipmap-contract')
        first=self.shader('deferred-mipmap-contract','deferred.fsh')
        second=self.shader('deferred-mipmap-contract','deferred1.fsh')
        third=self.shader('deferred-mipmap-contract','deferred2.fsh')
        final=self.shader('deferred-mipmap-contract','final.fsh')
        self.assertIn('gl_FragCoord.x',first)
        for source in (second,third):
            self.assertIn('const bool colortex0MipmapEnabled = true;',source)
            self.assertIn('texture2D(colortex0, texcoord, 6.0)',source)
        self.assertIn('vec4(0.0, checker, 0.0, 1.0)',second)
        self.assertIn('vec4(0.0, 1.0, 1.0, 1.0)',third)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)',third)
        self.assertIn('gl_FragColor = texture2D(colortex0, texcoord);',final)
        self.assertNotIn('vec4(0.0, 1.0, 1.0',final)

    def test_production_rebuilds_reader_half_and_invalidates_after_write(self):
        pack_pass=PASS.read_text(encoding='utf-8')
        chain=CHAIN.read_text(encoding='utf-8')
        directives=DIRECTIVES.read_text(encoding='utf-8')
        self.assertIn('case CLEAR, MIPMAP -> type.equals("bool")',directives)
        self.assertIn('this.mipmapRequests.computeIfAbsent',directives)
        self.assertIn('targets.lodReads(program)',pack_pass)
        self.assertIn('surface.chainWritten()',pack_pass)
        self.assertIn('for (PackPass.LodRead read : pass.lodReads())',chain)
        self.assertIn('MipmapReduction.generate(encoder, surface)',chain)
        self.assertIn('this.currentChains.remove(',chain)

if __name__ == '__main__':
    unittest.main()
