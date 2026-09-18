"""Lock PHASE 9 shadow-terrain routing, target and fixture contracts."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/shadow-terrain-contract/shaders"
TERRAIN_PASS = ROOT / "common/src/main/java/dev/vitrail/pack/program/TerrainPass.java"
FALLBACKS = ROOT / "common/src/main/java/dev/vitrail/pack/model/ProgramFallbacks.java"
TERRAIN_PROGRAM = ROOT / "common/src/main/java/dev/vitrail/render/TerrainProgram.java"
TERRAIN_DRAW = ROOT / "common/src/main/java/dev/vitrail/render/TerrainDraw.java"
GEOMETRY_PROGRAM = ROOT / "common/src/main/java/dev/vitrail/render/GeometryProgram.java"
SHADOW_TARGETS = ROOT / "common/src/main/java/dev/vitrail/render/ShadowTargets.java"
SHADOW_TERRAIN = ROOT / "common/src/main/java/dev/vitrail/sodium/ShadowTerrain.java"
ENGINE_STAGES = ROOT / "common/src/main/java/dev/vitrail/platform/EngineStages.java"
SECTION_MANAGER_ACCESSOR = ROOT / "common/src/main/java/dev/vitrail/mixin/access/RenderSectionManagerAccessor.java"
SODIUM_SETUP_MIXIN = ROOT / "common/src/main/java/dev/vitrail/mixin/sodium/MixinSodiumWorldRendererSetup.java"
MIXIN_CONFIG = ROOT / "common/src/main/resources/vitrail.mixins.json"


def text(path):
    return path.read_text(encoding="utf-8")


def compact(path):
    return re.sub(r"\s+", " ", text(path))


class ShadowTerrainContractTest(unittest.TestCase):
    def test_fixture_is_only_three_shadow_chunk_routes_plus_display_chain(self):
        expected = {
            "shadow_solid.vsh", "shadow_solid.fsh",
            "shadow_cutout.vsh", "shadow_cutout.fsh",
            "shadow_water.vsh", "shadow_water.fsh",
            "composite.vsh", "composite.fsh", "final.vsh", "final.fsh",
        }
        self.assertEqual({path.name for path in FIXTURE.iterdir()}, expected)

        all_shader_text = "\n".join(text(path) for path in FIXTURE.iterdir())
        self.assertNotIn("shadow_entities", all_shader_text)
        self.assertNotIn("shadowtex0", all_shader_text)
        self.assertNotIn("shadowtex1", all_shader_text)
        self.assertNotIn("shadowcolor1", all_shader_text)

    def test_each_shadow_chunk_program_carries_real_sodium_vertex_inputs(self):
        for name in ("shadow_solid", "shadow_cutout", "shadow_water"):
            vertex = text(FIXTURE / f"{name}.vsh")
            self.assertIn("attribute vec2 mc_midTexCoord;", vertex)
            self.assertIn("attribute vec4 at_tangent;", vertex)
            self.assertIn("varying float shadowAbiOk;", vertex)
            self.assertIn("invariant gl_Position;", vertex)
            self.assertIn("gl_Position = ftransform();", vertex)
            self.assertIn("texcoord = gl_MultiTexCoord0.st;", vertex)
            self.assertIn("length(gl_Normal)", vertex)

            fragment = text(FIXTURE / f"{name}.fsh")
            self.assertIn("uniform sampler2D gtexture;", fragment)
            self.assertIn("texture2D(gtexture, texcoord)", fragment)
            self.assertIn("/* DRAWBUFFERS:0 */", fragment)
            self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", fragment)

        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", text(FIXTURE / "shadow_solid.fsh"))
        cutout = text(FIXTURE / "shadow_cutout.fsh")
        self.assertIn("if (texel.a < 0.50)", cutout)
        self.assertIn("discard;", cutout)
        self.assertIn("vec4(1.0, 1.0, 0.0, 1.0)", cutout)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", text(FIXTURE / "shadow_water.fsh"))

    def test_display_chain_uses_shadowcolor0_only_as_observation_carrier(self):
        composite = text(FIXTURE / "composite.fsh")
        self.assertIn("uniform sampler2D shadowcolor0;", composite)
        self.assertIn("texture2D(shadowcolor0, texcoord)", composite)
        self.assertIn("/* DRAWBUFFERS:1 */", composite)
        self.assertNotIn("shadowtex", composite)
        self.assertNotIn("shadowcolor1", composite)

        final = text(FIXTURE / "final.fsh")
        self.assertIn("uniform sampler2D colortex1;", final)
        self.assertIn("texture2D(colortex1, texcoord)", final)

    def test_shadow_chunk_program_names_and_mapping_are_distinct(self):
        source = compact(TERRAIN_PASS)
        self.assertIn('SHADOW_SOLID("shadow_solid", AlphaTest.OFF, false, true)', source)
        self.assertIn('SHADOW_CUTOUT("shadow_cutout", AlphaTest.CUTOUT, false, true)', source)
        self.assertIn('SHADOW_TRANSLUCENT("shadow_water", AlphaTest.OFF, false, true)', source)
        self.assertIn("case SOLID -> SHADOW_SOLID;", source)
        self.assertIn("case CUTOUT -> SHADOW_CUTOUT;", source)
        self.assertIn("case TRANSLUCENT -> SHADOW_TRANSLUCENT;", source)
        self.assertIn("case SOLID, SHADOW_SOLID -> RenderStage.TERRAIN_SOLID;", source)
        self.assertIn("case CUTOUT, SHADOW_CUTOUT -> RenderStage.TERRAIN_CUTOUT;", source)
        self.assertIn("case TRANSLUCENT, SHADOW_TRANSLUCENT -> RenderStage.TERRAIN_TRANSLUCENT;", source)

    def test_shadow_program_fallbacks_remain_in_shadow_family(self):
        source = compact(FALLBACKS)
        self.assertIn('parents.put("shadow", null);', source)
        self.assertIn('parents.put("shadow_solid", "shadow");', source)
        self.assertIn('parents.put("shadow_cutout", "shadow");', source)
        self.assertIn('parents.put("shadow_water", "shadow");', source)

    def test_shadow_chunk_pipeline_is_forward_depth_no_cull(self):
        program = compact(TERRAIN_PROGRAM)
        self.assertIn("pass.shadow(),", program)
        self.assertIn("!pass.shadow(), depthState(pass), pass.stage(), null,", program)
        self.assertIn(
            "? new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true) : DepthStencilState.DEFAULT;",
            program,
        )

        geometry = compact(GEOMETRY_PROGRAM)
        self.assertIn(
            "this.values.convention(this.pass.shadow() ? ClipSpace.FORWARD : ClipSpace.REVERSED);",
            geometry,
        )

    def test_shadow_stage_reuses_camera_chunk_passes_under_shadow_mapping(self):
        draw = compact(TERRAIN_DRAW)
        self.assertIn("shadowing = true; try { draw.run(); } finally { shadowing = false; }", draw)
        self.assertIn("return shadowing ? pass.inShadow() : pass;", draw)
        self.assertIn("return wanted && shadowWanted && PackChain.terrain() != null;", draw)

    def test_shadow_and_shadowcomp_share_the_current_frame(self):
        stages = compact(ENGINE_STAGES)
        begin = stages.index("PackChain.beginShadowFrame();")
        draw = stages.index("ShadowTerrain.draw();")
        compute = stages.index("PackChain.dispatchShadowCompute();")
        after_level = stages.index("public static void afterLevel()")
        self.assertLess(begin, draw)
        self.assertLess(draw, compute)
        self.assertLess(draw, after_level)
        self.assertEqual(stages.count("ShadowTerrain.draw();"), 1)

    def test_light_walk_restores_the_exact_camera_manager_state(self):
        terrain = compact(SHADOW_TERRAIN)
        accessor = compact(SECTION_MANAGER_ACCESSOR)
        mixin = compact(SODIUM_SETUP_MIXIN)
        config = text(MIXIN_CONFIG)

        self.assertIn("ShadowTerrain.captureCameraWalk(camera, viewport, fogParameters);", mixin)
        self.assertIn('"sodium.MixinSodiumWorldRendererSetup"', config)
        self.assertIn("int shadowFrame = cameraFrame ^ Integer.MIN_VALUE;", terrain)
        self.assertIn("renderer.prepareChunkRendering(matrices, camera.x, camera.y, camera.z);", terrain)
        self.assertIn("manager.finalizeRenderLists(camera, viewport, fog, true);", terrain)
        self.assertIn("access.vitrail$setRenderLists(lists);", terrain)
        self.assertIn("access.vitrail$setRenderTree(tree);", terrain)
        self.assertIn("access.vitrail$setTaskLists(tasks);", terrain)
        self.assertIn("SortedRenderLists vitrail$getRenderLists();", accessor)
        self.assertIn("void vitrail$setCameraChanged(boolean value);", accessor)

    def test_shadow_target_is_forward_d32_render_to_sample_image(self):
        targets = compact(SHADOW_TARGETS)
        self.assertIn("private static final double FAR = 1.0;", targets)
        self.assertIn("private static final GpuFormat DEPTH_FORMAT = GpuFormat.D32_FLOAT;", targets)
        self.assertIn("GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT", targets)
        self.assertIn("private GpuTexture depth;", targets)
        self.assertIn("private GpuTextureView depthAttachment;", targets)

    def test_shadow_attachment_feedback_is_not_reported_as_unfilled(self):
        geometry = compact(GEOMETRY_PROGRAM)
        self.assertIn("private boolean shadowAttachmentFeedback(String sampler)", geometry)
        self.assertIn(
            "return kind == SamplerPlan.Kind.SHADOW_DEPTH || kind == SamplerPlan.Kind.SHADOW_COLOUR;",
            geometry,
        )
        self.assertIn(
            "List<String> feedback = this.samplers.stream().filter(this::shadowAttachmentFeedback).toList();",
            geometry,
        )
        self.assertIn("&& !shadowAttachmentFeedback(name))", geometry)
        self.assertIn("read a shadow attachment this pass is writing", geometry)


if __name__ == "__main__":
    unittest.main()
