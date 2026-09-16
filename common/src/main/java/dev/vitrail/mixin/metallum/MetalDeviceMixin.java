package dev.vitrail.mixin.metallum;

import dev.vitrail.compat.metallum.MetallumComputeBridge;
import dev.vitrail.compat.metallum.MetallumTextureBridge;
import dev.vitrail.render.StalePipelines;
import dev.vitrail.render.compute.ComputeDeviceBackend;
import dev.vitrail.render.storage.ShaderWritableTextureBackend;
import dev.vitrail.render.storage.StorageBufferBackend;
import dev.vitrail.render.storage.StorageImageBackend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Predicate;

/**
 * Adapts the small backend operations Vitrail needs from Metallum without putting Metallum on the
 * common compile classpath.
 * <p>
 * Selective pipeline eviction keeps native lifetime inside Metallum: a matching cache entry leaves
 * the map immediately while its compiled Metal object waits for the next full cache clear and the
 * GPU-completion wait guarding that release point. Storage resources likewise cross the seam only
 * through Minecraft's {@link GpuBuffer}/{@link GpuTexture} facades; compute pipelines cross as
 * opaque backend-owned tokens rather than MTL handles or Metal argument indices.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalDevice", remap = false)
public abstract class MetalDeviceMixin implements StalePipelines, StorageBufferBackend,
		StorageImageBackend, ShaderWritableTextureBackend, ComputeDeviceBackend {

	@Shadow(remap = false)
	public abstract List<RenderPipeline> evictCachedPipelines(Predicate<RenderPipeline> predicate);

	@Shadow(remap = false)
	public abstract Object createStorageBufferResource(long size);

	@Shadow(remap = false)
	public abstract GpuTexture createStorageTextureResource(String label, GpuFormat format,
			int width, int height, int depth, int dimensions);

	@Override
	public List<RenderPipeline> vitrail$dropPipelines(Predicate<RenderPipeline> predicate) {
		return evictCachedPipelines(predicate);
	}

	@Override
	public GpuBuffer vitrail$createStorageBuffer(long bytes) {
		Object resource = createStorageBufferResource(bytes);
		if (resource instanceof GpuBuffer buffer) {
			return buffer;
		}

		throw new IllegalStateException("Metallum returned a non-GpuBuffer storage resource");
	}

	@Override
	public GpuTexture vitrail$createStorageImage(String label, GpuFormat format, int width,
			int height, int depth, int dimensions) {
		return createStorageTextureResource(label, format, width, height, depth, dimensions);
	}

	@Override
	public GpuTexture vitrail$createShaderWritableTexture(String label, int usage, GpuFormat format,
			int width, int height, int depthOrLayers, int mipLevels) {
		return MetallumTextureBridge.createShaderWritable(this, label, usage, format,
				width, height, depthOrLayers, mipLevels);
	}

	@Override
	public Object vitrail$compileCompute(String label, ByteBuffer spirv) {
		return MetallumComputeBridge.compile(this, label, spirv);
	}

	@Override
	public void vitrail$closeCompute(Object pipeline) {
		MetallumComputeBridge.close(pipeline);
	}
}
