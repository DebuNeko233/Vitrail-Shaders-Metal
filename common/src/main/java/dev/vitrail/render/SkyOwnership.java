package dev.vitrail.render;

import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The mask-only replay for a sky element that claims every pixel its mesh spans.
 * <p>
 * The ordinary geometry pipeline writes coverage from an epilogue after the pack's fragment
 * {@code main}. That is the right answer for terrain, entities and every other family where a
 * fragment the pack discards is a pixel it did not cover. It is not the sky's answer. A sky
 * element marked {@code covers} means the pack has taken ownership of the whole mesh the renderer
 * handed it; a {@code discard} in that pack fragment removes the pack's colour there, but does not
 * hand the vanilla sky back to the scene seed.
 * <p>
 * This replay therefore runs the SAME translated vertex stage over the SAME bound vertex buffer,
 * but replaces the fragment stage with one that writes only the coverage attachment. The pack's
 * fragment stage is not run a second time, so none of its colour outputs or discards can change the
 * ownership answer. The depth written to the mask is the raster depth of the claimed mesh,
 * {@code gl_FragCoord.z}, which is the value produced after the translated vertex epilogue has put
 * the pack's clip position into the backend's window. Keeping a depth rather than a boolean is what
 * still lets {@link SceneSeed} carry a game feature drawn in front of the sky.
 * <p>
 * Sky only. Nothing in this class is reached by terrain, entities, particles or the hand, so their
 * fragment discards keep cutting their coverage exactly as before.
 */
public final class SkyOwnership {

	/** One claim pipeline per pack sky pipeline, weak because the owner defines its lifetime. */
	private static final Map<RenderPipeline, Claim> CLAIMS =
			Collections.synchronizedMap(new WeakHashMap<>());

	/** The owner whose extra geometry is being drawn, used only by {@link HorizonCone}. */
	private static final ThreadLocal<RenderPipeline> CURRENT = new ThreadLocal<>();

	private SkyOwnership() {
	}

	/**
	 * Makes sure the mask-only sibling of {@code owner} exists in the device cache.
	 * <p>
	 * Called from the same prepare as the ordinary sky pipeline and on every prepare. The device
	 * call is a cache lookup after the first compile, and repeating it is intentional: a resource
	 * reload empties the device cache while the immutable pipeline objects survive it.
	 */
	static void prepare(GpuDevice device, RenderPipeline owner, String vertex, boolean covers) {
		if (!covers) {
			return;
		}

		Claim claim;
		synchronized (CLAIMS) {
			claim = CLAIMS.get(owner);
			if (claim == null) {
				try {
					claim = Claim.of(owner, vertex);
				} catch (RuntimeException e) {
					// The semantic repair is optional to serving the sky. If Blaze3D refuses the
					// sibling shape for a device-specific reason, keep the ordinary program and its
					// old fragment-written coverage rather than turning a diagnostic fix into a sky
					// outage. Latch the failure under the weak owner so the next frame does not retry.
					Vitrail.logger().warn("The sky ownership sibling of {} could not be built, so this "
							+ "sky keeps fragment-written coverage and a pack-authored discard may let "
							+ "the scene seed through", owner.getLocation(), e);
					claim = Claim.failed();
				}
				CLAIMS.put(owner, claim);
			}
		}

		claim.compile(device);
	}

	/**
	 * Replays one just-recorded sky draw into the coverage attachment and nothing else.
	 * <p>
	 * The renderer has already bound the owner's uniform block, samplers, dynamic transforms and
	 * vertex buffer. The claim pipeline carries the same bind-group layouts and vertex layout, so a
	 * pipeline switch is enough; the bindings stay standing in the pass. The owner is restored even
	 * when the draw throws because the disc may immediately draw the horizon cone with it.
	 */
	public static void claim(RenderPass pass, RenderPipeline owner, int vertices, int instances,
			int firstVertex, int firstInstance) {
		Claim claim;
		synchronized (CLAIMS) {
			claim = CLAIMS.get(owner);
		}
		if (claim == null || claim.broken) {
			return;
		}

		pass.setPipeline(claim.pipeline);
		try {
			pass.draw(vertices, instances, firstVertex, firstInstance);
		} finally {
			pass.setPipeline(owner);
		}
	}

	/**
	 * Indexed twin of {@link #claim}. The game's index buffer stays bound across the pipeline
	 * switch, so the ownership pass repeats exactly the draw the sky renderer just recorded.
	 */
	public static void claimIndexed(RenderPass pass, RenderPipeline owner, int indices, int instances,
			int firstIndex, int vertexOffset, int firstInstance) {
		Claim claim;
		synchronized (CLAIMS) {
			claim = CLAIMS.get(owner);
		}
		if (claim == null || claim.broken) {
			return;
		}

		pass.setPipeline(claim.pipeline);
		try {
			pass.drawIndexed(indices, instances, firstIndex, vertexOffset, firstInstance);
		} finally {
			pass.setPipeline(owner);
		}
	}

	/**
	 * Runs the disc's extra geometry with {@code owner} available to its draw without putting a
	 * renderer-global pipeline field on {@link HorizonCone}. Nested use is preserved rather than
	 * assumed away, even though the current sky renderer never nests one.
	 */
	public static void withOwner(RenderPipeline owner, Runnable draw) {
		RenderPipeline previous = CURRENT.get();
		CURRENT.set(owner);
		try {
			draw.run();
		} finally {
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}

	/** Replays the extra sky geometry that has just been recorded with the current owner. */
	static void claimCurrent(RenderPass pass, int vertices, int instances, int firstVertex,
			int firstInstance) {
		RenderPipeline owner = CURRENT.get();
		if (owner != null) {
			claim(pass, owner, vertices, instances, firstVertex, firstInstance);
		}
	}

	private static final class Claim {

		private final RenderPipeline pipeline;
		private final ShaderSource source;
		private boolean broken;
		private boolean reported;

		private Claim(RenderPipeline pipeline, ShaderSource source, boolean broken) {
			this.pipeline = pipeline;
			this.source = source;
			this.broken = broken;
		}

		private static Claim failed() {
			return new Claim(null, null, true);
		}

		private static Claim of(RenderPipeline owner, String vertex) {
			ColorTargetState[] states = owner.getColorTargetStates();
			if (states.length == 0 || states[states.length - 1] == null) {
				throw new IllegalStateException("A claimed sky pipeline has no coverage colour state");
			}

			int coverage = states.length - 1;
			Identifier vertexId = owner.getVertexShader();
			Identifier fragmentId = owner.getFragmentShader().withSuffix("/claim");
			String mask = """
					#version 460 core

					layout(location = %d) out float ofCoverage;

					void main() {
						ofCoverage = gl_FragCoord.z;
					}
					""".formatted(coverage);

			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(owner.getLocation().withSuffix("/claim"))
					.withVertexShader(vertexId)
					.withFragmentShader(fragmentId)
					.withCull(owner.isCull())
					.withPrimitiveTopology(owner.getPrimitiveTopology())
					.withVertexBinding(0, owner.getVertexFormatBinding(0));
			owner.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
			if (owner.getDepthStencilState() != null) {
				builder.withDepthStencilState(owner.getDepthStencilState());
			}

			for (int slot = 0; slot < coverage; slot++) {
				ColorTargetState state = states[slot];
				if (state == null) {
					builder.withUnusedColorTargetState(slot);
					continue;
				}

				// The replay runs inside the render pass the ordinary sky program already opened.
				// Blaze3D therefore requires every non-empty attachment to keep a non-null target
				// state of exactly the same format. Preserve that shape and its blend declaration,
				// but turn every colour write off so only the coverage slot can change.
				builder.withColorTargetState(slot, new ColorTargetState(
						state.blendFunction(), state.format(), ColorTargetState.WRITE_NONE));
			}
			builder.withColorTargetState(coverage, states[coverage]);

			// GeometryProgram raises the same mark around its MRT builds. Copying its per-target
			// blend declarations here can otherwise make Minecraft's builder reject this sibling
			// before the backend sees it, even though the active device supports independent blend.
			RenderPipeline pipeline;
			BufferBlending.building(true);
			try {
				pipeline = builder.build();
			} finally {
				BufferBlending.building(false);
			}
			// A Vulkan device that can run a pack geometry stage has to run the same one here too:
			// it is part of the mesh's position. On Metal the only geometry stages admitted here are
			// pass-through stages already folded away, so there is no filed stage to copy.
			GeometryStage.noteBeside(pipeline, owner);
			// A vertex stage may itself read a comparison sampler. The binding decision is keyed on
			// the pipeline object, so carry that note even though the claim fragment samples nothing.
			ShadowCompare.noteBeside(pipeline, owner);

			// Keep only the vertex identifier, not owner, in the source closure. CLAIMS is weak on
			// owner; capturing it here would make the value keep its own weak key alive forever.
			ShaderSource source = (id, type) -> {
				if (type == ShaderType.FRAGMENT) {
					return fragmentId.equals(id) ? mask : null;
				}
				return vertexId.equals(id) ? vertex : null;
			};

			return new Claim(pipeline, source, false);
		}

		private void compile(GpuDevice device) {
			if (this.broken) {
				return;
			}

			try {
				if (!device.precompilePipeline(this.pipeline, this.source).isValid()) {
					fail(null);
				}
			} catch (GpuDeviceLossException e) {
				throw e;
			} catch (RuntimeException e) {
				fail(e);
			}
		}

		private void fail(RuntimeException cause) {
			this.broken = true;
			if (!this.reported) {
				this.reported = true;
				Vitrail.logger().warn("The sky ownership pipeline {} did not compile, so this sky "
						+ "keeps fragment-written coverage and a pack-authored discard may let the "
						+ "scene seed through", this.pipeline.getLocation(), cause);
			}
		}
	}
}
