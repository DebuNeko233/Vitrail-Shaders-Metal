from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "tests/fixtures/shaderpacks/compute-storage-contract/shaders"
PACK_COMPUTE = ROOT / "common/src/main/java/dev/vitrail/render/PackCompute.java"
BACKEND_COMPUTE = ROOT / "common/src/main/java/dev/vitrail/render/BackendComputePass.java"
BINDINGS = ROOT / "common/src/main/java/dev/vitrail/render/PackComputeBindings.java"
GPU_FORMATS = ROOT / "common/src/main/java/dev/vitrail/render/GpuFormats.java"
STORAGE_BUFFERS = ROOT / "common/src/main/java/dev/vitrail/render/storage/StorageBuffers.java"
STORAGE_IMAGES = ROOT / "common/src/main/java/dev/vitrail/render/storage/StorageImages.java"
SHADER_WRITABLE = ROOT / "common/src/main/java/dev/vitrail/render/storage/ShaderWritableTextureBackend.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class ComputeStorageContract(unittest.TestCase):
    def test_fixture_is_two_ordered_single_group_compute_passes(self):
        first = text(FIXTURE / "composite.csh")
        second = text(FIXTURE / "composite_a.csh")
        for shader in (first, second):
            self.assertIn("layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;", shader)
            self.assertIn("const ivec3 workGroups = ivec3(1, 1, 1);", shader)
            self.assertIn("buffer Phase15Buffer", shader)
            self.assertIn("phase15Image", shader)
        self.assertIn("imageStore(phase15Image", first)
        self.assertIn("imageLoad(phase15Image", second)
        self.assertIn("phase15Data.x == 0x50483135u", second)
        self.assertIn("imageStore(phase15Image", second)

    def test_fixture_declares_named_ssbo_and_custom_storage_image(self):
        properties = text(FIXTURE / "shaders.properties")
        self.assertRegex(properties, r"(?m)^bufferObject\.0=16 Phase15Buffer$")
        self.assertRegex(
            properties,
            r"(?m)^image\.phase15Image=phase15Tex RGBA RGBA8 UNSIGNED_BYTE false false 1 1$",
        )
        composite = text(FIXTURE / "composite.fsh")
        final = text(FIXTURE / "final.fsh")
        self.assertIn("uniform sampler2D phase15Tex;", composite)
        self.assertIn("texture2D(phase15Tex, texcoord)", composite)
        self.assertIn("vec4(0.0, 1.0, 0.0, 1.0)", composite)
        self.assertIn("vec4(1.0, 0.0, 1.0, 1.0)", composite)
        self.assertIn("uniform sampler2D colortex0;", final)

    def test_pack_semantics_stay_in_vitrail_and_backend_seam_is_opaque(self):
        compute = text(PACK_COMPUTE)
        backend = text(BACKEND_COMPUTE)
        bindings = text(BINDINGS)
        buffers = text(STORAGE_BUFFERS)
        images = text(STORAGE_IMAGES)
        self.assertIn("Dispatched {} compute pass(es) at {}", compute)
        self.assertIn("ProgramNames.computeBase", compute)
        self.assertIn("vitrail$dispatchCompute", backend)
        self.assertIn("ComputeResources.inspect", backend)
        self.assertIn("StorageBuffers.facadeSlice", bindings)
        self.assertIn("StorageImages.facadeView", bindings)
        self.assertIn("vitrail$createStorageBuffer", buffers)
        self.assertIn("vitrail$createStorageImage", images)
        fixture_text = "\n".join(text(path) for path in FIXTURE.iterdir() if path.is_file())
        self.assertNotIn("Metal", fixture_text)
        self.assertNotIn("MTL", fixture_text)

    def test_shader_writable_backend_counts_as_storage_capable(self):
        formats = text(GPU_FORMATS)
        writable = text(SHADER_WRITABLE)
        self.assertIn("interface ShaderWritableTextureBackend", writable)
        self.assertIn("instanceof ShaderWritableTextureBackend", formats)
        self.assertIn("VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT", formats)
        self.assertLess(
            formats.index("instanceof ShaderWritableTextureBackend"),
            formats.index("VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT"),
        )


if __name__ == "__main__":
    unittest.main()
