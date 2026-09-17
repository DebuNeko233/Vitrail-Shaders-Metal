"""Lock sky ownership to mesh claim rather than pack fragment survival."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
OWNERSHIP = ROOT / 'common/src/main/java/dev/vitrail/render/SkyOwnership.java'
SKY_PROGRAM = ROOT / 'common/src/main/java/dev/vitrail/render/SkyProgram.java'
HORIZON = ROOT / 'common/src/main/java/dev/vitrail/render/HorizonCone.java'
MIXIN = ROOT / 'common/src/main/java/dev/vitrail/mixin/SkyRendererMixin.java'


class SkyClaimCoverageTest(unittest.TestCase):
    def test_claim_reuses_pack_vertex_and_writes_only_coverage(self):
        ownership = OWNERSHIP.read_text(encoding='utf-8')
        program = SKY_PROGRAM.read_text(encoding='utf-8')

        self.assertIn('bound.program().stages().get(ProgramStage.VERTEX).text()', program)
        self.assertIn('SkyOwnership.prepare(device, pipeline, this.claimVertex, this.body.covers());', program)
        self.assertIn('Identifier vertexId = owner.getVertexShader();', ownership)
        self.assertIn('.withVertexShader(vertexId)', ownership)
        self.assertIn('.withFragmentShader(fragmentId)', ownership)
        self.assertIn('builder.withUnusedColorTargetState(slot);', ownership)
        self.assertIn('builder.withColorTargetState(coverage, states[coverage]);', ownership)
        self.assertIn('ofCoverage = gl_FragCoord.z;', ownership)
        self.assertIn('vertexId.equals(id) ? vertex : null', ownership)
        self.assertNotIn('owner.getFragmentShader().equals(id)', ownership)

    def test_claim_is_sky_only_and_restores_pack_pipeline(self):
        ownership = OWNERSHIP.read_text(encoding='utf-8')
        mixin = MIXIN.read_text(encoding='utf-8')

        self.assertIn('public final class SkyOwnership', ownership)
        self.assertIn('pass.setPipeline(claim.pipeline);', ownership)
        self.assertIn('pass.setPipeline(owner);', ownership)
        self.assertIn('SkyOwnership.claim(pass, this.vitrail$pipeline, vertices, instances, firstVertex,', mixin)
        self.assertIn('method = {"renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",', mixin)
        self.assertIn('"renderMoon", "renderEndSky", "renderEndFlash"}', mixin)
        self.assertIn('require = 7,', mixin)

    def test_disc_and_horizon_both_replay_claimed_geometry(self):
        mixin = MIXIN.read_text(encoding='utf-8')
        horizon = HORIZON.read_text(encoding='utf-8')

        disc = mixin.split('private void vitrail$horizon', 1)[1].split('\n\t}', 1)[0]
        self.assertIn('SkyOwnership.claim(pass, this.vitrail$pipeline, vertices, instances, firstVertex,', disc)
        self.assertIn('SkyOwnership.withOwner(this.vitrail$pipeline,', disc)
        self.assertIn('() -> SkyDraw.horizon(pass, this.vitrail$pipeline)', disc)
        self.assertIn('pass.draw(VERTICES, 1, 0, 0);', horizon)
        self.assertIn('SkyOwnership.claimCurrent(pass, VERTICES, 1, 0, 0);', horizon)
        self.assertLess(
            horizon.index('pass.draw(VERTICES, 1, 0, 0);'),
            horizon.index('SkyOwnership.claimCurrent(pass, VERTICES, 1, 0, 0);'),
        )

    def test_geometry_stage_and_comparison_metadata_follow_claim_pipeline(self):
        ownership = OWNERSHIP.read_text(encoding='utf-8')

        self.assertIn('GeometryStage.noteBeside(pipeline, owner);', ownership)
        self.assertIn('ShadowCompare.noteBeside(pipeline, owner);', ownership)
        self.assertIn('device.precompilePipeline(this.pipeline, this.source)', ownership)
        self.assertIn('catch (GpuDeviceLossException e)', ownership)
        self.assertIn('claim = Claim.failed();', ownership)


if __name__ == '__main__':
    unittest.main()
