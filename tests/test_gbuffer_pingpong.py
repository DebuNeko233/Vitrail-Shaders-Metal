"""Lock PHASE 6 authoritative same-target ping-pong semantics."""
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/fixtures/shaderpacks/gbuffer-pingpong-contract/shaders'
TARGET_SCHEDULE = ROOT / 'common/src/main/java/dev/vitrail/pack/target/TargetSchedule.java'
CHAIN_PLAN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/ChainPlan.java'
SAMPLER_PLAN = ROOT / 'common/src/main/java/dev/vitrail/pack/target/SamplerPlan.java'
PACK_PASS = ROOT / 'common/src/main/java/dev/vitrail/render/PackPass.java'


class GbufferPingPongTest(unittest.TestCase):
    def test_fixture_requires_two_consecutive_same_target_reads_and_writes(self):
        first = (FIXTURE / 'composite.fsh').read_text(encoding='utf-8')
        second = (FIXTURE / 'composite1.fsh').read_text(encoding='utf-8')
        third = (FIXTURE / 'composite2.fsh').read_text(encoding='utf-8')
        final = (FIXTURE / 'final.fsh').read_text(encoding='utf-8')

        self.assertIn('RENDERTARGETS:4,5', first)
        self.assertNotIn('sampler2D colortex4', first)
        self.assertIn('gl_FragData[0] = vec4(texcoord.x, 0.20, 0.40, 1.0);', first)
        self.assertIn('gl_FragData[1] = vec4(0.60, texcoord.y, 0.80, 1.0);', first)

        for source in (second, third):
            self.assertIn('uniform sampler2D colortex4;', source)
            self.assertIn('uniform sampler2D colortex5;', source)
            self.assertIn('texture2D(colortex4, texcoord)', source)
            self.assertIn('texture2D(colortex5, texcoord)', source)
            self.assertIn('RENDERTARGETS:4,5', source)
            self.assertGreaterEqual(source.count('vec4(1.0, 0.0, 1.0, 1.0)'), 2)

        for signature in (
            'closeEnough(source4.r, texcoord.x)',
            'closeEnough(source4.g, 0.20)',
            'closeEnough(source5.r, 0.60)',
            'closeEnough(source5.g, texcoord.y)',
            'gl_FragData[0] = vec4(0.15, texcoord.x, 0.35, 1.0);',
            'gl_FragData[1] = vec4(0.45, 0.55, texcoord.y, 1.0);',
        ):
            self.assertIn(signature, second)

        for signature in (
            'closeEnough(source4.r, 0.15)',
            'closeEnough(source4.g, texcoord.x)',
            'closeEnough(source4.b, 0.35)',
            'closeEnough(source5.r, 0.45)',
            'closeEnough(source5.g, 0.55)',
            'closeEnough(source5.b, texcoord.y)',
        ):
            self.assertIn(signature, third)

        self.assertIn('uniform sampler2D colortex4;', final)
        self.assertIn('uniform sampler2D colortex5;', final)
        self.assertIn('texture2D(colortex4, texcoord)', final)
        self.assertIn('texture2D(colortex5, texcoord)', final)

    def test_real_target_schedule_alternates_halves_within_one_frame(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            source = tmp / 'src'
            classes = tmp / 'classes'
            model = source / 'dev/vitrail/pack/model'
            pack_source = source / 'dev/vitrail/pack/source'
            harness_dir = source / 'dev/vitrail/pack/target'
            model.mkdir(parents=True)
            pack_source.mkdir(parents=True)
            harness_dir.mkdir(parents=True)
            classes.mkdir()

            (model / 'TargetName.java').write_text(textwrap.dedent('''
                package dev.vitrail.pack.model;
                import java.util.OptionalInt;
                public final class TargetName {
                    public static String bareName(String name) {
                        int slash = name.lastIndexOf('/');
                        return slash < 0 ? name : name.substring(slash + 1);
                    }
                    public static OptionalInt index(String name) {
                        if (!name.startsWith("colortex")) return OptionalInt.empty();
                        try { return OptionalInt.of(Integer.parseInt(name.substring(8))); }
                        catch (NumberFormatException ignored) { return OptionalInt.empty(); }
                    }
                }
            '''), encoding='utf-8')

            (model / 'ProgramNames.java').write_text(textwrap.dedent('''
                package dev.vitrail.pack.model;
                import java.util.Comparator;
                public final class ProgramNames {
                    public static String familyOf(String name) {
                        int end = name.length();
                        while (end > 0 && Character.isDigit(name.charAt(end - 1))) end--;
                        return name.substring(0, end);
                    }
                    public static int frameRank(String family) {
                        return switch (family) {
                            case "begin" -> 0;
                            case "prepare" -> 2;
                            case "deferred" -> 4;
                            case "composite" -> 5;
                            default -> 6;
                        };
                    }
                    public static Comparator<String> frameOrder() {
                        return Comparator.comparingInt((String name) -> frameRank(familyOf(name)))
                            .thenComparingInt(name -> {
                                String family = familyOf(name);
                                String suffix = name.substring(family.length());
                                return suffix.isEmpty() ? -1 : Integer.parseInt(suffix);
                            });
                    }
                }
            '''), encoding='utf-8')

            (pack_source / 'ShaderProperties.java').write_text(textwrap.dedent('''
                package dev.vitrail.pack.source;
                public final class ShaderProperties {
                    public record FlipDirective(String program, String buffer, boolean value) {}
                }
            '''), encoding='utf-8')

            harness = harness_dir / 'PingPongHarness.java'
            harness.write_text(textwrap.dedent('''
                package dev.vitrail.pack.target;
                import java.util.List;
                public final class PingPongHarness {
                    public static void main(String[] args) {
                        TargetSchedule schedule = TargetSchedule.of(List.of(
                            new TargetSchedule.Step("composite", List.of(4, 5), true),
                            new TargetSchedule.Step("composite1", List.of(4, 5), true),
                            new TargetSchedule.Step("composite2", List.of(4, 5), true)
                        ), List.of());
                        for (TargetSchedule.Bound step : schedule.steps()) {
                            System.out.println(step.program() + ":"
                                + step.read(4) + ">" + step.write(4) + ","
                                + step.read(5) + ">" + step.write(5));
                        }
                        System.out.println("END=" + schedule.flippedAtEnd());
                        System.out.println("DOUBLED=" + schedule.doubled());
                    }
                }
            '''), encoding='utf-8')

            compile_result = subprocess.run([
                'javac', '-d', str(classes),
                str(TARGET_SCHEDULE),
                str(model / 'TargetName.java'),
                str(model / 'ProgramNames.java'),
                str(pack_source / 'ShaderProperties.java'),
                str(harness),
            ], cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)

            run_result = subprocess.run([
                'java', '-cp', str(classes), 'dev.vitrail.pack.target.PingPongHarness'
            ], cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(run_result.returncode, 0, run_result.stderr)
            self.assertEqual(run_result.stdout.strip().splitlines(), [
                'composite:MAIN>ALT,MAIN>ALT',
                'composite1:ALT>MAIN,ALT>MAIN',
                'composite2:MAIN>ALT,MAIN>ALT',
                'END=[4, 5]',
                'DOUBLED=[4, 5]',
            ])

    def test_one_schedule_bound_drives_sampler_reads_and_attachment_writes(self):
        schedule = TARGET_SCHEDULE.read_text(encoding='utf-8')
        chain = CHAIN_PLAN.read_text(encoding='utf-8')
        samplers = SAMPLER_PLAN.read_text(encoding='utf-8')
        pack = PACK_PASS.read_text(encoding='utf-8')

        self.assertIn('Set<Integer> readsAlt = sortedCopy(flipped);', schedule)
        self.assertIn('bound.add(new Bound(step.program()', schedule)
        self.assertIn('attachments.add(new Attachment(index, bound.get().write(index)));', chain)
        self.assertIn('step.map(bound -> bound.read(index)).orElse(TargetSchedule.Side.MAIN)', samplers)
        self.assertIn('targets.surface(binding.index(), binding.side())', pack)


if __name__ == '__main__':
    unittest.main()
