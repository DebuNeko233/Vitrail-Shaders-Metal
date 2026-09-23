package dev.vitrail.render;

import dev.vitrail.glsl.GlslTranslator;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The comparison sampler a {@code sampler2DShadow} lookup runs on, and which pipelines owe it to
 * which names.
 * <p>
 * The translation leaves a comparison sampler its spelling wherever the map's own names are behind
 * it, so the lookup compiles to a depth-reference sample; what the hardware then needs is a sampler
 * with the comparison enabled, and {@code GpuSampler} cannot describe one. That is a narrow
 * capability rather than a backend detail, so the object is made by the backend:
 * {@link ComparisonSamplers} asks it for a sampler cloned from the pack's own ordinary one with
 * LEQUAL comparison added, and this class answers only the semantic half - which names, in which
 * pipeline, are read through a comparison at all.
 * <p>
 * The pair the backend carries is the pair Iris binds when a pack asks for its hardware shadow
 * filtering ({@code ShadowRenderTargets.getSamplerFor}, under {@code shadowHardwareFiltering}),
 * {@code GL_LINEAR} plus {@code GL_COMPARE_REF_TO_TEXTURE}; every pack of the corpus that declares
 * the type writes that directive, and without it Iris leaves what such a declaration reads
 * undefined.
 * <strong>LINEAR here is unconditional where Iris's is not</strong>: a pack that writes one of the
 * nearest directives beside the hardware one gets NEAREST_HW from Iris. No pack of the corpus
 * writes both live, so nothing measures it today. The sampler is one object for the device's life,
 * so serving it would need a second one, and such a pack reads the map NEAREST on the ordinary bind
 * and blended over four texels here.
 * The sense is LEQUAL: OptiFine sets that on a shadow texture, so it is what every pack is written
 * against, and the map stores the forward window where nearer is smaller. Filtered, the hardware
 * compares each of the four texels and blends the RESULTS with the bilinear weights, which is
 * exactly the arithmetic the translation writes on its other road; the two roads answer the same
 * fraction. The level of detail is pinned to the base, and that is the sampler's own gap now that
 * the map does carry a chain wherever a pack asks for one: Iris hands a compared read a mipmapped
 * sampler under {@code shadowtexMipmap} and this one clamps. The line that sets it says why it is
 * kept.
 * <p>
 * The registry is weak on the pipeline, because that is the lifetime being described: a pipeline
 * dropped on a pack change takes its entry with it, and a reload registers the new ones as they
 * are built. Nothing native is held here any more - an earlier shape created the sampler itself and
 * handed its handle to a descriptor walk that no longer exists - so there is nothing to release at
 * shutdown and {@link #close} only empties the registry.
 */
public final class ShadowCompare {

	private static final String ARM_FILE = "soft-shadow-compare";

	private static final Map<RenderPipeline, Set<String>> COMPARED =
			Collections.synchronizedMap(new WeakHashMap<>());

	/** Whether anything is filed at all, so the walk over every binding asks one flag first. */
	private static volatile boolean noted;

	private static boolean announced;

	private ShadowCompare() {
	}

	/**
	 * Puts the translation on the arithmetic road when somebody asked for it, and back off it when
	 * they stopped asking: a file {@code vitrail/soft-shadow-compare} in the game directory, or
	 * {@code -Dvitrail.softShadowCompare=true}. Called before a pack is read, every time one is,
	 * so removing the file and reloading undoes it without a restart. The trade cannot be watched
	 * from inside, a comparison bound wrong handing back a credible fraction rather than an error,
	 * so an image that comes right with this on has named the comparison sampler in one launch.
	 */
	public static void armIfAsked() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gameDirectory == null) {
			return;
		}

		boolean asked = Files.isRegularFile(minecraft.gameDirectory.toPath()
				.resolve("vitrail").resolve(ARM_FILE));
		GlslTranslator.askSoftCompare(asked);
		if (asked && !announced) {
			announced = true;
			Vitrail.logger().warn("Every shadow comparison is made in shader arithmetic, asked for "
					+ "by vitrail/{}. The image should not move; the shadow lookups are slower. "
					+ "Remove it and reload the pack to put the comparison back on the sampler",
					ARM_FILE);
		}
	}

	/**
	 * Files which sampler names this pipeline reads through a comparison, which is the question
	 * {@link ComparisonSamplers} asks back at every binding. Nothing is filed for a program with
	 * none, which is what keeps the common case behind {@link #noted()}.
	 * <p>
	 * The sampler follows the declaration, wherever that leads, because the shader's side is
	 * already settled: the lookup compiles to a depth-reference sample, and a comparison sampler
	 * against a wrong image and no comparison sampler under a depth-reference sample are both
	 * undefined, so withholding it would repair nothing. What this can add is the word nothing on
	 * screen would say: a pack that has put something that is not the shadow map behind a compared
	 * name, or that spells one name two ways across the stages of one program, is named here. The
	 * sampler serves the whole pipeline, so the stage that spelled the name ordinary reads through
	 * the comparison all the same, undefined exactly as under Iris, where the sampler sits on the
	 * texture unit both stages share.
	 */
	static void note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		Set<String> names = new LinkedHashSet<>();
		for (TranslatedUnit unit : loaded.program().stages().values()) {
			names.addAll(unit.notes().hardwareCompared());
		}

		if (names.isEmpty()) {
			return;
		}

		for (String name : names) {
			if (loaded.samplers().binding(name).kind() != SamplerPlan.Kind.SHADOW_DEPTH) {
				Vitrail.logger().warn("{} declares {} as a comparison sampler, and the pack has "
						+ "put something that is not the shadow map behind the name: the "
						+ "comparison runs against it all the same, which is undefined here as "
						+ "it is under Iris", path, name);
			}
		}

		for (TranslatedUnit unit : loaded.program().stages().values()) {
			for (String name : names) {
				if (!unit.notes().hardwareCompared().contains(name)
						&& unit.samplers().stream().anyMatch(one -> one.name().equals(name))) {
					Vitrail.logger().warn("{} declares {} as a comparison sampler in one stage and "
							+ "an ordinary one in another. The sampler is the pipeline's, so the "
							+ "ordinary read goes through the comparison too, which is undefined "
							+ "here as it is under Iris", path, name);
				}
			}
		}

		COMPARED.put(pipeline, Set.copyOf(names));
		noted = true;
	}

	/**
	 * Files a rebuilt variant beside the pipeline it was rebuilt from. A reshape swaps the vertex
	 * layout and nothing a comparison depends on, so the names are the base's, shared rather than
	 * copied; nothing is filed where the base filed nothing. The warnings stay with the base's
	 * filing: they speak of the pack's text, which the variant has not changed.
	 */
	static void noteBeside(RenderPipeline variant, RenderPipeline base) {
		Set<String> names = COMPARED.get(base);
		if (names != null) {
			COMPARED.put(variant, names);
		}
	}

	/** Whether any pipeline has filed anything, asked before the per-name question is worth asking. */
	public static boolean noted() {
		return noted;
	}

	/**
	 * The names this pipeline reads through a comparison, empty for one that filed none. Asked
	 * once per pipeline a pass draws with, the map behind it being a weak one under a monitor,
	 * and the set answers for every binding of every draw after that.
	 */
	public static Set<String> compared(RenderPipeline pipeline) {
		Set<String> names = COMPARED.get(pipeline);

		return names == null ? Set.of() : names;
	}

	/** Called when the client shuts down. Nothing native is held; the registry is emptied. */
	static void close() {
		COMPARED.clear();
		noted = false;
	}
}
