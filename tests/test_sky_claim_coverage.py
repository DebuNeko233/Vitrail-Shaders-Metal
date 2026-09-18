"""Lock sky ownership to mesh claim rather than pack fragment survival."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
OWNERSHIP = ROOT / 'common/src/main/java/dev/vitrail/render/SkyOwnership.java'
TRANSLATOR = ROOT / 'common/src/main/java/dev/vitrail/glsl/GlslTranslator.java'
GEOMETRY = ROOT / 'common/src/main/java/dev/vitrail/render/GeometryProgram.java'
EMITTER = ROOT / 'common/src/main/java/dev/vitrail/glsl/Emitter.java'
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
        self.assertIn('method = {"renderDarkDisc", "renderSunriseAndSunset"}', mixin)
        self.assertIn('require = 2,', mixin)
        self.assertIn('target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(IIII)V"', mixin)
        self.assertIn('method = {"renderStars", "renderSun", "renderMoon", "renderEndSky", "renderEndFlash"}', mixin)
        self.assertIn('require = 5,', mixin)
        self.assertIn('target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIIII)V"', mixin)
        self.assertIn('SkyOwnership.claimIndexed(pass, this.vitrail$pipeline, indices, instances, firstIndex,', mixin)
        self.assertIn('pass.drawIndexed(indices, instances, firstIndex, vertexOffset, firstInstance);', ownership)

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

    def test_zero_output_fragment_can_reserve_rank_zero_for_coverage(self):
        translator = TRANSLATOR.read_text(encoding='utf-8')
        geometry = GEOMETRY.read_text(encoding='utf-8')
        emitter = EMITTER.read_text(encoding='utf-8')

        plan = translator.split('private void planCoverage()', 1)[1].split(
            'private boolean wrapsFragment()', 1
        )[0]
        self.assertNotIn('this.maxFragmentOutput < 0', plan)
        self.assertIn('this.maxFragmentOutput + 1 >= MAX_FRAGMENT_OUTPUTS', plan)
        self.assertIn(
            '(notes.fragmentOutputs() == 0 || attachments <= notes.fragmentOutputs())',
            geometry,
        )
        self.assertIn('this.extra = this.covers && outputs == 0', geometry)
        self.assertIn(
            'layout(location = " + (this.maxFragmentOutput + 1) + ") out float',
            emitter,
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
