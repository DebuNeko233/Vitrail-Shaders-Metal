"""Lock the family warm-up's one road and its handshake, without a live GPU.

There used to be two ways for a leftover family's pipelines to be built ahead of their first draw:
a detached build driven by this engine's own compiler on a worker, and the backend's public
precompile road. The detached build went with the backend that had a detached build to offer, so
what is pinned here is the single surviving road and the facts that make it safe - the capability
gate, the closed refusal, and the optional-family skip.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WARMUP = ROOT / 'common/src/main/java/dev/vitrail/render/FamilyWarmup.java'
STATUS = ROOT / 'common/src/main/java/dev/vitrail/render/MetallumStatus.java'
DUMPED = ROOT / 'common/src/main/java/dev/vitrail/render/DumpedProgram.java'
DISTANT = ROOT / 'common/src/main/java/dev/vitrail/render/DistantProgram.java'
PACK_CHAIN = ROOT / 'common/src/main/java/dev/vitrail/render/PackChain.java'


class BackgroundPipelineWarmupTest(unittest.TestCase):
    def test_there_is_one_road_and_it_is_the_backends_public_precompile(self):
        warmup = WARMUP.read_text(encoding='utf-8')

        # The gate: the backend has to advertise the contract before a single task is spawned, and
        # the road itself is the public one a first draw would have taken.
        self.assertIn('BufferBlending.served()', warmup)
        self.assertIn('MetallumStatus.backgroundPipelinePrecompile()', warmup)
        self.assertIn('program.compile(device)', warmup)

        # And the detached build is gone with everything that only served it: no second compile
        # device, no engine-owned compiler on a worker, no ahead-of-time adoption.
        for gone in ('CompileDevice', 'warmAhead', 'GlslCompiler', 'VulkanDevice', 'this.ahead'):
            self.assertNotIn(gone, warmup, gone)

    def test_the_optional_capability_is_the_only_thing_that_gates_a_worker(self):
        warmup = WARMUP.read_text(encoding='utf-8')
        status = STATUS.read_text(encoding='utf-8')

        # An older v1 Metallum is still a valid Metal integration and answers false here, which
        # leaves every family on its first-draw path rather than guessing the caches are safe.
        self.assertIn('supportsBackgroundPipelinePrecompile', status)
        probe = status.split('private static boolean probeBackgroundPipelinePrecompile()', 1)[1]
        self.assertIn('return false;', probe)
        self.assertIn('catch (ReflectiveOperationException | LinkageError | RuntimeException ignored)',
                      status)
        self.assertEqual(status.count('SUPPORTED_API_VERSION = 1'), 1)

        # A refusal is logged with its reason and spawns nothing.
        self.assertIn('The workers leave the leftover families to their first draw', warmup)

    def test_optional_families_are_gated_before_the_road_warms_them(self):
        warmup = WARMUP.read_text(encoding='utf-8')
        dumped = DUMPED.read_text(encoding='utf-8')
        distant = DISTANT.read_text(encoding='utf-8')

        self.assertIn('default boolean warmable()', dumped)
        # One road, so one walk, so one skip and one correction of the progress total.
        self.assertEqual(warmup.count('if (!program.warmable())'), 1)
        self.assertEqual(warmup.count('this.warmTotal.decrementAndGet();'), 1)
        self.assertIn('public boolean warmable()', distant)
        self.assertIn('return DhLods.usable();', distant)

    def test_nothing_holds_a_pipeline_the_device_cache_owns(self):
        dumped = DUMPED.read_text(encoding='utf-8')
        warmup = WARMUP.read_text(encoding='utf-8')

        # The chain release still walks the families, and what it calls is documented as a no-op
        # rather than left reading as a release it does not perform: the public road hands the
        # pipeline to the device's own cache at the call, so the program keeps no resource for it.
        self.assertIn('default void discardAhead()', dumped)
        self.assertIn('DumpedProgram::discardAhead', PACK_CHAIN.read_text(encoding='utf-8'))
        self.assertNotIn('vitrail$adopt', warmup)


if __name__ == '__main__':
    unittest.main()
