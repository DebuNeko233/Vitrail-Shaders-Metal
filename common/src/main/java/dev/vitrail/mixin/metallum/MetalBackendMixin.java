package dev.vitrail.mixin.metallum;

import dev.vitrail.render.BufferBlending;

import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the Metal capability Vitrail can prove from Metallum's backend implementation without
 * making the common shader-pack engine depend on Metallum classes.
 * <p>
 * {@link Pseudo} is deliberate: this mixin targets a class of a mod that is not on Vitrail's compile
 * classpath, so the target is named as a string and this mixin contains only Minecraft and Vitrail
 * types in its bytecode-visible method signature.
 * <p>
 * This provider is intentionally narrow, and it is deliberately the only one there is. Metallum's
 * pipeline compilation configures a separate Metal blend state for every non-null
 * {@code RenderPipeline} color target, preserving target indices and their individual blend
 * functions, and that is enough to publish Vitrail's {@code PER_BUFFER_BLENDING} capability. No
 * other Metal feature is inferred here: unsupported or not-yet-bridged capabilities keep their
 * conservative defaults until their implementation is verified independently.
 * <p>
 * The answer is published only after {@code MetalBackend#createDevice} returns successfully. A
 * session whose device never came up therefore leaves no capability behind, and there is nothing
 * waiting to publish it instead: the two capabilities that used to be answered by the other
 * backend's own provider - per-attachment blending and the storage resources - went with that
 * backend, so what is left here is the whole of the question rather than one side of a race.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalBackend", remap = false)
public abstract class MetalBackendMixin {

	@Inject(method = "createDevice", at = @At("RETURN"), require = 1)
	private void vitrail$serveCapabilities(long window, ShaderSource defaultShaderSource,
			GpuDebugOptions debugOptions, Runnable criticalShaderLoader,
			CallbackInfoReturnable<GpuDevice> callback) {
		BufferBlending.serve(true);
	}
}
