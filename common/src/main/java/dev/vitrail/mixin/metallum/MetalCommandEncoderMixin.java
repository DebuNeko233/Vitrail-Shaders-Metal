package dev.vitrail.mixin.metallum;

import dev.vitrail.render.MipmapCommands;
import dev.vitrail.render.storage.StorageImageCommands;

import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adapts Metallum's backend-native texture commands to Vitrail without a compile-time Metallum
 * dependency. Metallum owns Metal encoder/fence rules; Vitrail owns when shader-pack resources are
 * cleared, copied, or mipmapped.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalCommandEncoder", remap = false)
public abstract class MetalCommandEncoderMixin implements MipmapCommands, StorageImageCommands {

	@Shadow(remap = false)
	public abstract boolean generateMipmaps(GpuTexture texture);

	@Shadow(remap = false)
	public abstract boolean clearStorageTexture(GpuTexture texture, int dimensions);

	@Shadow(remap = false)
	public abstract boolean copyStorageTextureRegion(
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

	@Override
	public boolean vitrail$generateMipmaps(GpuTexture texture) {
		return generateMipmaps(texture);
	}

	@Override
	public boolean vitrail$clearStorageImage(GpuTexture texture, int dimensions) {
		return clearStorageTexture(texture, dimensions);
	}

	@Override
	public boolean vitrail$copyStorageImageRegion(
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
			int depth) {
		return copyStorageTextureRegion(source, destination,
				sourceX, sourceY, sourceZ,
				destinationX, destinationY, destinationZ,
				width, height, depth);
	}
}
