package dev.vitrail.mixin.metallum;

import dev.vitrail.compat.metallum.MetallumAttachmentBridge;
import dev.vitrail.compat.metallum.MetallumComputeBridge;
import dev.vitrail.compat.metallum.MetallumDepthMipmapBridge;
import dev.vitrail.compat.metallum.MetallumScaleBridge;
import dev.vitrail.render.AttachmentCommands;
import dev.vitrail.render.MipmapCommands;
import dev.vitrail.render.ScaleCommands;
import dev.vitrail.render.compute.ComputeCommands;
import dev.vitrail.render.storage.StorageImageCommands;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * Adapts Metallum's backend-native texture and compute commands to Vitrail without a compile-time
 * Metallum dependency. Metallum owns Metal encoder/fence rules; Vitrail owns when shader-pack
 * resources are cleared, copied, mipmapped, or dispatched and what each declared resource means.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalCommandEncoder", remap = false)
public abstract class MetalCommandEncoderMixin implements MipmapCommands, StorageImageCommands,
		ComputeCommands, AttachmentCommands, ScaleCommands {

	@Override
	public boolean vitrail$metalFxAvailable() {
		return MetallumScaleBridge.available(this);
	}

	@Override
	public boolean vitrail$metalFxScale(
			GpuTextureView from, GpuTextureView to, int contentWidth, int contentHeight) {
		return MetallumScaleBridge.scale(this, from, to, contentWidth, contentHeight);
	}

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
		return generateMipmaps(texture) || MetallumDepthMipmapBridge.generate(this, texture);
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

	@Override
	public void vitrail$setNextPassReadsStorageImage(boolean reads) {
		MetallumAttachmentBridge.setNextPassReadsStorageImage(this, reads);
	}

	@Override
	public void vitrail$setNextPassContents(boolean[] readAfterwards, boolean[] overwritten) {
		MetallumAttachmentBridge.setNextPassContents(this, readAfterwards, overwritten);
	}

	@Override
	public boolean vitrail$dispatchCompute(
			Object pipeline,
			Map<String, GpuBufferSlice> buffers,
			Map<String, GpuTextureView> textures,
			Map<String, GpuSampler> samplers,
			int groupsX,
			int groupsY,
			int groupsZ,
			int localX,
			int localY,
			int localZ) {
		return MetallumComputeBridge.dispatch(this, pipeline, buffers, textures, samplers,
				groupsX, groupsY, groupsZ, localX, localY, localZ);
	}
}
