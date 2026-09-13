package dev.vitrail.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;

/**
 * Vulkan-only adoption of a compiled pipeline produced by Vitrail's background family warm-up.
 * <p>
 * This is intentionally separate from {@link StalePipelines}. Selective cache eviction is required
 * by any backend whose compiled pipeline bakes mutable vertex-layout state; adoption is only an
 * optimization for the existing Vulkan worker path, which builds a {@link VulkanRenderPipeline}
 * directly with a worker-owned compiler. Metal keeps the safe first-draw compile fallback and does
 * not have to imitate this optimization to implement cache correctness.
 */
public interface VulkanPipelineAdoption {

	/**
	 * Offers a worker-compiled Vulkan pipeline to the render-thread cache.
	 *
	 * @return false when the cache already owns a compiled pipeline for {@code pipeline}, in which
	 *         case the caller still owns {@code compiled}
	 */
	boolean vitrail$adopt(RenderPipeline pipeline, VulkanRenderPipeline compiled);
}
