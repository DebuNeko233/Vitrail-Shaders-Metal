package dev.vitrail.render;

import dev.vitrail.cache.ModuleCache;
import dev.vitrail.glsl.LoadClock;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.SharedMemory;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.pack.model.TextureStage;
import dev.vitrail.pack.target.TargetSchedule;
import dev.vitrail.render.compute.ComputeCommands;
import dev.vitrail.render.compute.ComputeDeviceBackend;
import dev.vitrail.render.compute.ComputeResources;
import dev.vitrail.render.storage.StorageBufferBackend;
import dev.vitrail.Vitrail;

import com.mojang.blaze3d.GpuDeviceLossException;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One shader-pack compute pass on a backend implementing Vitrail's facade compute seam.
 * <p>
 * Vulkan deliberately does not use this class yet; its established module/layout/descriptor path
 * stays untouched in {@link PackCompute}. This class is the parallel backend-neutral road used by
 * Metallum: shader-pack scheduling and name resolution remain in Vitrail while native pipeline,
 * argument binding, synchronization and destruction stay behind {@link ComputeDeviceBackend} and
 * {@link ComputeCommands}.
 */
final class BackendComputePass implements AutoCloseable {

	private static final int SHADERC_VULKAN_1_2 = 4202496;
	private static final int SHADERC_COMPUTE = 2;
	private static final String MODULE_CACHE_STAGE = "COMPUTE/shaderc-opt2-vulkan1.2";
	private static final Pattern LOCAL_AXIS = Pattern.compile("\\blocal_size_([xyz])\\s*=\\s*");
	private static final Pattern LOCAL_LITERAL = Pattern.compile("\\d+");

	private final PackProgram.Compute compute;
	private final PackUniforms uniforms;
	private final String path;
	private final String label;
	private final String program;
	private final TextureStage textureStage;

	private MappableRingBuffer block;
	private GpuBuffer sharedMemory;
	private long sharedMemoryBytes;
	private ComputeDeviceBackend owner;
	private Object pipeline;
	private ComputeResources resources;
	private LocalSize localSize;
	private boolean compiled;
	private boolean announced;

	BackendComputePass(PackProgram.Compute compute, PackUniforms uniforms, String path, String label,
			String program, TextureStage textureStage) {
		this.compute = compute;
		this.uniforms = uniforms;
		this.path = path;
		this.label = label;
		this.program = program;
		this.textureStage = textureStage;
	}

	void dispatch(ComputeDeviceBackend deviceBackend, ComputeCommands commands, PackValues values,
			ColorTargets targets, int width, int height, TargetSchedule.Bound step,
			GpuTextureView depth, GpuTextureView distant) {
		if (!this.compiled) {
			compile(deviceBackend);
		}
		if (this.pipeline == null || this.resources == null || this.localSize == null) {
			return;
		}
		if (this.owner != deviceBackend) {
			throw new IllegalStateException("Compute backend changed after " + this.path + " was compiled");
		}

		GpuBufferSlice uniformBlock = writeBlock(values);
		PackComputeBindings.Resolved bound = PackComputeBindings.resolve(
				this.resources,
				uniformBlock,
				this.compute.loaded().samplers(),
				this.textureStage,
				this.program,
				targets,
				step,
				depth,
				distant,
				transientBuffers());
		int[] groups = this.compute.groupsAt(width, height);
		if (!commands.vitrail$dispatchCompute(
				this.pipeline,
				bound.buffers(),
				bound.textures(),
				bound.samplers(),
				groups[0], groups[1], groups[2],
				this.localSize.x(), this.localSize.y(), this.localSize.z())) {
			throw new IllegalStateException("Active command encoder refused compute " + this.path);
		}
		if (this.block != null) {
			this.block.rotate();
		}
		// Acceptance of a zero-sized dispatch is a no-op, not runtime evidence. Announce
		// each program only after the backend has accepted work with non-zero dimensions.
		if (!this.announced && groups[0] > 0 && groups[1] > 0 && groups[2] > 0) {
			this.announced = true;
			Vitrail.logger().info("Dispatched compute {} through the active backend: "
					+ "groups=({}, {}, {}), local=({}, {}, {})", this.path,
					groups[0], groups[1], groups[2],
					this.localSize.x(), this.localSize.y(), this.localSize.z());
		}
	}

	private Map<String, GpuBufferSlice> transientBuffers() {
		return this.sharedMemory == null
				? Map.of()
				: Map.of(SharedMemory.BLOCK, this.sharedMemory.slice(0, this.sharedMemoryBytes));
	}

	private void compile(ComputeDeviceBackend backend) {
		this.compiled = true;
		TranslatedUnit unit = this.compute.loaded().program().stages().get(ProgramStage.COMPUTE);
		if (unit == null) {
			return;
		}

		String source = unit.text();
		String preprocessed = preprocess(source);
		if (preprocessed == null) {
			Vitrail.logger().warn("compute {} is not dispatched on the active backend: shaderc could "
					+ "not preprocess it for local-size validation", this.path);
			return;
		}
		this.localSize = LocalSize.read(preprocessed, this.path);

		if (SharedMemory.mentioned(source)) {
			SharedMemory.Reading shared = SharedMemory.read(preprocessed);
			if (shared.unread() != null) {
				Vitrail.logger().warn("compute {} is not dispatched on native Metal: its shared "
						+ "declaration cannot be sized reliably: {}", this.path, shared.unread());
				this.localSize = null;
				return;
			}
			if (shared.over()) {
				// A storage buffer is one copy for the whole dispatch, while GLSL shared memory is
				// one copy per work group. They are equivalent only for a fixed single group. This
				// is the same condition the established MoltenVK road uses; keeping it here avoids
				// silently changing a multi-group reduction merely to fit Metal's memory limit.
				if (this.compute.groupsX() != 1 || this.compute.groupsY() != 1
						|| this.compute.groupsZ() != 1) {
					Vitrail.logger().warn("compute {} is not dispatched on native Metal: it asks for {} "
							+ "bytes of threadgroup memory, past the verified {} byte limit, and does "
							+ "not dispatch a single work group", this.path, shared.threadgroupBytes(),
							SharedMemory.THREADGROUP_BYTES);
					this.localSize = null;
					return;
				}
				if (!(backend instanceof StorageBufferBackend)) {
					Vitrail.logger().warn("compute {} is not dispatched on the active backend: its {} "
							+ "bytes of shared memory need the storage-buffer fallback, but that backend "
							+ "does not expose storage-buffer allocation", this.path,
							shared.threadgroupBytes());
					this.localSize = null;
					return;
				}

				this.sharedMemoryBytes = shared.bufferBytes();
				source = shared.moved();
				Vitrail.logger().info("compute {} asks Metal for {} bytes of threadgroup memory, past "
						+ "the {} it allows; its fixed single work group is served from a transient "
						+ "storage buffer of {} bytes", this.path, shared.threadgroupBytes(),
						SharedMemory.THREADGROUP_BYTES, this.sharedMemoryBytes);
			}
		}

		long began = System.nanoTime();
		IntermediaryShaderModule module = null;
		RawLocals.begin();
		try {
			String key = ModuleCache.keyOf(source, MODULE_CACHE_STAGE);
			module = ModuleCache.lookup(key, this.label);
			ByteBuffer spirv = null;
			if (module == null) {
				spirv = compileSpirv(source);
				if (spirv == null) {
					ModuleCache.building(this.label);
					return;
				}
				ModuleCache.building(this.label);
			}

			if (module == null) {
				module = IntermediaryShaderModule.createFromSpirv(this.label,
						RawLocals.patch(this.label, spirv));
				ModuleCache.store(key, module);
			}

			this.resources = ComputeResources.inspect(module.spirv());
			this.pipeline = backend.vitrail$compileCompute(this.label, module.spirv().duplicate());
			this.owner = backend;
			if (this.sharedMemoryBytes > 0L) {
				this.sharedMemory = ((StorageBufferBackend) backend)
						.vitrail$createStorageBuffer(this.sharedMemoryBytes);
			}
		} catch (GpuDeviceLossException e) {
			throw e;
		} catch (Exception e) {
			Vitrail.logger().warn("compute {} backend pipeline failed: {}", this.path, e.toString());
			closePipeline();
		} finally {
			RawLocals.end();
			if (module != null) {
				module.close();
			}
			LoadClock.module(System.nanoTime() - began);
		}

		if (this.pipeline != null) {
			Vitrail.logger().info("Compiled compute {} through the active backend", this.path);
		}
	}

	private GpuBufferSlice writeBlock(PackValues values) {
		int bytes = Math.max(16, this.uniforms.size());
		if (this.block == null) {
			this.block = new MappableRingBuffer(() -> "vitrail compute " + this.path,
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, bytes);
		}
		try (GpuBufferSlice.MappedView view = this.block.currentBuffer().map(false, true)) {
			ByteBuffer data = view.data();
			data.position(0);
			this.uniforms.write(Std140Builder.intoBuffer(data), values.world());
		}
		return this.block.currentBuffer().slice(0, bytes);
	}

	private static ByteBuffer compileSpirv(String source) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		long options = compileOptions();
		ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
		ByteBuffer filename = MemoryUtil.memUTF8("compute.csh");
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		long result = 0L;
		try {
			result = Shaderc.shaderc_compile_into_spv(compiler, sourceBuffer, SHADERC_COMPUTE,
					filename, entry, options);
			if (Shaderc.shaderc_result_get_compilation_status(result) != 0) {
				Vitrail.logger().warn("compute shaderc: {}",
						Shaderc.shaderc_result_get_error_message(result));
				return null;
			}
			ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
			ByteBuffer copy = MemoryUtil.memCalloc(spirv.remaining());
			MemoryUtil.memCopy(spirv, copy);
			return copy;
		} finally {
			if (result != 0L) {
				Shaderc.shaderc_result_release(result);
			}
			MemoryUtil.memFree(entry);
			MemoryUtil.memFree(filename);
			MemoryUtil.memFree(sourceBuffer);
			Shaderc.shaderc_compile_options_release(options);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}

	private static String preprocess(String source) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		long options = compileOptions();
		ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
		ByteBuffer filename = MemoryUtil.memUTF8("compute.csh");
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		long result = 0L;
		try {
			result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, sourceBuffer,
					SHADERC_COMPUTE, filename, entry, options);
			if (result == 0L) {
				return null;
			}
			ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
			if (Shaderc.shaderc_result_get_compilation_status(result) != 0 || bytes == null) {
				return null;
			}
			return StandardCharsets.UTF_8.decode(bytes).toString();
		} finally {
			if (result != 0L) {
				Shaderc.shaderc_result_release(result);
			}
			MemoryUtil.memFree(entry);
			MemoryUtil.memFree(filename);
			MemoryUtil.memFree(sourceBuffer);
			Shaderc.shaderc_compile_options_release(options);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}

	private static long compileOptions() {
		long options = Shaderc.shaderc_compile_options_initialize();
		Shaderc.shaderc_compile_options_set_target_env(options, 0, SHADERC_VULKAN_1_2);
		Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
		Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
		Shaderc.shaderc_compile_options_set_generate_debug_info(options);
		Shaderc.shaderc_compile_options_set_optimization_level(options, 2);
		return options;
	}

	private void closePipeline() {
		Object pipeline = this.pipeline;
		ComputeDeviceBackend backend = this.owner;
		this.pipeline = null;
		this.owner = null;
		this.resources = null;
		if (pipeline != null && backend != null) {
			backend.vitrail$closeCompute(pipeline);
		}
	}

	@Override
	public void close() {
		closePipeline();
		if (this.block != null) {
			this.block.close();
			this.block = null;
		}
		if (this.sharedMemory != null) {
			this.sharedMemory.close();
			this.sharedMemory = null;
		}
		this.sharedMemoryBytes = 0L;
	}

	private record LocalSize(int x, int y, int z) {
		private static LocalSize read(String source, String path) {
			int[] local = { -1, 1, 1 };
			boolean[] mentioned = { false, false, false };
			Matcher matcher = LOCAL_AXIS.matcher(source);
			while (matcher.find()) {
				int axis = switch (matcher.group(1)) {
					case "x" -> 0;
					case "y" -> 1;
					case "z" -> 2;
					default -> throw new IllegalStateException("Unexpected local-size axis");
				};
				mentioned[axis] = true;
				String argument = argumentAt(source, matcher.end());
				if (!LOCAL_LITERAL.matcher(argument).matches()) {
					throw new IllegalStateException("Compute " + path + " writes local_size_"
							+ matcher.group(1) + " as an unreadable expression after preprocessing: "
							+ argument);
				}
				int value;
				try {
					value = Integer.parseInt(argument);
				} catch (NumberFormatException e) {
					throw new IllegalStateException("Compute " + path + " local size is too wide", e);
				}
				if (value <= 0) {
					throw new IllegalStateException("Compute " + path + " local_size_"
							+ matcher.group(1) + " must be positive");
				}
				local[axis] = value;
			}
			if (!mentioned[0] || local[0] <= 0) {
				throw new IllegalStateException("Compute " + path
						+ " has no readable local_size_x after preprocessing");
			}
			return new LocalSize(local[0], local[1], local[2]);
		}

		private static String argumentAt(String source, int from) {
			int depth = 0;
			for (int at = from; at < source.length(); at++) {
				char letter = source.charAt(at);
				if (letter == '(') {
					depth++;
				} else if (letter == ')' && depth > 0) {
					depth--;
				} else if (letter == ')' || (letter == ',' && depth == 0)) {
					return source.substring(from, at).trim();
				}
			}
			return source.substring(from).trim();
		}
	}
}
