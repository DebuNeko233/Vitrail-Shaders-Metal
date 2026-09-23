package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.vitrail.render.BufferBlending;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Lets one pipeline carry a different blend function on each colour target it writes, where the
 * device can really keep them apart.
 * <p>
 * The builder walks the active states and throws on the first two that name different functions
 * ({@code RenderPipeline:457-468}, the throw at {@code :464}). It is right to. The game's OpenGL road
 * turns blending on and off per buffer but sets the function once for the whole draw,
 * {@code _enableBlend(i)} beside an unindexed {@code _blendFuncSeparate}
 * ({@code GlCommandEncoder:842-858}), so the last target's function would quietly stand on all of
 * them. On this engine's road the shape wanted is already there: Metallum's pipeline compilation
 * fills one blend state per colour target from that target's own state, preserving indices and their
 * individual functions, so the permission rather than the mechanism is what has to be lifted.
 * <p>
 * So this is the second half of the permission {@link BufferBlending} describes, the backend's
 * per-target blend support being the first: {@code MetalBackendMixin} answers it once the Metal
 * device exists, and this lifts the refusal standing in front of it.
 * <p>
 * <strong>The builder below is the one every pipeline of the process is built through</strong>, the
 * game's own and every other mod's, none of which asked for anything. So the backend's answer is not
 * by itself a narrow enough condition to lift on, and {@code parting()} is the two together: a Metal
 * device that parts its attachments, and the build being one of this engine's own, which
 * {@code GeometryProgram.part} marks on its thread for the length of the call. Everything else keeps
 * the game's own word - the OpenGL road, and every builder in the process this engine is not standing
 * in - and so does a build of ours on a device that did not answer, which is instead given the whole
 * program function on every attachment ({@code GeometryProgram.state}).
 */
@Mixin(RenderPipeline.Builder.class)
public abstract class RenderPipelineBuilderMixin {

	/**
	 * Two functions read as one where this engine is building and the backend parts its attachments,
	 * which is the comparison the refusal hangs off and the only thing here that moves.
	 */
	@WrapOperation(method = "build()Lcom/mojang/blaze3d/pipeline/RenderPipeline;", require = 1,
			at = @At(value = "INVOKE", target = "Ljava/util/Optional;equals(Ljava/lang/Object;)Z"))
	private boolean vitrail$blendApart(Optional<BlendFunction> current, Object last,
			Operation<Boolean> original) {
		return original.call(current, last) || BufferBlending.parting();
	}
}
