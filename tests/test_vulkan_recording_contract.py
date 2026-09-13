#!/usr/bin/env python3
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
GPU_RECORDING = ROOT / 'common/src/main/java/dev/vitrail/render/storage/GpuRecording.java'
VULKAN_ACCESSOR = ROOT / 'common/src/main/java/dev/vitrail/mixin/access/VulkanCommandEncoderAccessor.java'


class VulkanRecordingContractTest(unittest.TestCase):
    def test_vulkan_end_pass_checks_for_an_open_render_pass(self):
        source = GPU_RECORDING.read_text(encoding='utf-8')

        self.assertIn('backend instanceof VulkanCommandEncoder vulkan', source)
        self.assertIn('vitrail$currentRenderPass() != null', source)
        self.assertIn('vulkan.submitRenderPass();', source)
        self.assertNotIn(
            'backend instanceof VulkanCommandEncoder || backend instanceof StorageImageCommands',
            source,
        )

        guarded = re.search(
            r'if \(backend instanceof VulkanCommandEncoder vulkan\) \{.*?'
            r'if \(\(\(VulkanCommandEncoderAccessor\) vulkan\)'
            r'\.vitrail\$currentRenderPass\(\) != null\) \{.*?'
            r'vulkan\.submitRenderPass\(\);.*?\}.*?return;.*?\}',
            source,
            re.DOTALL,
        )
        self.assertIsNotNone(
            guarded,
            'Vulkan submitRenderPass must stay behind the currentRenderPass != null guard',
        )

    def test_accessor_exposes_current_render_pass(self):
        source = VULKAN_ACCESSOR.read_text(encoding='utf-8')
        self.assertIn('@Accessor("currentRenderPass")', source)
        self.assertIn('VulkanRenderPass vitrail$currentRenderPass();', source)

    def test_storage_backend_path_remains_separate(self):
        source = GPU_RECORDING.read_text(encoding='utf-8')
        self.assertRegex(
            source,
            r'if \(backend instanceof StorageImageCommands\) \{\s*backend\.submitRenderPass\(\);\s*\}',
        )


if __name__ == '__main__':
    unittest.main()
