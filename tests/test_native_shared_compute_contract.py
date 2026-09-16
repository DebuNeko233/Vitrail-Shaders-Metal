"""Lock the native-backend fallback used by Photon deferred4_a."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "common/src/main/java/dev/vitrail/render/BackendComputePass.java"
BINDINGS = ROOT / "common/src/main/java/dev/vitrail/render/PackComputeBindings.java"
SHARED = ROOT / "common/src/main/java/dev/vitrail/glsl/SharedMemory.java"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class NativeSharedComputeContract(unittest.TestCase):
    def test_photon_skylight_shape_exceeds_verified_metal_threadgroup_limit(self):
        # Photon deferred4_a declares shared vec3 shared_memory[256][9]. SPIRV-Cross lays a
        # vec3 array element out at 16 bytes in Metal threadgroup memory.
        self.assertEqual(256 * 9 * 16, 36_864)
        self.assertGreater(36_864, 32_768)
        shared = text(SHARED)
        self.assertIn("public static final long THREADGROUP_BYTES = 32768L;", shared)
        self.assertIn('public static final String BLOCK = "OfSharedMemory";', shared)
        self.assertIn("GL_KHR_memory_scope_semantics", shared)
        self.assertIn("controlBarrier(gl_ScopeWorkgroup, gl_ScopeWorkgroup", shared)
        self.assertIn("gl_StorageSemanticsBuffer", shared)
        self.assertIn("gl_SemanticsAcquireRelease", shared)
        self.assertNotIn("memoryBarrierBuffer() in front", shared)
        self.assertNotIn('"layout(std430) coherent buffer "', shared)

    def test_native_fallback_is_limited_to_a_fixed_single_workgroup(self):
        backend = text(BACKEND)
        self.assertIn("SharedMemory.read(preprocessed)", backend)
        self.assertIn("shared.over()", backend)
        self.assertIn("this.compute.groupsX() != 1", backend)
        self.assertIn("this.compute.groupsY() != 1", backend)
        self.assertIn("this.compute.groupsZ() != 1", backend)
        self.assertIn("source = shared.moved();", backend)
        self.assertIn("this.sharedMemoryBytes = shared.bufferBytes();", backend)
        self.assertIn("instanceof StorageBufferBackend", backend)
        self.assertIn("vitrail$createStorageBuffer(this.sharedMemoryBytes)", backend)

    def test_rewritten_block_is_bound_as_a_transient_resource(self):
        backend = text(BACKEND)
        bindings = text(BINDINGS)
        self.assertIn("Map.of(SharedMemory.BLOCK", backend)
        self.assertIn("transientBuffers()", backend)
        transient = bindings.index("transientBuffers.get(name)")
        registry = bindings.index("StorageBuffers.facadeSlice(name)")
        self.assertLess(transient, registry)


if __name__ == "__main__":
    unittest.main()
