"""Exercise the real optional Metallum preference adapter without Minecraft or a GPU."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / 'common/src/main/java/dev/vitrail/render/MetallumStatus.java'
PACK_SCREENS = ROOT / 'common/src/main/java/dev/vitrail/screen/PackScreens.java'
BACKEND_PLACEHOLDER = ROOT / 'common/src/main/java/dev/vitrail/screen/BackendPlaceholder.java'
HOST_REPORT = ROOT / 'common/src/main/java/dev/vitrail/HostReport.java'

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
                require(!MetallumStatus.backgroundPipelinePrecompile(), "missing API enabled background precompile");
                require(!MetallumStatus.renderingEnabled(), "missing API enabled rendering");
            }
            case "version" -> {
                require(status.present(), "versioned API not detected");
                require(status.apiVersion() == 2, "wrong incompatible API version");
                require(!status.compatible(), "incompatible version accepted");
                require(!MetallumStatus.backgroundPipelinePrecompile(), "incompatible API enabled background precompile");
                require(!MetallumStatus.renderingEnabled(), "incompatible version enabled rendering");
            }
            case "preference-off" -> {
                require(status.present() && status.compatible(), "compatible API rejected");
                require(!status.metalPreferred(), "false preference became true");
                require(!MetallumStatus.backgroundPipelinePrecompile(), "legacy v1 guessed background safety");
                BufferBlending.serve(true);
                require(!MetallumStatus.renderingEnabled(), "preference-off enabled rendering");
            }
            case "preference-on" -> {
                require(status.present() && status.compatible() && status.metalPreferred(),
                        "Prefer Metal not observed");
                require(MetallumStatus.backgroundPipelinePrecompile(),
                        "advertised background precompile capability not observed");
                require(!MetallumStatus.renderingEnabled(), "capability-less path enabled rendering");
                BufferBlending.serve(true);
                require(MetallumStatus.renderingEnabled(),
                        "a served device with a compatible preference did not enable rendering");
            }
            case "shape" -> {
                require(status.present(), "malformed API should still be present");
                require(!status.compatible(), "malformed API accepted");
                require(!MetallumStatus.backgroundPipelinePrecompile(), "malformed API enabled background precompile");
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
    public static boolean supportsBackgroundPipelinePrecompile() { return true; }
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
    public static boolean supportsBackgroundPipelinePrecompile() { return true; }
}
'''
    if mode == 'shape':
        return '''package com.metallum.api;
public final class MetallumApi {
    public static String apiVersion() { return "1"; }
    public static boolean isMetalPreferred() { return true; }
    public static boolean supportsBackgroundPipelinePrecompile() { return true; }
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

    def test_legacy_v1_without_background_contract_fails_closed_for_warmup_only(self):
        self.run_fixture('preference-off')

    def test_preference_and_a_served_device_are_all_that_is_required(self):
        self.run_fixture('preference-on')

    def test_blocked_ui_only_goes_back_and_names_no_setting_to_change(self):
        routing = PACK_SCREENS.read_text(encoding='utf-8')
        placeholder = BACKEND_PLACEHOLDER.read_text(encoding='utf-8')

        # One screen for every session that cannot reach the Metal path: a session on some other
        # backend and a Metal session whose device never came up are the same statement now, so there
        # is one branch and no second placeholder factory.
        self.assertIn('HostReport.otherBackend()', routing)
        self.assertIn('new BackendPlaceholder(parent)', routing)
        self.assertNotIn('metalValidation', routing)
        self.assertNotIn('METAL.equals(HostReport.backend())', routing)
        self.assertNotIn('metalValidation', placeholder)

        # The screen says which fact the Metal path is missing, and offers one button.
        self.assertIn('ScreenText.BACKEND_PLACEHOLDER', placeholder)
        self.assertIn('HostReport.diagnosis()', placeholder)
        self.assertIn('ScreenText.BACKEND_RETURN', placeholder)
        self.assertNotIn('ScreenText.BACKEND_SWITCH', placeholder)

        # And it never writes the graphics preference. The button that switched the game to the other
        # backend and closed it is gone with the backend it switched to; the preference the Metal path
        # is selected through belongs to the game and to Metallum, not to this mod.
        self.assertNotIn('PreferredGraphicsApi', placeholder)
        self.assertNotIn('preferredGraphicsBackend', placeholder)
        self.assertNotIn('options.save', placeholder)
        self.assertNotIn('stop()', placeholder)
        self.assertNotIn('experimentalMetal', placeholder)
        self.assertNotIn('SMOKE_PROPERTY', placeholder)

    def test_no_session_is_ever_told_to_switch_backend(self):
        source = HOST_REPORT.read_text(encoding='utf-8')
        in_world = source.split('public static void sayInWorld() {', 1)[1].split(
            '\n\t/**\n\t * Says what an install decides', 1)[0]
        logged = source.split('private static void sayBackend() {', 1)[1]

        # The chat line names the backend and nothing else. There is no other backend to send a player
        # to, so the line cannot name a setting to change: it says the picture is missing and the log
        # carries the exact reason.
        self.assertIn('ScreenText.OTHER_BACKEND, backend()', in_world)
        self.assertNotIn('ScreenText.GRAPHICS_API', in_world)
        self.assertNotIn('ScreenText.CRASH_API', in_world)

        # The log line is built from the diagnosis, which is the clause naming the one fact that is
        # missing, and never from a setting to move.
        self.assertIn('diagnosis()', logged)
        self.assertNotIn('Vitrail.logger().error("This game is running the {} backend and {}', logged)
        for setting in ('ScreenText.GRAPHICS_API', 'ScreenText.CRASH_API', 'preferredGraphicsBackend'):
            self.assertNotIn(setting, source)

        # And the engine may not name the deleted API or its runtime portability layer anywhere.
        lowered = source.lower()
        self.assertNotIn('vulkan', lowered)
        self.assertNotIn('moltenvk', lowered)

    def test_host_report_diagnoses_the_missing_fact(self):
        source = HOST_REPORT.read_text(encoding='utf-8')
        body = source.split('public static String diagnosis() {', 1)[1].split(
            '\n\t/** Whether this is the one platform', 1)[0]

        # Seven facts, asked in the order in which each one only means anything given the last: the
        # platform, the mod, its API version, its preference, and the device. Asked of the body of
        # `diagnosis` and not of the file, which mentions the same answers in other orders elsewhere.
        for clause in (
            'MetallumStatus.status()',
            'status.present()',
            'status.compatible()',
            'status.metalPreferred()',
            'BufferBlending.served()',
            'onAppleSilicon()',
        ):
            self.assertIn(clause, body, clause)
        self.assertLess(body.index('onAppleSilicon()'), body.index('status.present()'))
        self.assertLess(body.index('status.present()'), body.index('status.compatible()'))
        self.assertLess(body.index('status.compatible()'), body.index('status.metalPreferred()'))
        self.assertLess(body.index('status.metalPreferred()'), body.index('BufferBlending.served()'))
        # And the first clause of the answer is the one that is not about the mod at all.
        self.assertLess(body.index('onAppleSilicon()'), body.index('return "this engine draws'))

    def test_malformed_api_shape_fails_closed(self):
        self.run_fixture('shape')


if __name__ == '__main__':
    unittest.main()
