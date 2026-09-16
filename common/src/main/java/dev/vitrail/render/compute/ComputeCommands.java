package dev.vitrail.render.compute;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.Map;

/**
 * Backend command-encoder capability for one already-resolved shader-pack compute dispatch.
 * <p>
 * Resource-name policy stays above this seam. Vitrail resolves each declaration to the exact
 * buffer, texture view, sampler, ping-pong half, and dispatch moment first; the backend only binds
 * those facade objects to its compiled resource indices and dispatches the requested workgroups.
 */
public interface ComputeCommands {

	/**
	 * Dispatches exact workgroup counts with the shader's local workgroup size.
	 *
	 * @return true when this encoder/backend accepted the opaque pipeline and encoded the dispatch
	 */
	boolean vitrail$dispatchCompute(
			Object pipeline,
			Map<String, GpuBufferSlice> buffers,
			Map<String, GpuTextureView> textures,
			Map<String, GpuSampler> samplers,
			int groupsX,
			int groupsY,
			int groupsZ,
			int localX,
			int localY,
			int localZ);
}
