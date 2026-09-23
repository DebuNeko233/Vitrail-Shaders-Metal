package dev.vitrail.render.storage;

import com.mojang.blaze3d.buffers.GpuBuffer;

/**
 * The one operation Minecraft 26.2's public GPU facade cannot express for a shader-storage buffer:
 * creating a buffer whose backend may expose it to a storage declaration.
 * <p>
 * Binding deliberately does not live here. A storage block still travels through
 * {@code RenderPass.setUniform}: the public pass facade only requires the slice offset to satisfy
 * the device alignment, and each backend decides from the compiled shader resource whether the
 * buffer is a uniform or storage resource. Keeping that public road means shader-pack code never
 * sees a raw buffer handle, a descriptor set, or a Metal argument index.
 * <p>
 * The returned allocation is a normal {@link GpuBuffer} so ownership and deferred destruction stay
 * with the backend implementation already used by every other Minecraft buffer.
 */
public interface StorageBufferBackend {

	/**
	 * Allocates a zero-initialized shader-storage buffer of {@code bytes} bytes.
	 *
	 * @throws RuntimeException when the backend cannot provide the requested allocation; callers
	 *                          must refuse the dependent shader rather than substitute a smaller
	 *                          buffer because shader writes are allowed across the declared extent
	 */
	GpuBuffer vitrail$createStorageBuffer(long bytes);
}
