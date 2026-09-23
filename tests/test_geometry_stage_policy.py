"""Lock the geometry-stage policy: fold what only hands each corner on, refuse everything else.

Metal has no stage between the vertex and fragment ones, so a pack shipping a `.gsh` has exactly two
honest answers here and no third. The fold is a real parser over the preprocessed stage text, so this
contract exercises it rather than reading it: `GeometryFold` and its lexer are dependency-free Java, and
the harness below compiles them and folds four stages with one JVM. The three refusals matter as much as
the fold: a stage that scaled a varying, and a fragment stage that does not take the name plainly, must
come back refused with the reason named, because drawing the program without the stage would be a
picture missing whatever that stage worked out per primitive.
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
LEXER = ROOT / "common/src/main/java/dev/vitrail/glsl/GlslLexer.java"
FOLD = ROOT / "common/src/main/java/dev/vitrail/glsl/GeometryFold.java"
STAGE = ROOT / "common/src/main/java/dev/vitrail/render/GeometryStage.java"

HARNESS = """package dev.vitrail.glsl;

import java.nio.file.Files;
import java.nio.file.Path;

/** Folds the stages this test wrote out, and insists on what each answer has to be. */
public final class GeometryFoldCheck {
    public static void main(String[] args) throws Exception {
        Path stages = Path.of(args[0]);

        GeometryFold.Result folded = GeometryFold.fold(read(stages, "pass.gsh"), read(stages, "pass.fsh"));
        require(folded.folded(), "a pass-through stage was refused: " + folded.refusal());
        require(folded.fragment().contains("in vec2 v_texcoord;"),
                "the vertex stage's own name is not declared as the fragment stage's input");
        require(folded.fragment().contains("f_texcoord = v_texcoord;"),
                "the name the stage handed on is not filled from the vertex stage's output");
        require(!folded.fragment().contains("EmitVertex"), "the folded stage still emits vertices");
        require(!folded.fragment().contains("gl_in"), "the folded stage still reads the geometry inputs");

        GeometryFold.Result bare = GeometryFold.fold(read(stages, "bare.gsh"), read(stages, "bare.fsh"));
        require(bare.folded(), "a position-only stage was refused: " + bare.refusal());
        require(!bare.fragment().contains("gl_in"), "a position-only fold still reads the geometry inputs");

        GeometryFold.Result arithmetic = GeometryFold.fold(read(stages, "arithmetic.gsh"), read(stages, "pass.fsh"));
        require(!arithmetic.folded(), "a stage that scaled a varying was folded into the fragment stage");
        require(arithmetic.refusal() != null && arithmetic.refusal().contains("copy each corner"),
                "the refusal does not say what the stage did: " + arithmetic.refusal());

        GeometryFold.Result unplain = GeometryFold.fold(read(stages, "pass.gsh"), read(stages, "varying.fsh"));
        require(!unplain.folded(), "a fragment stage that does not declare the name was folded anyway");
        require(unplain.refusal() != null && unplain.refusal().contains("does not declare once as a plain input"),
                "the refusal does not name the declaration: " + unplain.refusal());

        System.out.println("geometry fold check: PASS");
    }

    private static String read(Path stages, String name) throws Exception {
        return Files.readString(stages.resolve(name));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
"""

# The shape the corpus writes and Iris links: triangles in, a strip of three out, and a loop that
# copies each corner of one input onto the output the fragment stage reads.
PASS_THROUGH = """#version 120
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec2 v_texcoord[];
out vec2 f_texcoord;
void main() {
    for (int i = 0; i < 3; i++) {
        gl_Position = gl_in[i].gl_Position;
        f_texcoord = v_texcoord[i];
        EmitVertex();
    }
    EndPrimitive();
}
"""

PASS_FRAGMENT = """#version 120
in vec2 f_texcoord;
void main() {
    gl_FragColor = vec4(f_texcoord, 0.0, 1.0);
}
"""

BARE = """#version 120
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
void main() {
    for (int i = 0; i < 3; i++) {
        gl_Position = gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
"""

BARE_FRAGMENT = """#version 120
void main() {
    gl_FragColor = vec4(1.0);
}
"""

# One value worked out from the corner rather than copied from it, which no fragment stage can see.
ARITHMETIC = """#version 120
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
in vec2 v_texcoord[];
out vec2 f_texcoord;
void main() {
    for (int i = 0; i < 3; i++) {
        gl_Position = gl_in[i].gl_Position;
        f_texcoord = v_texcoord[i] * 2.0;
        EmitVertex();
    }
    EndPrimitive();
}
"""

# The same name, declared the way a hand-written pack declares it rather than the way this engine's own
# translated stages do. The fold refuses rather than renaming it behind the fragment stage's back.
VARYING_FRAGMENT = """#version 120
varying vec2 f_texcoord;
void main() {
    gl_FragColor = vec4(f_texcoord, 0.0, 1.0);
}
"""

def compact(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


class GeometryStagePolicyTest(unittest.TestCase):
    def test_the_fold_serves_a_pass_through_and_refuses_what_it_cannot_see(self):
        with tempfile.TemporaryDirectory(prefix="vitrail-geometry-fold-") as directory:
            root = Path(directory)
            stages = root / "stages"
            stages.mkdir()
            for name, text in (
                ("pass.gsh", PASS_THROUGH),
                ("pass.fsh", PASS_FRAGMENT),
                ("bare.gsh", BARE),
                ("bare.fsh", BARE_FRAGMENT),
                ("arithmetic.gsh", ARITHMETIC),
                ("varying.fsh", VARYING_FRAGMENT),
            ):
                (stages / name).write_text(text, encoding="utf-8")

            harness = root / "dev/vitrail/glsl/GeometryFoldCheck.java"
            harness.parent.mkdir(parents=True, exist_ok=True)
            harness.write_text(HARNESS, encoding="utf-8")

            classes = root / "classes"
            subprocess.run(["javac", "-d", str(classes), str(LEXER), str(FOLD), str(harness)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(classes), "dev.vitrail.glsl.GeometryFoldCheck",
                                     str(stages)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("geometry fold check: PASS", result.stdout)

    def test_the_stage_has_two_roads_and_no_third(self):
        stage = compact(STAGE)

        # Fold: the stage is read after the preprocessor, and what comes back is compiled in its place.
        self.assertIn("GeometryFold.Result fold = fold(pipeline, path, unit.text(), fragment);", stage)
        self.assertIn("if (!fold.folded()) {", stage)
        self.assertIn("return fold.fragment();", stage)

        # Refuse: the program is set aside and the reason is said, at the level a lost picture deserves.
        self.assertIn("Vitrail.logger().error(", stage)
        self.assertIn("fold.refusal());", stage)
        self.assertIn("return null;", stage)

        # A full screen pass has no fallback - nothing else draws a composite - so it is refused by name.
        self.assertIn("public static boolean note(RenderPipeline pipeline, String path, "
                      "PackProgram.Loaded loaded) {", stage)
        self.assertIn("TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);", stage)
        self.assertIn("if (unit == null) {", stage)
        self.assertIn("return false;", stage)

        # The pipeline's own defines are injected before the fold reads a line, because iterationT
        # chooses between two shapes of its stage under a #if and both branches standing would leave
        # the reading unable to tell which one it was folding.
        self.assertIn("Shaderc.shaderc_compile_into_preprocessed_text(compiler, source, "
                      "Shaderc.shaderc_glsl_geometry_shader, name, entry, 0L)", stage)
        self.assertIn("GlslPreprocessor.injectDefines(geometry, pipeline.getShaderDefines())", stage)
        self.assertIn("GeometryFold.fold(StandardCharsets.UTF_8.decode(bytes).toString(), fragment)", stage)

        # A refusal is a clause and never a fragment: `folded()` is what decides, not a null check on one.
        fold = compact(FOLD)
        self.assertIn("public record Result(String fragment, String refusal) {", fold)
        self.assertIn("public static Result refused(String refusal) {", fold)
        self.assertIn("return this.fragment != null;", fold)
        self.assertIn("catch (Refused refused) {", fold)
        self.assertIn("return Result.refused(refused.getMessage());", fold)


if __name__ == "__main__":
    unittest.main()
