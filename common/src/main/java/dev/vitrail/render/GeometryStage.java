package dev.vitrail.render;

import dev.vitrail.glsl.GeometryFold;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * The geometry stage a pack ships, on a backend that has no such stage to bind it to.
 * <p>
 * Iris links a {@code .gsh} whenever the pack ships one, building it as a third shader and passing it
 * to the link ({@code gl/program/ProgramBuilder.java:44-45,55}), so a pack that renames its varyings
 * in that stage is written for a pipeline of three. Metal has no third programmable stage between the
 * vertex and fragment ones at all - it is not a feature that can be asked for and refused, it does not
 * exist - so this engine has exactly two honest answers and no third:
 * <ol>
 * <li><strong>Fold</strong>, where the stage provably only hands each corner on.
 * {@link GeometryFold} reads the preprocessed stage and, when every write to {@code gl_Position} and
 * to every varying is the input it was given, produces a fragment stage that pairs with the vertex
 * stage's outputs directly. The stage is then not missing; it is compiled into the one after it.</li>
 * <li><strong>Refuse</strong>, where it does anything else. A stage that transformed a position,
 * dropped a primitive or wrote arithmetic into a varying cannot be reproduced by a fragment stage, and
 * drawing the program without it would be a picture missing whatever that stage worked out per
 * primitive - credible and wrong, which this engine refuses everywhere else.</li>
 * </ol>
 * <p>
 * <strong>What the fold road does NOT reach, said because it is a live gap rather than a historical
 * one.</strong> The clip depth is converted in the vertex stage's epilogue, this engine rasterising
 * with reversed Z where Iris runs against an OpenGL volume, so a geometry stage reads a position
 * already converted. One that passes {@code gl_in[i].gl_Position} through, which is what the corpus
 * writes, carries it untouched, and the fold reproduces it. One that did arithmetic on the depth it
 * reads back would be working in the wrong volume, which is one of the shapes the fold refuses. Same
 * shape for the varyings this engine names for itself, the entity overlay colour among them: they are
 * emitted into the vertex and fragment stages off one union, and a stage between the two would not
 * have carried them, so such a program is refused rather than drawn wrong.
 * <p>
 * <strong>An earlier shape of this bound the stage for real</strong>, through a compiler that could be
 * asked for a third shader kind, a bind group the third module could join, a pipeline description with
 * a third stage behind the other two, and an optional device feature gating the lot, and it filed the
 * stage's text against the pipeline so that a rebuild could copy it. All of that went with the backend
 * that had those concepts, and the two answers above are what is left, which is also what that road
 * already fell back to whenever the feature was absent. Nothing a pack means was lost: the fold is
 * byte-for-byte the road the old fallback took, and the refusal names the same shapes it named then.
 * The filing went with it, because the fold now happens where the translated program is in hand rather
 * than where the pipeline is compiled, so there is nothing left to file against a pipeline.
 */
public final class GeometryStage {

	private GeometryStage() {
	}

	/**
	 * Files the geometry stage this program ships, which is the question the refuse road asks back at
	 * every build. Nothing is filed for a program without one, which is nearly all of them.
	 * <p>
	 * <strong>The answer is whether the program can be served at all</strong>, and here that is
	 * whether it ships a stage this engine cannot bind and has no fallback for. A full screen pass asks
	 * this and has no fallback, nothing else drawing a composite, so it says so and the game's own
	 * shader draws instead; no pack of the corpus writes one. A world program asks {@link #fragment}
	 * instead, which can fold the stage away and only refuses the shapes that cannot be folded.
	 *
	 * @param pipeline the pipeline this program's stages were built into
	 * @param path     the pack-side path of the program, for the line a refusal prints
	 * @param loaded   the translated program, whose geometry unit is taken if it has one
	 * @return whether this program can be drawn here
	 */
	public static boolean note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return true;
		}

		Vitrail.logger().error("{} ships a geometry stage, and Metal has no stage between the vertex "
				+ "and fragment ones to bind it to and this road has no fold, so the program is set "
				+ "aside rather than drawn without the stage", path);

		return false;
	}

	/**
	 * The fragment stage a world program is drawn with, or null where the program cannot be drawn
	 * here.
	 * <p>
	 * The program's own fragment stage wherever it ships no geometry stage. Where it ships one, the
	 * stage goes to {@link GeometryFold}: one that only hands each corner on comes back folded into the
	 * fragment stage, which is compiled in place of both and pairs with the vertex stage's outputs
	 * directly, and any other is refused. The program is set aside on that refusal and the game's own
	 * shader draws instead, which is what this engine does with every other shape it cannot serve.
	 * <p>
	 * The fold reads the stage the way the compiler will, after the preprocessor has settled every
	 * branch under the pipeline's defines. iterationT chooses between working its texture size out of
	 * the triangle and taking a constant by a {@code #if}, and a reading with both branches standing
	 * could not tell which of the two it was folding.
	 *
	 * @param pipeline the pipeline this program's stages were built into
	 * @param path     the pack-side path of the program, for the line the answer prints
	 * @param loaded   the translated program
	 * @return the text to compile the fragment stage from, or null to set the program aside
	 */
	public static String fragment(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return fragment;
		}

		GeometryFold.Result fold = fold(pipeline, path, unit.text(), fragment);
		if (!fold.folded()) {
			Vitrail.logger().error("{} ships a geometry stage, which Metal cannot bind, and the stage "
					+ "{}, so the program is set aside rather than drawn without it", path,
					fold.refusal());

			return null;
		}

		Vitrail.logger().info("{} ships a geometry stage, which Metal cannot bind, and the stage only "
				+ "hands each corner on, so it is folded into the fragment stage", path);

		return fold.fragment();
	}

	/**
	 * The geometry stage preprocessed under the pipeline's defines and handed to the fold. A compiler
	 * of its own for the one call: this runs for the few programs shipping a stage, on the fold road,
	 * once per build.
	 */
	private static GeometryFold.Result fold(RenderPipeline pipeline, String path, String geometry,
			String fragment) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		if (compiler == 0L) {
			return GeometryFold.Result.refused("could not be read, shaderc giving no compiler");
		}

		// On the heap rather than through the overloads taking text, which encode on the thread's
		// memory stack: a translated stage carries the pack's whole settings header and runs to tens
		// of kilobytes, against a stack of sixty-four.
		ByteBuffer source = MemoryUtil.memUTF8(
				GlslPreprocessor.injectDefines(geometry, pipeline.getShaderDefines()), false);
		ByteBuffer name = MemoryUtil.memUTF8(path);
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		try {
			long result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, source,
					Shaderc.shaderc_glsl_geometry_shader, name, entry, 0L);
			if (result == 0L) {
				return GeometryFold.Result.refused("could not be read, shaderc giving no result");
			}

			try {
				ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
				if (Shaderc.shaderc_result_get_compilation_status(result)
						!= Shaderc.shaderc_compilation_status_success || bytes == null) {
					return GeometryFold.Result.refused("did not preprocess: "
							+ Shaderc.shaderc_result_get_error_message(result));
				}

				return GeometryFold.fold(StandardCharsets.UTF_8.decode(bytes).toString(), fragment);
			} finally {
				Shaderc.shaderc_result_release(result);
			}
		} finally {
			MemoryUtil.memFree(entry);
			MemoryUtil.memFree(name);
			MemoryUtil.memFree(source);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}
}
