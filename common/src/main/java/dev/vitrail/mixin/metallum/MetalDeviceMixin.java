package dev.vitrail.mixin.metallum;

import dev.vitrail.render.StalePipelines;
import dev.vitrail.render.storage.StorageBufferBackend;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;

/**
 * Adapts the small backend operations Vitrail needs from Metallum without putting Metallum on the
 * common compile classpath.
 * <p>
 * Selective pipeline eviction keeps native lifetime inside Metallum: a matching cache entry leaves
 * the map immediately while its compiled Metal object waits for the next full cache clear and the
 * GPU-completion wait guarding that release point. Storage-buffer allocation likewise crosses the
 * seam only as Minecraft's own {@link GpuBuffer}; no {@code MTLBuffer} or Metal binding index is
 * exposed to Vitrail.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalDevice", remap = false)
public abstract class MetalDeviceMixin implements StalePipelines, StorageBufferBackend {

	@Shadow(remap = false)
	public abstract List<RenderPipeline> evictCachedPipelines(Predicate<RenderPipeline> predicate);

	@Shadow(remap = false)
	public abstract Object createStorageBufferResource(long size);

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
}
