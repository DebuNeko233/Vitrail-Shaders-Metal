package dev.vitrail.render.storage;

import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Backend commands Vitrail needs for storage-image housekeeping outside a render pass.
 * <p>
 * Policy deliberately stays with {@link StorageImages}: this interface does not decide which
 * image is cleared, when a volume follows the camera, or whether a scratch copy is necessary.
 * It only records the clear/copy operation against resources Vitrail already chose.
 */
public interface StorageImageCommands {

	/** Clears the complete storage texture to numeric zero. */
	boolean vitrail$clearStorageImage(GpuTexture texture, int dimensions);

	/** Copies one exact 1D/2D/3D texel box between two storage textures. */
	boolean vitrail$copyStorageImageRegion(
			GpuTexture source,
			GpuTexture destination,
			int sourceX,
			int sourceY,
			int sourceZ,
			int destinationX,
			int destinationY,
			int destinationZ,
			int width,
			int height,
			int depth);
}
