"""Lock the final PHASE 9 shadow depth-mipmap acceptance contract."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/shadow-mipmap-contract/shaders"
SHADOW_TARGETS = ROOT / "common/src/main/java/dev/vitrail/render/ShadowTargets.java"
GPU_FORMATS = ROOT / "common/src/main/java/dev/vitrail/render/GpuFormats.java"
MIPMAP_REDUCTION = ROOT / "common/src/main/java/dev/vitrail/render/MipmapReduction.java"
METAL_MIXIN_DIR = ROOT / "common/src/main/java/dev/vitrail/mixin/metallum"
METAL_MIXINS_JSON = ROOT / "common/src/main/resources/vitrail.mixins.json"
METAL_CAPABILITIES = ROOT / "common/src/main/java/dev/vitrail/compat/metallum/MetallumEncoderCapabilities.java"
BACKENDS = ROOT / "common/src/main/java/dev/vitrail/render/Backends.java"
METAL_BRIDGE = ROOT / "common/src/main/java/dev/vitrail/compat/metallum/MetallumDepthMipmapBridge.java"
MIPMAP_CENSUS = ROOT / "common/src/main/java/dev/vitrail/render/MipmapCensus.java"
TARGET_SURFACE = ROOT / "common/src/main/java/dev/vitrail/render/TargetSurface.java"
PACK_CHAIN = ROOT / "common/src/main/java/dev/vitrail/render/PackChain.java"
PACK_DIRECTIVES = ROOT / "common/src/main/java/dev/vitrail/pack/target/PackDirectives.java"


def text(path):
    return path.read_text(encoding="utf-8")


def compact(path):
    return re.sub(r"\s+", " ", text(path))


class ShadowMipmapContractTest(unittest.TestCase):
    def test_fixture_requires_both_shadow_depth_chains_and_explicit_high_lod(self):
        self.assertEqual(
            {p.name for p in FIXTURE.iterdir()},
            {"shadow.vsh", "shadow.fsh", "composite.vsh", "composite.fsh", "final.vsh", "final.fsh"},
        )
        shadow = text(FIXTURE / "shadow.fsh")
        composite = text(FIXTURE / "composite.fsh")
        self.assertIn("gl_FragDepth = mix(0.20, 0.80, parity);", shadow)
        self.assertIn("const bool shadowtex0Mipmap = true;", composite)
        self.assertIn("const bool shadowtex1Mipmap = true;", composite)
        self.assertIn("texture2DLod(shadowtex0, texcoord, 4.0)", composite)
        self.assertIn("texture2DLod(shadowtex1, texcoord, 4.0)", composite)
        self.assertIn("bool reduced0 = abs(base0 - mip0) > 0.05;", composite)
        self.assertIn("bool reduced1 = abs(base1 - mip1) > 0.05;", composite)
        self.assertIn("!valid || reduced0 != reduced1", composite)
        self.assertIn("reduced0 && reduced1", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(0.0, 0.0, 1.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)

    def test_directives_keep_shadowtex0_and_shadowtex1_mipmap_policy_separate(self):
        directives = compact(PACK_DIRECTIVES)
        self.assertIn('case "generateShadowMipmap" -> asBool(directive, value -> { this.depthMipmap[0] = value; this.depthMipmap[1] = value;', directives)
        self.assertIn('case "shadowtexMipmap", "shadowtex0Mipmap" -> asBool(directive, value -> this.depthMipmap[0] = value);', directives)
        self.assertIn('case "shadowtex1Mipmap" -> asBool(directive, value -> this.depthMipmap[1] = value);', directives)

    def test_shadow_pair_allocates_renderable_levels_and_unlocks_lod_only_after_success(self):
        targets = compact(SHADOW_TARGETS)
        self.assertIn("GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT", targets)
        self.assertIn('createTexture(() -> "Vitrail shadowtex1", USAGE, this.depth.getFormat()', targets)
        # Each chain is named for the census: the map's two images are not pack targets, and a rate of four
        # chains a second is four images, so which image is the only thing that says which is which.
        self.assertIn('this.chainWritten[0] = MipmapReduction.generate(encoder, this.depth, "shadow");', targets)
        self.assertIn('&& MipmapReduction.generate(encoder, this.noTranslucents, "shadowtex1");', targets)
        self.assertIn("return this.chainWritten[withoutTranslucents && this.copied ? 1 : 0];", targets)

        formats = compact(GPU_FORMATS)
        # The blit question is answered by the platform rather than read off a device: the two bit
        # constants and the per-format query they were read from belonged to the deleted backend, and
        # what survives is the answer plus the safety net that made it safe to assume - the chain's
        # samplers stay at level zero unless the active capability reports that it filled them.
        blits = formats.split("static boolean blitsBothWays", 1)[1].split("\n\t}", 1)[0]
        self.assertIn("return true;", blits)
        self.assertNotIn("format(", blits)

    def test_backend_capability_failure_still_clamps_shadow_sampling_to_base(self):
        reduction = compact(MIPMAP_REDUCTION)
        # The capability is resolved through Backends.capabilities: asking the raw backend silently answered no for
        # every backend that does not carry the capabilities itself, which is the fault this resolver exists for.
        self.assertIn("if (!(Backends.capabilities(encoder) instanceof MipmapCommands commands) "
                      "|| !commands.vitrail$generateMipmaps(texture)) {", reduction)

        # The capability is supplied by an adapter over the stable flat surface, not by injecting methods into
        # whichever Metallum class happens to be named. Injection read as working until that class moved and the
        # @Mixin target stopped resolving; the capability then vanished silently and shadows went dark again.
        capabilities = compact(METAL_CAPABILITIES)
        self.assertIn("MetallumFrameBridge.generateMipmaps(backend, texture) "
                      "|| MetallumDepthMipmapBridge.generate(backend, texture)", capabilities)
        self.assertIn("implements MipmapCommands, StorageImageCommands, ComputeCommands, "
                      "AttachmentCommands, ScaleCommands", capabilities)

        mixins = compact(METAL_MIXINS_JSON)
        self.assertNotIn("MetalCommandEncoderMixin", mixins)
        for source in METAL_MIXIN_DIR.rglob("*.java"):
            # A capability injected into a named class leaves with that class, and nothing can tell: no mixin of
            # this engine may carry one again, whichever Metallum class it names.
            self.assertNotIn("MipmapCommands", compact(source), source.name)

        resolver = compact(BACKENDS)
        self.assertIn("if (carriesCapabilities(backend)) { return backend; }", resolver)
        self.assertIn("if (MetallumFrameBridge.supports(backend)) { return adapterFor(backend); }", resolver)
        self.assertIn("return METALLUM.computeIfAbsent(backend, MetallumEncoderCapabilities::new);", resolver)

        bridge = compact(METAL_BRIDGE)
        self.assertIn("package dev.vitrail.compat.metallum;", bridge)
        self.assertIn('private static final String CLASS_NAME = "com.metallum.render.MetalDepthMipmapBridge";', bridge)
        self.assertIn('bridge.getMethod("generate", Object.class, GpuTexture.class);', bridge)
        self.assertIn("if (method == null) { return false; }", bridge)
        self.assertIn("catch (ReflectiveOperationException ignored) { generate = null; }", bridge)
        self.assertNotIn("unavailable or incompatible", bridge)


    def test_a_chain_is_counted_against_the_target_it_was_filled_for(self):
        """The census says which image a chain was filled for, which is what C6 asks of it.

        A rate of four chains a second is four images and not one, and the answer to "is a chain ever filled
        after nobody reads it" needs the names: a pack target's own name, or the shadow map's, which is not a
        pack target at all. The label travels with the call and the count is taken at the one road every chain
        reaches - the texture overload - because taking it in the surface overload as well would report every
        pack target's chain twice.
        """
        census = compact(MIPMAP_CENSUS)
        reduction = compact(MIPMAP_REDUCTION)
        shadow = compact(SHADOW_TARGETS)

        self.assertIn("static void generated(final String target, final int levelsInChain, ", census)
        self.assertIn("byTarget.merge(target, 1, Integer::sum);", census)
        self.assertIn("private static String described()", census)
        self.assertIn("byTarget.clear();", census)

        # One counting site, on the road both callers reach, and the name travels to it.
        self.assertEqual(reduction.count("MipmapCensus.generated("), 1)
        self.assertIn("MipmapCensus.generated(label, texture.getMipLevels(), texture.getWidth(0), "
                      "texture.getHeight(0));", reduction)
        self.assertIn("generate(encoder, surface.texture(), surface.label())", reduction)

        # The surface's own name, and the shadow map's two images named for what they are.
        self.assertIn("String label() {", compact(TARGET_SURFACE))
        self.assertIn('MipmapReduction.generate(encoder, this.depth, "shadow")', shadow)
        self.assertIn('MipmapReduction.generate(encoder, this.noTranslucents, "shadowtex1")', shadow)

        # And the pack targets' chains still reach the same road, from the pass that reads them at a lod.
        chain = compact(PACK_CHAIN)
        self.assertIn("MipmapReduction.generate(encoder, surface)", chain)

    def test_a_refused_chain_is_said_once_rather_than_left_to_the_picture(self):
        """A pack that asked for a chain and did not get one must not have to read that off the image.

        Everything above keeps a reader at level nought when the chain could not be filled, which is safe
        and, until this test, silent: the frame is a coarser map and not a wrong one, so the difference
        reads as the pack's own settings being off. Measured on the device, a generation whose encoder does
        not carry the depth road fills nothing, and the whole shadow-mipmap diagnostic then reads as its
        matching-depth colour - which is also what it reads when no chain was ever asked for. So the
        refusal is reported once for the image it was asked of.
        """
        reduction = compact(MIPMAP_REDUCTION)
        census = compact(MIPMAP_CENSUS)

        # Reported on the branch that asked the backend, and not on the branch that declines on purpose:
        # the probe arm is this engine's own diagnostic and must keep reading as no chain at all.
        self.assertIn("|| !commands.vitrail$generateMipmaps(texture)) { "
                      "MipmapCensus.refused(label, texture.getMipLevels()); return false; }", reduction)
        self.assertIn("if (texture == null || texture.getMipLevels() <= 1 || PROBE_NO_MIP_CHAINS) "
                      "{ return false; }", reduction)
        self.assertNotIn("PROBE_NO_MIP_CHAINS) { MipmapCensus.refused", reduction)

        # One road each way, and the counting site stays single.
        self.assertEqual(reduction.count("MipmapCensus.generated("), 1)
        self.assertEqual(reduction.count("MipmapCensus.refused("), 1)

        self.assertIn("private static final Set<String> refusedTargets = new HashSet<>();", census)
        self.assertIn("static void refused(final String target, final int levelsInChain) "
                      "{ if (!refusedTargets.add(target)) { return; }", census)
        self.assertIn("Mip chain: this backend did not fill the {} image's {} levels", census)
        # Once a session and not once an interval: the counters are cleared every second, this is not.
        self.assertNotIn("refusedTargets.clear()", census)


if __name__ == "__main__":
    unittest.main()
