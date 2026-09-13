"""Exercise the real optional Metallum preference adapter without Minecraft or a GPU."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / 'common/src/main/java/dev/vitrail/render/MetallumStatus.java'
PACK_SCREENS = ROOT / 'common/src/main/java/dev/vitrail/screen/PackScreens.java'
BACKEND_PLACEHOLDER = ROOT / 'common/src/main/java/dev/vitrail/screen/BackendPlaceholder.java'

BUFFER_BLENDING = '''package dev.vitrail.render;
public final class BufferBlending {
    private static boolean served;
    public static boolean served() { return served; }
    public static void serve(boolean value) { served = value; }
}
'''

HARNESS = '''package dev.vitrail.render;
public final class MetallumStatusCheck {
    public static void main(String[] args) {
        String mode = args[0];
        MetallumStatus.Status status = MetallumStatus.status();

        switch (mode) {
            case "missing" -> {
                require(!status.present(), "missing API reported present");
                require(!status.compatible(), "missing API reported compatible");
                require(!status.metalPreferred(), "missing API reported Metal preference");
                require(!MetallumStatus.renderingEnabled(), "missing API enabled rendering");
            }
            case "version" -> {
                require(status.present(), "versioned API not detected");
                require(status.apiVersion() == 2, "wrong incompatible API version");
                require(!status.compatible(), "incompatible version accepted");
                require(!MetallumStatus.renderingEnabled(), "incompatible version enabled rendering");
            }
            case "preference-off" -> {
                require(status.present() && status.compatible(), "compatible API rejected");
                require(!status.metalPreferred(), "false preference became true");
                BufferBlending.serve(true);
                System.setProperty(MetallumStatus.SMOKE_PROPERTY, "true");
                require(!MetallumStatus.renderingEnabled(), "preference-off enabled rendering");
            }
            case "preference-on" -> {
                require(status.present() && status.compatible() && status.metalPreferred(),
                        "Prefer Metal not observed");
                require(!MetallumStatus.renderingEnabled(), "capability-less path enabled rendering");
                BufferBlending.serve(true);
                require(!MetallumStatus.renderingEnabled(), "default smoke gate was open");
                System.setProperty(MetallumStatus.SMOKE_PROPERTY, "true");
                require(MetallumStatus.renderingEnabled(), "explicit smoke gate did not open");
            }
            case "shape" -> {
                require(status.present(), "malformed API should still be present");
                require(!status.compatible(), "malformed API accepted");
                require(!MetallumStatus.renderingEnabled(), "malformed API enabled rendering");
            }
            default -> throw new AssertionError("unknown mode " + mode);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
'''


def api_source(mode):
    if mode == 'version':
        return '''package com.metallum.api;
public final class MetallumApi {
    public static int apiVersion() { return 2; }
    public static boolean isMetalPreferred() { return true; }
}
'''
    if mode == 'preference-off':
        return '''package com.metallum.api;
public final class MetallumApi {
    public static int apiVersion() { return 1; }
    public static boolean isMetalPreferred() { return false; }
}
'''
    if mode == 'preference-on':
        return '''package com.metallum.api;
public final class MetallumApi {
    public static int apiVersion() { return 1; }
    public static boolean isMetalPreferred() { return true; }
}
'''
    if mode == 'shape':
        return '''package com.metallum.api;
public final class MetallumApi {
    public static String apiVersion() { return "1"; }
    public static boolean isMetalPreferred() { return true; }
}
'''
    return None


class MetallumStatusTest(unittest.TestCase):
    def run_fixture(self, mode):
        with tempfile.TemporaryDirectory(prefix='vitrail-metallum-status-') as directory:
            root = Path(directory)
            sources = []

            def write(path, text):
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding='utf-8')
                sources.append(str(target))

            write('dev/vitrail/render/MetallumStatus.java', ADAPTER.read_text(encoding='utf-8'))
            write('dev/vitrail/render/BufferBlending.java', BUFFER_BLENDING)
            write('dev/vitrail/render/MetallumStatusCheck.java', HARNESS)
            api = api_source(mode)
            if api is not None:
                write('com/metallum/api/MetallumApi.java', api)

            classes = root / 'classes'
            subprocess.run(['javac', '-d', str(classes), *sources], check=True,
                           capture_output=True, text=True)
            subprocess.run(['java', '-cp', str(classes),
                            'dev.vitrail.render.MetallumStatusCheck', mode], check=True,
                           capture_output=True, text=True)

    def test_missing_api_fails_closed(self):
        self.run_fixture('missing')

    def test_incompatible_version_fails_closed(self):
        self.run_fixture('version')

    def test_preference_false_cannot_enable_metal(self):
        self.run_fixture('preference-off')

    def test_preference_capability_and_explicit_smoke_gate_are_all_required(self):
        self.run_fixture('preference-on')

    def test_metal_blocked_ui_does_not_reuse_opengl_switch_prompt(self):
        routing = PACK_SCREENS.read_text(encoding='utf-8')
        placeholder = BACKEND_PLACEHOLDER.read_text(encoding='utf-8')

        self.assertIn('METAL.equals(HostReport.backend())', routing)
        self.assertIn('BackendPlaceholder.metalValidation(parent)', routing)
        self.assertIn('MetallumStatus.SMOKE_PROPERTY', placeholder)
        self.assertIn('HostReport.metalCandidate()', placeholder)
        self.assertIn('Vitrail will not change your Graphics API here', placeholder)

        branch = placeholder.split('if (this.metalValidation) {', 1)[1].split('\n\t\t}', 1)[0]
        self.assertIn('ScreenText.BACKEND_RETURN', branch)
        self.assertIn('return;', branch)
        self.assertNotIn('switchToVulkan', branch)

    def test_malformed_api_shape_fails_closed(self):
        self.run_fixture('shape')


if __name__ == '__main__':
    unittest.main()
