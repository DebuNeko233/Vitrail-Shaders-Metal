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
import net.minecraft.client.renderer.MappableRingBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One shader-pack compute pass on Vitrail's backend compute seam, and the single implementation
 * the compute road uses.
 * <p>
 * Shader-pack scheduling, name resolution and dispatch size stay in Vitrail, while native
 * shader-language conversion, pipeline creation, argument binding, synchronization and destruction
 * stay behind {@link ComputeDeviceBackend} and {@link ComputeCommands}. The SPIR-V this class
 * produces from shaderc is what the seam takes, handed over as bytes; the game's own module type is
 * not reached for, since it exists to build a native shader module this road never binds.
 */
final class BackendComputePass implements AutoCloseable {

	/**
	 * The shaderc target for the SPIR-V this engine compiles, one number because the call takes two:
	 * environment zero, which shaderc names after the graphics API this engine no longer speaks, and
	 * a version word of {@code (1 << 22) | (2 << 12)} for SPIR-V 1.2. The number is shaderc's and is
	 * left alone; only the name says what it means here, which is the target both roads compile
	 * against.
	 */
	private static final int SHADERC_TARGET_SPIRV_1_2 = 4202496;
	private static final int SHADERC_COMPUTE = 2;
	private static final int SHADERC_OPTIMIZATION_NONE = 0;
	private static final int SHADERC_OPTIMIZATION_PERFORMANCE = 2;
	private static final String MODULE_CACHE_STAGE_OPTIMIZED = "COMPUTE/shaderc-opt2-spirv1.2";
	private static final String MODULE_CACHE_STAGE_UNOPTIMIZED = "COMPUTE/shaderc-opt0-spirv1.2";
	private static final Pattern LOCAL_AXIS = Pattern.compile("\\blocal_size_([xyz])\\s*=\\s*");
	private static final Pattern LOCAL_LITERAL = Pattern.compile("\\d+");

	/**
	 * The computes this run has compiled, under the module cache's own key, each with the resource
	 * names reflected out of its SPIR-V.
	 * <p>
	 * <strong>Why this lives here and not in the store next door.</strong> That store keeps what the
	 * game's compiler makes of a unit - the bytes, the uniform buffers and samplers its reflection
	 * found, the inputs and outputs it numbered, the storage resources this engine appends behind
	 * them - and it hands them back as one of the game's own module objects. A compute never enters
	 * that compiler: its SPIR-V comes from shaderc here, and the only thing the store could carry
	 * for it is the bytes. Keying on the same text and the same stage token keeps the two roads
	 * apart, which is what the token is for: a graphics COMPUTE through the game's compiler cannot
	 * serve this blob, and this road cannot serve one of its modules.
	 * <p>
	 * The reflection is kept beside the words for the reason the store keeps its own: it is the
	 * other half of the same load cost, and a hit that still walked SPIRV-Cross would have saved the
	 * cheaper half. What this costs against the store is the disk: a second pack load in the same
	 * run pays nothing, and the first load after a restart pays the compile again. {@code keyOf}
	 * answering null, which is the store having nowhere to write, means no reuse at all.
	 * <p>
	 * The words are held on the heap and a native buffer is cut for the backend per pass, because a
	 * cached native buffer would outlive every reason to free it. Entries are never dropped: the key
	 * carries the text and every switch that changes what the compile emits, so a session holds one
	 * entry per distinct compute it has translated, and that is what a pack load costs anyway.
	 */
	private static final Map<String, Compiled> COMPILED_SPIRV = new ConcurrentHashMap<>();

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

	/**
	 * The maps this program's dispatches fill, made once and reused.
	 * <p>
	 * Reapplied, and the reason the revert was made turned out not to be this: the picture symptom reported
	 * beside it was the pack's own boundary fog switched off, and the arm that ran with this change on read the
	 * reference scene intact (0 maps built a dispatch against 3, everything else equal). Kept here so phase 7's
	 * before/after is a measurement rather than a plan.
	 */
	private final PackComputeBindings.Scratch scratch = PackComputeBindings.Scratch.of();

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
				transientBuffers(),
				this.scratch);
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
				// one copy per work group. They are equivalent only for a fixed single group. The
				// condition is kept as this engine has always had it, because widening it would
				// silently change a multi-group reduction merely to fit Metal's memory limit.
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
		RawLocals.begin();
		ByteBuffer spirv = null;
		try {
			String optimizedKey = ModuleCache.keyOf(source, MODULE_CACHE_STAGE_OPTIMIZED);
			String unoptimizedKey = ModuleCache.keyOf(source, MODULE_CACHE_STAGE_UNOPTIMIZED);

			// The optimized compile is the one that is wanted, and the unoptimized key is asked only
			// because that is the blob a load which had to fall back holds.
			String key = optimizedKey;
			Compiled compiled = cached(optimizedKey);
			if (compiled == null) {
				compiled = cached(unoptimizedKey);
				if (compiled != null) {
					key = unoptimizedKey;
				}
			}

			if (compiled == null) {
				SpirvResult optimized = compileSpirv(source, SHADERC_OPTIMIZATION_PERFORMANCE);
				if (optimized.spirv() != null) {
					spirv = optimized.spirv();
				} else {
					SpirvResult unoptimized = compileSpirv(source, SHADERC_OPTIMIZATION_NONE);
					if (unoptimized.spirv() == null) {
						Vitrail.logger().warn("compute shaderc: optimized compile failed: {}; "
								+ "unoptimized compile failed: {}", optimized.error(), unoptimized.error());
						ModuleCache.building(this.label);
						return;
					}

					Vitrail.logger().warn("compute {} shaderc optimization failed; retrying without "
							+ "optimization: {}", this.path, optimized.error());
					spirv = unoptimized.spirv();
					key = unoptimizedKey;
				}

				ModuleCache.building(this.label);
				// The same zeroes the game's compiler road gets in GlslCompilerMixin: this road has
				// its own shaderc call, so it has to ask for them itself, and before the reflection
				// and the store, so a kept blob carries them too. Compiled at the performance level,
				// this module has mostly values where that road has variables, and its undefined
				// reads are what the pass turns into zeroes here. The patch takes over the buffer it
				// is handed, freeing it where it replaces it, and the buffer it hands on is filled
				// to its end: the position goes back to nought because the reflection and the
				// backend both read from the start.
				ByteBuffer patched = RawLocals.patch(this.label, spirv);
				spirv = patched.rewind();
				compiled = new Compiled(words(spirv), ComputeResources.inspect(spirv));
				if (key != null) {
					COMPILED_SPIRV.put(key, compiled);
				}
			}

			this.resources = compiled.resources();
			this.pipeline = compilePipeline(backend, compiled.words());
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
			if (spirv != null) {
				MemoryUtil.memFree(spirv);
			}
			LoadClock.module(System.nanoTime() - began);
		}

		if (this.pipeline != null) {
			Vitrail.logger().info("Compiled compute {} through the active backend", this.path);
		}
	}

	/** The compile this run already holds under that key, or null where the key is one of none. */
	private static Compiled cached(String key) {
		return key == null ? null : COMPILED_SPIRV.get(key);
	}

	/** The words on the heap, so the native buffer shaderc filled can be freed with the compile. */
	private static byte[] words(ByteBuffer spirv) {
		ByteBuffer view = spirv.duplicate();
		byte[] words = new byte[view.remaining()];
		view.get(words);
		return words;
	}

	/**
	 * One native buffer of these words for the backend, freed the moment it has read them, which is
	 * the ownership the module store's own caller had: the buffer is handed over for the length of
	 * the call and nothing may keep it.
	 */
	private Object compilePipeline(ComputeDeviceBackend backend, byte[] words) {
		ByteBuffer spirv = MemoryUtil.memAlloc(words.length);
		try {
			spirv.put(words);
			spirv.flip();
			return backend.vitrail$compileCompute(this.label, spirv.duplicate());
		} finally {
			MemoryUtil.memFree(spirv);
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

	/**
	 * One compute as it is kept between loads: the shaderc words, and the names SPIRV-Cross read out
	 * of them. Both halves of the load are here because either one alone would leave the other to be
	 * paid again by the load that hit.
	 */
	private record Compiled(byte[] words, ComputeResources resources) {
	}

	private record SpirvResult(ByteBuffer spirv, String error) {
	}

	private static SpirvResult compileSpirv(String source, int optimization) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		long options = compileOptions(optimization);
		ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, false);
		ByteBuffer filename = MemoryUtil.memUTF8("compute.csh");
		ByteBuffer entry = MemoryUtil.memUTF8("main");
		long result = 0L;
		try {
			result = Shaderc.shaderc_compile_into_spv(compiler, sourceBuffer, SHADERC_COMPUTE,
					filename, entry, options);
			if (result == 0L) {
				return new SpirvResult(null, "shaderc returned no result");
			}
			if (Shaderc.shaderc_result_get_compilation_status(result) != 0) {
				return new SpirvResult(null, Shaderc.shaderc_result_get_error_message(result));
			}
			ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
			if (spirv == null) {
				return new SpirvResult(null, "shaderc returned no SPIR-V bytes");
			}
			// Written and flipped rather than memCopy'd, because memCopy leaves the position at the
			// end: both the patch and the reflection read from nought, and a buffer whose shape
			// depends on how a helper left it is a buffer that changes shape when the helper moves.
			ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
			copy.put(spirv);
			copy.flip();
			return new SpirvResult(copy, null);
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
		long options = compileOptions(SHADERC_OPTIMIZATION_NONE);
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

	private static long compileOptions(int optimization) {
		long options = Shaderc.shaderc_compile_options_initialize();
		// Environment zero, which is the SPIR-V road, and then the version word beside it.
		Shaderc.shaderc_compile_options_set_target_env(options, 0, SHADERC_TARGET_SPIRV_1_2);
		Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
		Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
		Shaderc.shaderc_compile_options_set_generate_debug_info(options);
		Shaderc.shaderc_compile_options_set_optimization_level(options, optimization);
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
