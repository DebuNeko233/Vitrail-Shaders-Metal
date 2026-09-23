package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import dev.vitrail.cache.ModuleCache;
import dev.vitrail.glsl.LoadClock;
import dev.vitrail.render.RawLocals;
import dev.vitrail.render.ShaderDebugInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The disk store this engine keeps around one stage's compile, and the one compiler switch that
 * decides what that compile costs.
 * <p>
 * <strong>What is left here, and what moved.</strong> This mixin used to carry the whole of this
 * engine's half of a shader compile: the pack's locals zeroed and its debug names attached between
 * the compiler and the reflection, a 3D sampler let through the bind-group walk, and the store
 * around the lot. Three of those have gone.
 * <ul>
 * <li><strong>The patch moved to the backend's seam.</strong> Zeroing a pack's bare variables and
 * naming its resources have to happen between the compiler's output and the reflection that reads
 * it, which is inside a call the backend owns. The backend now offers exactly that moment
 * ({@code MetallumShaderModules}), and this engine's half is installed into it by
 * {@code compat.metallum.MetallumShaderBridge}. The state the patch reads is still taken here,
 * around the whole compile, which is what makes the key and the bytes agree.</li>
 * <li><strong>The 3D sampler allowance went with the walk it was in.</strong> It relaxed the game's
 * own bind-group construction so a pack's {@code sampler3D} would survive it. That construction is
 * reached only from the game's own pipeline build, and the road this engine draws on builds its own
 * layout - one that takes 3D dimensions as they come. So the walk this hook stood in was never
 * reached on a Metal session, and an override of a call nobody makes is worse than no override: it
 * reads as a capability this engine has exercised when it has not.</li>
 * <li><strong>The store stays.</strong> It is this engine's own performance feature - a compiled
 * module written to disk and read back on the next load - and nothing about it belongs to the
 * backend.</li>
 * </ul>
 * <p>
 * <strong>The store is around the whole method rather than inside it</strong>, and that is the
 * difference between this and caching the SPIR-V alone. {@code createIntermediary} is two costs in a
 * row, shaderc and then the SPIRV-Cross reflection reading what shaderc emitted, and the reflection is
 * the half a SPIR-V cache leaves standing. Wrapping the method skips both: the module that comes back
 * is built from the file, and nothing native runs. Nothing happens after the reflection inside the
 * method, so a module taken here is a module taken the instant it was finished.
 * <p>
 * <strong>The clock stays on this method rather than on this engine's own call sites</strong>, because
 * the game's compiler is the funnel and the call sites are not: the background warm-up goes through
 * {@code GeometryProgram}, but the terrain, every composite pass and anything a first draw or a
 * resource reload still owes goes through {@code precompilePipeline}, which lands here without a line
 * of this engine on the way. Clocking the funnel counts every road once; clocking a call site counted
 * one road and read as all of them. The span ends in a finally, so a refusal thrown by a compile still
 * costs what it cost, and a served unit is clocked like any other.
 */
@Mixin(GlslCompiler.class)
public abstract class GlslCompilerMixin {

	/**
	 * Skips the one call that asks shaderc for debug information, unless somebody asked for it back.
	 * {@link ShaderDebugInfo} carries the switch, what it costs and why the decision can only be
	 * taken here: shaderc turns the option on and has no call that turns it off, so the constructor
	 * is the only place, and the compiler it builds is the one every unit of the session goes
	 * through.
	 */
	@WrapOperation(method = "<init>", require = 1,
			at = @At(value = "INVOKE",
					target = "Lorg/lwjgl/util/shaderc/Shaderc;"
							+ "shaderc_compile_options_set_generate_debug_info(J)V"))
	private void vitrail$skipDebugInfo(long options, Operation<Void> original) {
		ShaderDebugInfo.announce();
		if (ShaderDebugInfo.asked()) {
			original.call(options);
		}
	}

	/**
	 * The text keyed on is the one this method is handed, which is two lines short of the one shaderc
	 * sees: the method splices the compiler's own two global defines in behind the version directive.
	 * They are built once in its constructor out of literals and nothing can move them, so they say
	 * the same thing about every unit and cannot tell two of them apart. The debug name is handed to
	 * {@link ModuleCache#lookup} so the rebuilt module carries this chain's identifier, and it is not
	 * hashed: that name carries the load number the disk key must not see.
	 * <p>
	 * The state is taken here and read inside, by the patch the backend's seam calls: a compile the
	 * store serves never reaches that seam at all, so a module read back from disk carries the state
	 * it was built under rather than this load's.
	 */
	@WrapMethod(method = "createIntermediary", require = 1)
	private IntermediaryShaderModule vitrail$module(String filename, String source, ShaderType type,
			Operation<IntermediaryShaderModule> original) {
		long began = System.nanoTime();
		// The state the key is hashed under is the state the bytes are patched under, taken once
		// here: a load flipping the switch while this thread is between the two would otherwise
		// store one state's module under the other's key.
		RawLocals.begin();
		try {
			String key = ModuleCache.keyOf(source, type.name());
			IntermediaryShaderModule served = ModuleCache.lookup(key, filename);
			if (served != null) {
				return served;
			}

			// Counted before the call and not after it: a unit a pack broke throws out of the
			// compile, and counting on the way back would leave that load short by exactly the
			// units somebody is reading the log to find.
			ModuleCache.building(filename);
			IntermediaryShaderModule built = original.call(filename, source, type);
			ModuleCache.store(key, built);

			return built;
		} finally {
			RawLocals.end();
			LoadClock.module(System.nanoTime() - began);
		}
	}
}
