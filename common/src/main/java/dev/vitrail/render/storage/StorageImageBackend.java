package dev.vitrail.render.storage;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Backend allocation capability for shader-writable one-, two-, and three-dimensional images.
 * <p>
 * Minecraft 26.2 has no storage-image usage bit and its public texture creation path cannot say
 * that {@code depthOrLayers} is a true 3D depth instead of array layers. Vitrail therefore asks a
 * backend for exactly this missing allocation fact while continuing to carry the result as an
 * ordinary {@link GpuTexture}. Native image handles and argument/descriptor indices stay behind
 * the backend boundary.
 */
public interface StorageImageBackend {

	/**
	 * Allocates one shader-readable and shader-writable texture with a single mip level.
	 *
	 * @param label debugger label; an empty label is allowed
	 * @param format resolved Minecraft format of the storage image
	 * @param width texel width, always positive
	 * @param height texel height, one for a 1D image
	 * @param depth texel depth, one unless {@code dimensions == 3}
	 * @param dimensions logical texture dimensionality: 1, 2, or 3
	 */
	GpuTexture vitrail$createStorageImage(String label, GpuFormat format, int width, int height,
			int depth, int dimensions);
}
