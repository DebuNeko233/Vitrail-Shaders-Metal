"""Run the real optional adapter against isolated bridge fixtures (no GPU required)."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / 'common/src/main/java/dev/vitrail/compat/metallum/MetallumComputeBridge.java'
DEPTH_ADAPTER = ROOT / 'common/src/main/java/dev/vitrail/compat/metallum/MetallumDepthMipmapBridge.java'
MIXIN_METALLUM = ROOT / 'common/src/main/java/dev/vitrail/mixin/metallum'

BRIDGE = '''package com.metallum.render;
import java.nio.ByteBuffer;
import java.util.Map;
public final class MetalComputeBridge {
    public static Object compile(Object backend, String label, ByteBuffer spirv) {
        if (label.equals("runtime")) throw new IllegalArgumentException("runtime");
        if (label.equals("fatal")) throw new AssertionError("fatal");
        return backend;
    }
    public static boolean dispatch(Object encoder, Object pipeline, Map<?, ?> buffers,
            Map<?, ?> textures, Map<?, ?> samplers, int gx, int gy, int gz,
            int lx, int ly, int lz) {
        return encoder == pipeline && gx == 2 && gy == 3 && gz == 4
                && lx == 8 && ly == 4 && lz == 1;
    }
    public static void close(Object pipeline) {}
}
'''

DEPTH_BRIDGE = '''package com.metallum.render;
import com.mojang.blaze3d.textures.GpuTexture;
public final class MetalDepthMipmapBridge {
    public static boolean generate(Object encoder, GpuTexture texture) {
        return encoder != null && texture != null;
    }
}
'''

HARNESS = '''package dev.vitrail.compat.metallum;
import java.nio.ByteBuffer;
import java.util.Map;
public final class BridgeCheck {
    public static void main(String[] args) {
        if (!args[0].equals("valid")) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    MetallumComputeBridge.compile(new Object(), "test", ByteBuffer.allocate(0));
                    throw new AssertionError("Unavailable bridge accepted");
                } catch (IllegalStateException expected) {
                    if (!(expected.getCause() instanceof ReflectiveOperationException)) {
                        throw new AssertionError("Lost lookup cause", expected);
                    }
                }
            }
            return;
        }
        Object token = new Object();
        if (MetallumComputeBridge.compile(token, "test", ByteBuffer.allocate(0)) != token) {
            throw new AssertionError("Pipeline identity lost");
        }
        if (!MetallumComputeBridge.dispatch(token, token, Map.of(), Map.of(), Map.of(),
                2, 3, 4, 8, 4, 1)) throw new AssertionError("Dispatch arguments lost");
        MetallumComputeBridge.close(token);
        try {
            MetallumComputeBridge.compile(token, "runtime", ByteBuffer.allocate(0));
            throw new AssertionError("Runtime failure swallowed");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().equals("runtime")) throw expected;
        }
        try {
            MetallumComputeBridge.compile(token, "fatal", ByteBuffer.allocate(0));
            throw new IllegalStateException("Fatal failure swallowed");
        } catch (AssertionError expected) {
            if (!expected.getMessage().equals("fatal")) throw expected;
        }
    }
}
'''


DEPTH_HARNESS = '''package dev.vitrail.compat.metallum;
import com.mojang.blaze3d.textures.GpuTexture;
public final class DepthCheck {
    public static void main(String[] args) {
        boolean expected = args[0].equals("valid");
        if (MetallumDepthMipmapBridge.generate(new Object(), new GpuTexture()) != expected) {
            throw new AssertionError("the backend's answer did not arrive: " + expected);
        }
        if (MetallumDepthMipmapBridge.generate(null, new GpuTexture())) {
            throw new AssertionError("the backend's refusal was not refused");
        }
        // The lookup caches its answer, negative included, so a second call must answer the same thing
        // rather than resolving again - an older backend may not pay reflection once a frame.
        if (MetallumDepthMipmapBridge.generate(new Object(), new GpuTexture()) != expected) {
            throw new AssertionError("the cached answer changed");
        }
    }
}
'''


def compile_and_run(files, main, argument):
    """Compile these sources as their own little tree and run one check against them."""
    with tempfile.TemporaryDirectory(prefix='vitrail-bridge-') as directory:
        root = Path(directory)
        sources = []
        for relative, text in files.items():
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')
            sources.append(str(target))
        subprocess.run(['javac', '-d', str(root / 'classes'), *sources], check=True,
                       capture_output=True, text=True)
        subprocess.run(['java', '-cp', str(root / 'classes'), main, argument], check=True,
                       capture_output=True, text=True)


class ComputeBridgeTest(unittest.TestCase):
    def test_optional_helpers_live_outside_the_defined_mixin_package(self):
        self.assertIn('package dev.vitrail.compat.metallum;', ADAPTER.read_text(encoding='utf-8'))
        self.assertEqual(list(MIXIN_METALLUM.glob('Metallum*Bridge.java')), [])

    def run_fixture(self, mode):
        # Only the facade type names are needed: this checks reflection/failure behavior,
        # not the Minecraft ABI or native resource semantics.
        files = {
            'com/mojang/blaze3d/buffers/GpuBufferSlice.java':
                'package com.mojang.blaze3d.buffers; public class GpuBufferSlice {}',
            'com/mojang/blaze3d/textures/GpuSampler.java':
                'package com.mojang.blaze3d.textures; public class GpuSampler {}',
            'com/mojang/blaze3d/textures/GpuTextureView.java':
                'package com.mojang.blaze3d.textures; public class GpuTextureView {}',
            'dev/vitrail/compat/metallum/MetallumComputeBridge.java': ADAPTER.read_text(),
            'dev/vitrail/compat/metallum/BridgeCheck.java': HARNESS,
        }
        if mode != 'missing':
            bridge = BRIDGE
            if mode == 'incompatible':
                bridge = bridge.replace('public static void close(Object pipeline) {}', '')
            files['com/metallum/render/MetalComputeBridge.java'] = bridge
        compile_and_run(files, 'dev.vitrail.compat.metallum.BridgeCheck', mode)

    def run_depth_fixture(self, mode):
        files = {
            'com/mojang/blaze3d/textures/GpuTexture.java':
                'package com.mojang.blaze3d.textures; public class GpuTexture {}',
            'dev/vitrail/compat/metallum/MetallumDepthMipmapBridge.java': DEPTH_ADAPTER.read_text(),
            'dev/vitrail/compat/metallum/DepthCheck.java': DEPTH_HARNESS,
        }
        if mode == 'valid':
            files['com/metallum/render/MetalDepthMipmapBridge.java'] = DEPTH_BRIDGE
        compile_and_run(files, 'dev.vitrail.compat.metallum.DepthCheck', mode)

    def test_missing_bridge_remains_catchable_on_repeated_calls(self):
        self.run_fixture('missing')

    def test_missing_close_rejects_bridge_before_compilation(self):
        self.run_fixture('incompatible')

    def test_valid_bridge_and_backend_failures(self):
        self.run_fixture('valid')

    def test_a_flat_depth_bridge_is_answered_and_a_missing_one_is_a_no(self):
        # The depth chain's fallback is the one whose absence was visible as darker shadows, so it is the
        # fixture worth having: the answer arrives when the flat bridge is there, and an older backend
        # answers "not mine" rather than throwing - which is what keeps the sampler clamped to level zero
        # instead of ending the frame.
        self.run_depth_fixture('valid')
        self.run_depth_fixture('missing')


if __name__ == '__main__':
    unittest.main()
