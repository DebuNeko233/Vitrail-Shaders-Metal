package dev.vitrail.glsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The vendor GLSL extensions that do not survive the road this engine takes, so that a pack testing
 * for one reads the answer this engine can honour and not the one the compiler assumes.
 * <p>
 * The compiler defines the macro of every extension it KNOWS, whether the target has it or not, and
 * it knows the AMD, Intel and NVIDIA ones. A pack written against OpenGL gates its use of them on
 * that macro, which a GL driver only defines for what the card really has: RenderPearl takes
 * {@code max3} from {@code GL_AMD_shader_trinary_minmax} when the macro is defined and falls back on
 * two {@code max} otherwise. Compiled here, the macro would be defined, the pack would ask for the
 * AMD instruction, and the module would carry an {@code OpExtInst} of an instruction set the
 * SPIR-V-to-MSL profile does not carry. The failure is not a clean refusal - measured off game, one
 * pipeline at a time, on the driver this engine used to run through, the resulting module took the
 * process down inside pipeline creation on the pack's very first pipeline.
 * <p>
 * <strong>So absence is the answer, and it is a fixed answer.</strong> The table below is every
 * vendor extension this engine counts as absent, with the reason, and it does not depend on what a
 * device reports: the road from here is SPIR-V, then MSL, then Apple's compiler, and what that road
 * can express is a property of the toolchain rather than of the GPU. An earlier shape of this asked
 * the device for a driver extension per GLSL extension and enabled the ones it answered for, which
 * is a question only the deleted backend had an answer to; on the road this engine actually takes
 * the answer was always "absent", and now it is written down as such.
 * <p>
 * The expander reads a pack's conditionals with the same answer before the compiler does: the macros
 * a stage starts with there ({@link CompilerMacros}) leave out every name {@link #absent} gives, so
 * an include under such a test is followed exactly where the compiler takes the branch.
 * <p>
 * <strong>The subgroup extensions are deliberately not in the table.</strong> They are a real part of
 * the SPIR-V-to-MSL profile, so a pack gating on them takes its subgroup path, which is what the
 * reference compilers do. The one exception is {@code GL_NV_shader_subgroup_partitioned}, whose
 * operation the profile does not have, and it is listed on that ground rather than on the ground
 * that it is a subgroup extension.
 * <p>
 * The absent names are joined into the translation cache key ({@link #key}), so a change to the
 * policy here does not serve a translation made under the old one.
 */
public final class ShaderExtensionCapabilities {

	/**
	 * Every vendor GLSL extension that counts as absent, with why. Two reasons appear: an extension
	 * glslang lowers to an instruction set the SPIR-V-to-MSL profile does not carry, and one that
	 * would additionally need a device feature nothing here enables, so enabling the extension alone
	 * would still leave a module that cannot be built.
	 */
	private static final Map<String, String> ABSENT = absent();

	private static final Set<String> NAMES =
			Collections.unmodifiableSet(new TreeSet<>(ABSENT.keySet()));

	private ShaderExtensionCapabilities() {
	}

	/**
	 * Whether a pack's {@code #extension} of this name asks for something this engine cannot honour.
	 * <p>
	 * One argument and not two any more: the answer used to depend on the stage, because a device
	 * could run subgroup operations in some stages and not others. Nothing staged survives in the
	 * table, so the stage would be read and never used.
	 */
	static boolean absent(String glslExtension) {
		return NAMES.contains(glslExtension);
	}

	/** Why this name is absent, or empty where it is not one this engine refuses. */
	static String reason(String glslExtension) {
		return ABSENT.getOrDefault(glslExtension, "");
	}

	/** The absent names, joined, for a cache key. */
	public static String key() {
		return String.join(",", NAMES);
	}

	/** The hidden spelling of a macro the compiler would define and this engine would not honour. */
	static String hidden(String glslExtension) {
		return "OF_ABSENT_" + glslExtension;
	}

	private static Map<String, String> absent() {
		Map<String, String> table = new LinkedHashMap<>();
		// Lowered by glslang to an OpExtInst set the SPIR-V-to-MSL profile does not implement, so the
		// module cannot be converted at all.
		table.put("GL_AMD_shader_trinary_minmax", "no MSL form");
		table.put("GL_AMD_gpu_shader_half_float", "no MSL form");
		table.put("GL_AMD_gpu_shader_int16", "no MSL form");
		table.put("GL_AMD_gcn_shader", "no MSL form");
		table.put("GL_AMD_shader_explicit_vertex_parameter", "no MSL form");
		table.put("GL_AMD_shader_fragment_mask", "no MSL form");
		table.put("GL_AMD_shader_image_load_store_lod", "no MSL form");
		table.put("GL_AMD_texture_gather_bias_lod", "no MSL form");
		table.put("GL_NV_shader_subgroup_partitioned", "no MSL form for the partitioned ballot");
		// No form at all, or a second extension or a device feature of its own beside it, so the
		// module would be invalid with this one alone.
		table.put("GL_NV_gpu_shader5", "no form at all on this road");
		table.put("GL_AMD_shader_ballot", "needs the subgroup ballot extension beside it, which is absent");
		table.put("GL_INTEL_shader_integer_functions2", "needs a device feature nothing here enables");
		table.put("GL_NV_shader_sm_builtins", "needs a device feature nothing here enables");
		table.put("GL_ARM_shader_core_builtins", "needs a device feature nothing here enables");

		return Collections.unmodifiableMap(table);
	}
}
