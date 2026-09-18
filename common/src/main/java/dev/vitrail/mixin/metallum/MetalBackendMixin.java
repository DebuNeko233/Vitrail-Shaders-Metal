package dev.vitrail.mixin.metallum;

import dev.vitrail.glsl.VendorExtensions;
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
 * Publishes the Metal capabilities Vitrail can prove from Metallum's backend implementation without
 * making the common shader-pack engine depend on Metallum classes.
 * <p>
 * {@link Pseudo} is deliberate: Metallum is an optional backend, so its classes are not on Vitrail's
 * compile classpath and may not exist at runtime. The target is therefore named as a string and this
 * mixin contains only Minecraft/Vitrail types in its bytecode-visible method signature.
 * <p>
 * This provider is intentionally narrow. The MRT foundation in Metallum configures a separate Metal
 * blend state for every non-null {@code RenderPipeline} color target, preserving target indices and
 * their individual blend functions. That is enough to publish Vitrail's
 * {@code PER_BUFFER_BLENDING} capability. No other Metal feature is inferred here: unsupported or
 * not-yet-bridged capabilities keep their conservative defaults until their implementation is
 * verified independently.
 * <p>
 * The answer is published only after {@code MetalBackend#createDevice} returns successfully. If
 * Metal device creation fails and Minecraft falls back to Vulkan, this mixin leaves no Metal fact
 * behind and the Vulkan provider publishes the capabilities of the device that actually won.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalBackend", remap = false)
public abstract class MetalBackendMixin {

	@Inject(method = "createDevice", at = @At("RETURN"), require = 1)
	private void vitrail$serveCapabilities(long window, ShaderSource defaultShaderSource,
			GpuDebugOptions debugOptions, Runnable criticalShaderLoader,
			CallbackInfoReturnable<GpuDevice> callback) {
		BufferBlending.serve(true);
		VendorExtensions.serveMetal(true);
	}
}
