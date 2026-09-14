"""Lock PHASE 7's block-entity routing and carried entity vertex ABI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/block-entity-contract/shaders'
DISPATCHER = ROOT / 'common/src/main/java/dev/vitrail/mixin/BlockEntityRenderDispatcherMixin.java'
SUBMIT = ROOT / 'common/src/main/java/dev/vitrail/mixin/ModelSubmitMixin.java'
PREPARE = ROOT / 'common/src/main/java/dev/vitrail/mixin/ModelFeatureRendererMixin.java'
GROUP = ROOT / 'common/src/main/java/dev/vitrail/mixin/RenderTypeFeatureRendererGroupMixin.java'
EXECUTE = ROOT / 'common/src/main/java/dev/vitrail/mixin/RenderTypeFeatureRendererMixin.java'
BUFFER = ROOT / 'common/src/main/java/dev/vitrail/mixin/BufferBuilderMixin.java'
DRAW = ROOT / 'common/src/main/java/dev/vitrail/render/EntityDraw.java'


class BlockEntityFamilyTest(unittest.TestCase):
    def test_fixture_is_block_only_and_observes_carried_polygon_abi(self):
        vertex = (FIXTURE / 'gbuffers_block.vsh').read_text(encoding='utf-8')
        fragment = (FIXTURE / 'gbuffers_block.fsh').read_text(encoding='utf-8')
        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')
        self.assertIn('attribute vec2 mc_midTexCoord;', vertex)
        self.assertIn('attribute vec4 at_tangent;', vertex)
        self.assertIn('distance(mc_midTexCoord, texcoord)', vertex)
        self.assertIn('length(at_tangent.xyz)', vertex)
        self.assertIn('abs(at_tangent.w)', vertex)
        self.assertIn('vec4(0.0, 1.0, 0.0, 1.0)', fragment)
        self.assertIn('vec4(1.0, 0.0, 1.0, 1.0)', fragment)
        self.assertIn('uniform sampler2D colortex0;', final)
        names = {path.name for path in FIXTURE.iterdir()}
        self.assertIn('gbuffers_block.vsh', names)
        self.assertIn('gbuffers_block.fsh', names)
        self.assertFalse(any('entities' in name for name in names))
        self.assertFalse(any('spidereyes' in name for name in names))
        self.assertFalse(any('hand' in name for name in names))

    def test_submission_mark_and_identifier_are_captured(self):
        dispatcher = DISPATCHER.read_text(encoding='utf-8')
        submit = SUBMIT.read_text(encoding='utf-8')
        self.assertIn('BlockEntityGeometry.submitting(true);', dispatcher)
        self.assertIn('EntityIdentifiers.blockEntity(', dispatcher)
        self.assertIn('BlockStateIds.id(', dispatcher)
        self.assertIn('BlockEntityGeometry.submitting(false);', dispatcher)
        self.assertIn('EntityIdentifiers.blockEntity(0);', dispatcher)
        self.assertIn('this.vitrail$blockEntity = BlockEntityGeometry.submitting();', submit)
        self.assertIn('this.vitrail$identifiers = EntityIdentifiers.packed();', submit)

    def test_submission_origin_is_restored_for_vertex_build_and_kept_out_of_mob_draws(self):
        prepare = PREPARE.read_text(encoding='utf-8')
        group = GROUP.read_text(encoding='utf-8')
        self.assertIn('BlockEntityGeometry.building(((BlockEntityOrigin) (Object) submit).vitrail$fromBlockEntity());', prepare)
        self.assertIn('EntityIdentifiers.restore(((SubmittedIdentifiers) (Object) submit).vitrail$identifiers());', prepare)
        self.assertIn('BlockEntityGeometry.building(false);', prepare)
        self.assertIn('EntityIdentifiers.clear();', prepare)
        self.assertIn('!= BlockEntityGeometry.building()', group)
        self.assertIn('vitrail$fromBlockEntity(BlockEntityGeometry.building());', group)
        self.assertIn('== BlockEntityGeometry.building() ? found : -1;', group)

    def test_draw_origin_selects_block_program_and_stage(self):
        execute = EXECUTE.read_text(encoding='utf-8')
        draw = DRAW.read_text(encoding='utf-8')
        self.assertIn('BlockEntityGeometry.drawing(((BlockEntityOrigin) draw).vitrail$fromBlockEntity());', execute)
        self.assertIn('private static final String BLOCK = "gbuffers_block";', draw)
        self.assertIn('private static final String BLOCK_TRANSLUCENT = "gbuffers_block_translucent";', draw)
        self.assertIn('mob.blended() ? BLOCK_TRANSLUCENT : BLOCK, CUTOUT,', draw)
        self.assertIn('RenderStage.BLOCK_ENTITIES, false);', draw)

    def test_buffer_builder_writes_block_entity_identifier_lane_and_polygon_fields(self):
        source = BUFFER.read_text(encoding='utf-8')
        for needle in (
            'MemoryUtil.memPutShort(identifiers + 2L, (short) EntityIdentifiers.blockEntity());',
            'MemoryUtil.memPutFloat(pointer + this.vitrail$midTexCoord, 0.0F);',
            'MemoryUtil.memPutInt(pointer + this.vitrail$tangent, EntityFrame.FLAT);',
            'midU /= corners;',
            'midV /= corners;',
        ):
            self.assertIn(needle, source)


if __name__ == '__main__':
    unittest.main()
