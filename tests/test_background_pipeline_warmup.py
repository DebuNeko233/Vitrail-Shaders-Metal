"""Lock the backend-neutral family warm-up handshake without a live GPU."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WARMUP = ROOT / 'common/src/main/java/dev/vitrail/render/FamilyWarmup.java'
STATUS = ROOT / 'common/src/main/java/dev/vitrail/render/MetallumStatus.java'


class BackgroundPipelineWarmupTest(unittest.TestCase):
    def test_vulkan_keeps_detached_build_and_metal_uses_advertised_public_precompile(self):
        warmup = WARMUP.read_text(encoding='utf-8')

        self.assertIn('private record CompileDevice(GpuDevice front, VulkanDevice vulkan)', warmup)
        self.assertIn('backend instanceof VulkanDevice device', warmup)
        self.assertIn('program.warmAhead(device.vulkan(), compiler)', warmup)
        self.assertIn('BufferBlending.served() && MetallumStatus.backgroundPipelinePrecompile()', warmup)
        self.assertIn('program.compile(device.front())', warmup)
        self.assertNotIn('the backend is not the Vulkan one', warmup)

    def test_optional_capability_fails_closed_for_older_api_v1(self):
        status = STATUS.read_text(encoding='utf-8')

        self.assertIn('supportsBackgroundPipelinePrecompile', status)
        self.assertIn('catch (ReflectiveOperationException | LinkageError | RuntimeException ignored)', status)
        self.assertIn('return false;', status.split('private static boolean probeBackgroundPipelinePrecompile()', 1)[1])
        self.assertEqual(status.count('SUPPORTED_API_VERSION = 1'), 1)


if __name__ == '__main__':
    unittest.main()
