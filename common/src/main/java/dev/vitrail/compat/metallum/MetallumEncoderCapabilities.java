package dev.vitrail.compat.metallum;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.vitrail.render.AttachmentCommands;
import dev.vitrail.render.MipmapCommands;
import dev.vitrail.render.ScaleCommands;
import dev.vitrail.render.compute.ComputeCommands;
import dev.vitrail.render.storage.StorageImageCommands;

import java.util.Map;

/**
 * The five capabilities this engine adds, answered for a backend that does not carry them itself.
 * <p>
 * It holds only the backend, and every operation goes through the compat bridges - the stable flat surface -
 * so nothing here knows which generation is executing. It exists so that the injected mixin which used to put
 * these capabilities onto Metallum's encoder can be deleted: the capability is supplied by this adapter rather
 * than by a target class name, and a class that moves no longer takes these five methods with it.
 */
public final class MetallumEncoderCapabilities implements MipmapCommands, StorageImageCommands,
		ComputeCommands, AttachmentCommands, ScaleCommands {

	private final Object backend;

	public MetallumEncoderCapabilities(Object backend) {
		this.backend = backend;
	}

	@Override
	public boolean vitrail$generateMipmaps(GpuTexture texture) {
		// The native path first, then the depth fallback this engine has always used for D32 chains.
		return MetallumFrameBridge.generateMipmaps(backend, texture)
				|| MetallumDepthMipmapBridge.generate(backend, texture);
	}

	@Override
	public boolean vitrail$clearStorageImage(GpuTexture texture, int dimensions) {
		return MetallumFrameBridge.clearStorageTexture(backend, texture, dimensions);
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
		return MetallumFrameBridge.copyStorageTextureRegion(backend, source, destination,
				sourceX, sourceY, sourceZ, destinationX, destinationY, destinationZ, width, height, depth);
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
		return MetallumComputeBridge.dispatch(backend, pipeline, buffers, textures, samplers,
				groupsX, groupsY, groupsZ, localX, localY, localZ);
	}

	@Override
	public void vitrail$setNextPassContents(boolean[] readAfterwards, boolean[] overwritten) {
		MetallumAttachmentBridge.setNextPassContents(backend, readAfterwards, overwritten);
	}

	@Override
	public void vitrail$setNextPassReadsStorageImage(boolean reads) {
		MetallumAttachmentBridge.setNextPassReadsStorageImage(backend, reads);
	}

	@Override
	public boolean vitrail$metalFxAvailable() {
		return MetallumScaleBridge.available(backend);
	}

	@Override
	public boolean vitrail$metalFxScale(GpuTextureView from, GpuTextureView to, int contentWidth,
			int contentHeight) {
		return MetallumScaleBridge.scale(backend, from, to, contentWidth, contentHeight);
	}
}
