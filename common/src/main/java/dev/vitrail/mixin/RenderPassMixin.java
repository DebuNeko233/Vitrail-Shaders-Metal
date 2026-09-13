package dev.vitrail.mixin;

import dev.vitrail.render.GeometryHold;
import dev.vitrail.render.ParticleDraw;
import dev.vitrail.render.storage.StorageImages;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves a geometry pass open when the next program still writes the same images, and answers for
 * every draw recorded into the pass a particle group holds, whoever records it.
 * <p>
 * Closing would end the backend pass; {@link GeometryHold} is what decides that the FBO has not
 * moved. The particle hooks live on the pass and not on the renderer for the reason
 * {@link QuadParticleFeatureRendererMixin} gives: the game's own draws go through
 * {@code drawLayers}, but a mod may record draws of its own into the same pass from a handler of
 * its own, and a hook on the renderer's method sees none of those. {@link ParticleDraw} scopes
 * both to the one pass the group opened and stays out of the way on every other pass of the frame.
 * <p>
 * Storage-image facade replacement also lives at this common pass boundary. Vulkan keeps the
 * placeholder view and replaces its native descriptor later; a backend that allocated the image as
 * a real {@link com.mojang.blaze3d.textures.GpuTexture} instead substitutes that view here, before
 * the backend sees it. The original invocation is always called so other wrappers and the particle
 * tail hook keep their ordinary ordering.
 */
@Mixin(RenderPass.class)
public abstract class RenderPassMixin {

	@Inject(method = "close", at = @At("HEAD"), cancellable = true, require = 1)
	private void vitrail$keep(CallbackInfo callback) {
		if (GeometryHold.keep((RenderPass) (Object) this)) {
			callback.cancel();
		}
	}

	@ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, require = 1)
	private RenderPipeline vitrail$particlePipeline(RenderPipeline pipeline) {
		return ParticleDraw.pipeline((RenderPass) (Object) this, pipeline);
	}

	@WrapOperation(method = "bindTexture", require = 1,
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/systems/RenderPassBackend;bindTexture("
							+ "Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;"
							+ "Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
	private void vitrail$storageImage(RenderPassBackend backend, String name, GpuTextureView view,
			GpuSampler sampler, Operation<Void> original) {
		GpuTextureView storage = StorageImages.facadeView(name);
		original.call(backend, name, storage == null ? view : storage, sampler);
	}

	@Inject(method = "bindTexture", at = @At("TAIL"), require = 1)
	private void vitrail$particleAtlas(String name, GpuTextureView view, GpuSampler sampler,
			CallbackInfo callback) {
		ParticleDraw.texture((RenderPass) (Object) this, name, view, sampler);
	}
}
