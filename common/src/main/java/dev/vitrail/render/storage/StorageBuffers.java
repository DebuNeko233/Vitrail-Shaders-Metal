package dev.vitrail.render.storage;

import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.pack.model.BufferObject;
import dev.vitrail.pack.texture.CustomStorage;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shader storage buffers a pack declared with {@code bufferObject.N}.
 * <p>
 * Minecraft 26.2 has no storage-buffer usage bit, so there is exactly one road and it goes through
 * the backend: a backend implementing {@link StorageBufferBackend} creates a real backend-owned
 * {@link GpuBuffer}, and that buffer travels through the ordinary public
 * {@link RenderPass#setUniform} call while the backend's compiled shader resource decides that the
 * binding is storage rather than uniform. Allocation, zeroing at birth and destruction are the
 * backend's; the pack layer keeps the declaration, the size and the lifetime it already owned.
 * <p>
 * Nothing native crosses the seam. A second road used to allocate here directly through the
 * driver's own allocator, hand a placeholder uniform slice to {@code setUniform} and let descriptor
 * rewriting swap the real buffer in behind it; both the placeholder and the swap are gone, because
 * the only backend this engine draws on answers {@link StorageBufferBackend} directly and the
 * allocation, the zeroing at birth and the destruction are all its own.
 *
 * @see <a href="https://github.com/IrisShaders/Iris">Iris ShaderStorageBuffer, LGPL-3.0</a>
 */
public final class StorageBuffers implements AutoCloseable {

	private static volatile StorageBuffers current = none();

	private final BufferObject.Reading declared;
	private final List<BackendAllocated> backendAllocated = new ArrayList<>();

	/** The backend-owned allocation under the declared index the pack names it by. */
	private Map<Integer, GpuBuffer> backendBindings = Map.of();
	private int lastWidth;
	private int lastHeight;

	public StorageBuffers(BufferObject.Reading declared) {
		this.declared = declared;
	}

	static StorageBuffers none() {
		return new StorageBuffers(BufferObject.Reading.empty());
	}

	/** Whether this name is a storage buffer this engine serves, allocated or about to be. */
	public static boolean named(String name) {
		return CustomStorage.named(name);
	}

	/**
	 * The backend-owned Minecraft buffer slice for this GLSL name, or null where nothing is allocated
	 * under it. Compute backends use this facade rather than a native buffer handle, which is the same
	 * ownership boundary render-pass binding uses.
	 */
	public static GpuBufferSlice facadeSlice(String name) {
		GpuBuffer buffer = current.backendBuffer(name);
		return buffer == null ? null : buffer.slice();
	}

	private GpuBuffer backendBuffer(String name) {
		if (this.backendBindings.isEmpty()) {
			return null;
		}

		int index = CustomStorage.indexOf(name);
		return index < 0 ? null : this.backendBindings.get(index);
	}

	private void rebindBackend() {
		Map<Integer, GpuBuffer> bound = new HashMap<>();
		for (BackendAllocated buffer : this.backendAllocated) {
			bound.putIfAbsent(buffer.declared.index(), buffer.buffer);
		}

		this.backendBindings = Map.copyOf(bound);
	}

	void install() {
		current = this;
	}

	/**
	 * Allocates every absolute buffer once, and rebuilds the relative ones when the screen moves.
	 * <p>
	 * A failure is thrown rather than skipped: a tiny stand-in would let the pack compile and then
	 * hang the GPU on the first out-of-range write. A device whose backend does not serve
	 * {@link StorageBufferBackend} is one this engine cannot draw a pack with at all, and saying so
	 * here is better than a pack that silently reads nothing.
	 */
	public void ensure(int screenWidth, int screenHeight) {
		install();
		if (this.declared.buffers().isEmpty()) {
			return;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		boolean first = this.backendAllocated.isEmpty();
		boolean resized = screenWidth != this.lastWidth || screenHeight != this.lastHeight;
		if (!first && !resized) {
			return;
		}

		GpuDeviceBackend deviceBackend = ((GpuDeviceAccessor) device).vitrail$backend();
		if (!(deviceBackend instanceof StorageBufferBackend storageBackend)) {
			throw new IllegalStateException("The " + device.getDeviceInfo().backendName()
					+ " backend does not serve shader storage buffers, which "
					+ this.declared.buffers().size() + " of this pack declares");
		}

		// The backend owns native allocation details and returns each buffer already initialized to
		// zero, so there is no transfer command and no separate zeroing pass on this road.
		try {
			allocateBackend(storageBackend, first, resized, screenWidth, screenHeight);
		} finally {
			rebindBackend();
		}

		this.lastWidth = screenWidth;
		this.lastHeight = screenHeight;
	}

	private void allocateBackend(StorageBufferBackend backend, boolean first, boolean resized,
			int screenWidth, int screenHeight) {
		if (first) {
			for (BufferObject buffer : this.declared.buffers()) {
				if (buffer.relative()) {
					continue;
				}

				long bytes = aligned(buffer.size());
				this.backendAllocated.add(BackendAllocated.create(backend, buffer, bytes));
				Vitrail.logger().info("storage buffer {} through the active GPU backend", buffer.describe());
			}
		}

		if (first || resized) {
			List<BackendAllocated> kept = new ArrayList<>();
			for (BackendAllocated buffer : this.backendAllocated) {
				if (buffer.relative) {
					buffer.close();
				} else {
					kept.add(buffer);
				}
			}

			this.backendAllocated.clear();
			this.backendAllocated.addAll(kept);
			for (BufferObject buffer : this.declared.buffers()) {
				if (!buffer.relative()) {
					continue;
				}

				long bytes = aligned((long) (screenWidth * buffer.scaleX())
						* (long) (screenHeight * buffer.scaleY())
						* buffer.size());
				bytes = Math.max(bytes, 4L);
				this.backendAllocated.add(BackendAllocated.create(backend, buffer, bytes));
				Vitrail.logger().info("storage buffer {} at {} bytes through the active GPU backend",
						buffer.describe(), bytes);
			}
		}
	}

	/**
	 * Binds every storage-buffer name this program's layout carries.
	 * <p>
	 * A backend-owned allocation is itself handed to {@link RenderPass#setUniform}; the backend's
	 * compiled resource kind is what makes that buffer a storage binding rather than a uniform one.
	 * A name with nothing allocated under it is left unbound rather than given a placeholder, so a
	 * missing allocation shows up as a validation error naming the binding instead of as a shader
	 * reading sixteen bytes of nothing.
	 */
	public static void bind(RenderPass pass, List<String> names) {
		for (String name : names) {
			GpuBuffer backend = current.backendBuffer(name);
			if (backend != null) {
				pass.setUniform(name, backend);
			}
		}
	}

	private static long aligned(long size) {
		return Math.max(4L, (size + 3L) & ~3L);
	}

	@Override
	public void close() {
		if (current == this) {
			current = none();
		}

		this.backendAllocated.forEach(BackendAllocated::close);
		this.backendAllocated.clear();
		this.backendBindings = Map.of();
		this.lastWidth = 0;
		this.lastHeight = 0;
	}

	private static final class BackendAllocated {

		private final BufferObject declared;
		private final boolean relative;
		private final GpuBuffer buffer;

		private BackendAllocated(BufferObject declared, GpuBuffer buffer) {
			this.declared = declared;
			this.relative = declared.relative();
			this.buffer = buffer;
		}

		private static BackendAllocated create(StorageBufferBackend backend, BufferObject declared,
				long bytes) {
			GpuBuffer buffer = backend.vitrail$createStorageBuffer(bytes);
			if (buffer.size() < bytes) {
				buffer.close();
				throw new IllegalStateException("Storage backend returned " + buffer.size()
						+ " bytes for a " + bytes + " byte storage buffer");
			}
			return new BackendAllocated(declared, buffer);
		}

		private void close() {
			this.buffer.close();
		}
	}
}
