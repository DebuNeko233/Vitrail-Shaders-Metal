"""Run the real optional adapter against isolated bridge fixtures (no GPU required)."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / 'common/src/main/java/dev/vitrail/compat/metallum/MetallumComputeBridge.java'
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


class ComputeBridgeTest(unittest.TestCase):
    def test_optional_helpers_live_outside_the_defined_mixin_package(self):
        self.assertIn('package dev.vitrail.compat.metallum;', ADAPTER.read_text(encoding='utf-8'))
        self.assertEqual(list(MIXIN_METALLUM.glob('Metallum*Bridge.java')), [])

    def run_fixture(self, mode):
        with tempfile.TemporaryDirectory(prefix='vitrail-bridge-') as directory:
            root = Path(directory)
            sources = []

            def write(path, text):
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding='utf-8')
                sources.append(str(target))

            # Only the facade type names are needed: this checks reflection/failure behavior,
            # not the Minecraft ABI or native resource semantics.
            for package, name in [('buffers', 'GpuBufferSlice'),
                                  ('textures', 'GpuSampler'), ('textures', 'GpuTextureView')]:
                write(f'com/mojang/blaze3d/{package}/{name}.java',
                      f'package com.mojang.blaze3d.{package}; public class {name} {{}}')
            write('dev/vitrail/compat/metallum/MetallumComputeBridge.java', ADAPTER.read_text())
            write('dev/vitrail/compat/metallum/BridgeCheck.java', HARNESS)
            if mode != 'missing':
                bridge = BRIDGE
                if mode == 'incompatible':
                    bridge = bridge.replace('public static void close(Object pipeline) {}', '')
                write('com/metallum/render/MetalComputeBridge.java', bridge)
            subprocess.run(['javac', '-d', str(root / 'classes'), *sources], check=True,
                           capture_output=True, text=True)
            subprocess.run(['java', '-cp', str(root / 'classes'),
                            'dev.vitrail.compat.metallum.BridgeCheck', mode], check=True,
                           capture_output=True, text=True)

    def test_missing_bridge_remains_catchable_on_repeated_calls(self):
        self.run_fixture('missing')

    def test_missing_close_rejects_bridge_before_compilation(self):
        self.run_fixture('incompatible')

    def test_valid_bridge_and_backend_failures(self):
        self.run_fixture('valid')


if __name__ == '__main__':
    unittest.main()
