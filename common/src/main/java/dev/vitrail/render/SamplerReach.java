package dev.vitrail.render;

import dev.vitrail.Vitrail;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Which of a stage's declared sampled images the entry point never reaches, so that the layout built
 * from that declaration carries a binding for the samplers the shader really reads and no others.
 * <p>
 * <strong>What this is for.</strong> A pack's programs are a handful of files over a shared include,
 * and that include declares every sampler any of them might want. The reflection a backend asks
 * SPIRV-Cross for lists the resources of the MODULE, and shaderc is run at optimisation level nought,
 * so a declaration nothing samples survives into the SPIR-V and into that list. Measured over the
 * corpus the gap is wide: the worst module declares forty-eight sampled images and reaches
 * seventeen. On a backend where a binding number is a name and nothing more that gap costs nothing.
 * On Metal it costs slots: each declared sampler counts against the sixteen a stage has, a stage that
 * runs past them is refused as Apple builds it, and a layout that is wide enough also decides whether
 * the whole pipeline goes through an argument buffer. Measured on the pack that started this, a
 * stage reading thirteen samplers out of forty-eight was refused for the sixteen it was numbered
 * over.
 * <p>
 * <strong>Why the reached set is exact and the text is not.</strong> The one other place the question
 * could be asked is the translated GLSL, and the answer there is not safe: this engine leaves every
 * {@code #if} standing for the compiler to evaluate, so the text carries the declarations and the
 * reads of branches that will be dropped, and a name can only be called unused when it appears
 * nowhere at all. That criterion over-keeps by about a factor of three. The module is past the
 * preprocessor and past the dead branches, and what it says is what the driver will see.
 * <p>
 * <strong>What this class no longer does, and where that went.</strong> It used to reach into the
 * compiler's own module and remove the unreached entries from the reflected list, through an accessor
 * and a read of the record's name. That made this engine the only thing that could perform the
 * narrowing, because only this engine had the module in hand at that moment - and it made the engine
 * speak two of the compiler's package-private types to do it. The question is now asked of the
 * backend across a seam of bytes and names: this class answers which declared names are unreached,
 * and the backend removes them from the module it built. Nothing here names a module type or a
 * record, so what is left is a pure function from SPIR-V bytes to a list of names.
 * <p>
 * <strong>Only this engine's own compiles</strong>, on the same rule as {@link RawLocals} and
 * {@link PackNames} and asked of the first of them: the game's shaders and Sodium's go through the
 * same compiler and are left alone. A storage image is never dropped whatever it reaches, because
 * only names the reflection gave as sampled images are candidates and a storage image is not one.
 * <p>
 * {@code -Dvitrail.declaredSamplers=true} answers nothing for every stage, which puts the whole
 * declared list back - what every layout carried before this existed. It is the A/B this pass is
 * measured with, one jar and two launches, and like the other two switches over the same bytes it
 * goes into the module cache's key: the two states reflect the same text into different tables, so a
 * blob built under one must never be served under the other.
 */
public final class SamplerReach {

	/** {@code SPVC_RESOURCE_TYPE_SAMPLED_IMAGE}, the type the backend's sampler list comes from. */
	private static final int SAMPLED_IMAGE = Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE;

	private static final boolean DECLARED = Boolean.getBoolean("vitrail.declaredSamplers");

	private static final AtomicLong WALKED = new AtomicLong();
	private static final AtomicLong NARROWED = new AtomicLong();
	private static final AtomicLong DROPPED = new AtomicLong();

	private SamplerReach() {
	}

	/** The word the module cache's key carries, since the state decides the tables it stores. */
	public static String cacheWord() {
		return DECLARED ? "declared-samplers" : "reached-samplers";
	}

	/**
	 * The declared sampled images of a stage that its entry point never reaches.
	 * <p>
	 * Asked once per compiled stage, on the thread doing the compile, on the SPIR-V the backend has
	 * already rewritten. The declared names come from the backend's own reflection of that module, so
	 * the two sides cannot disagree about how a resource is spelled.
	 *
	 * @param filename the debug name the compile was given, which says whose module it is
	 * @param spirv    the module to read, or null where the backend has none to offer
	 * @param declared every sampled image the module declares, by name
	 * @return the subset of those names the entry point never reaches; empty where nothing is to be
	 *         dropped, which leaves every declared name its binding
	 */
	public static List<String> unreached(String filename, ByteBuffer spirv, List<String> declared) {
		if (DECLARED || spirv == null || declared == null || declared.isEmpty()
				|| !RawLocals.ours(filename)) {
			return List.of();
		}

		WALKED.incrementAndGet();
		Set<String> unreached = unreached(spirv);
		if (unreached.isEmpty()) {
			return List.of();
		}

		// Intersected with what the backend says it declares rather than returned as the reflection
		// found it: the set is the answer to a question about that list, and handing back a name the
		// backend never listed would be answering about a module nobody asked about.
		List<String> gone = new ArrayList<>(unreached.size());
		for (String name : declared) {
			if (unreached.contains(name)) {
				gone.add(name);
			}
		}

		if (!gone.isEmpty()) {
			NARROWED.incrementAndGet();
			DROPPED.addAndGet(gone.size());
		}

		return gone;
	}

	/**
	 * One line beside the module cache's, said in BOTH states: a load served whole from the store was
	 * built under the state its blobs carry, and a reading taken on it has to be able to name that
	 * state.
	 *
	 * @param compiled how many modules the compiler built this load
	 */
	public static void say(long compiled) {
		long walked = WALKED.getAndSet(0L);
		long narrowed = NARROWED.getAndSet(0L);
		long dropped = DROPPED.getAndSet(0L);
		if (DECLARED) {
			Vitrail.logger().warn("Every DECLARED sampler given a binding, asked for by "
					+ "-Dvitrail.declaredSamplers ({} modules built this load, none walked): a pack "
					+ "whose shared include declares more samplers than a stage reads is numbered "
					+ "past Metal's sixteen slots, which on Apple hardware takes an allocated set or is "
					+ "refused",
					compiled);

			return;
		}

		Vitrail.logger().info("Samplers bound from what a module reaches: {} dropped across {} of "
						+ "the {} pack modules walked ({} modules built this load in all, the "
						+ "game's and Sodium's among them)", dropped, narrowed, walked, compiled);
	}

	/**
	 * The sampled images the module declares and never reaches.
	 * <p>
	 * Two readings of one module through one compiler: the resource list the reflection asks for,
	 * then the same list restricted to the entry point's active interface variables. The second is
	 * the module's static reach, which is what a descriptor a shader uses means and what SPIRV-Cross
	 * carries into the shader it writes.
	 * <p>
	 * A failure at any step returns nothing to drop, so a module this cannot read keeps the layout it
	 * would have had, which is the layout of every build before this one. Silently, and on purpose:
	 * there is nothing to say about a module the reflection would not read twice that the refusal it
	 * may earn on Apple will not say better.
	 */
	private static Set<String> unreached(ByteBuffer spirv) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer pointer = stack.callocPointer(1);
			if (Spvc.spvc_context_create(pointer) != 0) {
				return Set.of();
			}

			long context = pointer.get(0);
			try {
				if (Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(),
						spirv.remaining() / 4, pointer) != 0) {
					return Set.of();
				}

				long ir = pointer.get(0);
				if (Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer) != 0) {
					return Set.of();
				}

				long compiler = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources(compiler, pointer) != 0) {
					return Set.of();
				}

				List<String> declared = sampledImages(stack, pointer.get(0));
				if (declared.isEmpty()) {
					return Set.of();
				}

				if (Spvc.spvc_compiler_get_active_interface_variables(compiler, pointer) != 0) {
					return Set.of();
				}

				long active = pointer.get(0);
				if (Spvc.spvc_compiler_create_shader_resources_for_active_variables(compiler,
						pointer, active) != 0) {
					return Set.of();
				}

				Set<String> unreached = new HashSet<>(declared);
				unreached.removeAll(sampledImages(stack, pointer.get(0)));

				return unreached;
			} finally {
				Spvc.spvc_context_destroy(context);
			}
		}
	}

	private static List<String> sampledImages(MemoryStack stack, long resources) {
		PointerBuffer list = stack.callocPointer(1);
		PointerBuffer count = stack.callocPointer(1);
		if (Spvc.spvc_resources_get_resource_list_for_type(resources, SAMPLED_IMAGE, list,
				count) != 0) {
			return List.of();
		}

		int found = (int) count.get(0);
		if (found == 0) {
			return List.of();
		}

		List<String> names = new ArrayList<>(found);
		SpvcReflectedResource.Buffer reflected = SpvcReflectedResource.create(list.get(0), found);
		for (int index = 0; index < found; index++) {
			// The same string the backend's own reflection puts on the record, taken from the same
			// call, so the two spellings of one resource cannot differ.
			names.add(reflected.get(index).nameString());
		}

		return names;
	}
}
