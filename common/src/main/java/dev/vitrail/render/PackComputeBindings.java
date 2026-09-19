package dev.vitrail.render;

import dev.vitrail.pack.model.TargetName;
import dev.vitrail.pack.model.TextureStage;
import dev.vitrail.pack.target.SamplerPlan;
import dev.vitrail.pack.target.TargetSchedule;
import dev.vitrail.pack.texture.CustomImages;
import dev.vitrail.render.compute.ComputeResources;
import dev.vitrail.render.storage.StorageBuffers;
import dev.vitrail.render.storage.StorageImages;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves shader-pack compute resource names to Minecraft GPU-facade objects.
 * <p>
 * This class contains policy but no native binding code. The priority deliberately mirrors the
 * established Vulkan descriptor path: custom storage images and pack textures first, then the
 * selected ping-pong colour target, stage depth, engine textures, the stage default target, and
 * finally the same black fallback used by the adjacent graphics pass.
 */
final class PackComputeBindings {

	private static final Pattern COLOUR_IMAGE = Pattern.compile("\\bcolorimg(\\d+)\\b");

	private PackComputeBindings() {
	}

	/**
	 * The three maps a resolve fills, owned by the caller and reused.
	 * <p>
	 * Phase 7 of the optimisation plan: these were built from scratch per dispatch - three
	 * {@code LinkedHashMap}s plus the three {@code Map.copyOf} copies {@link Resolved} makes - and this is the
	 * half that can be reused without touching the ABI. {@link Resolved} still copies, so nothing downstream
	 * can ever hold one of these maps and see it change under it.
	 */
	record Scratch(
			Map<String, GpuBufferSlice> buffers,
			Map<String, GpuTextureView> textures,
			Map<String, GpuSampler> samplers) {

		static Scratch of() {
			ComputeDispatchCensus.mapsBuilt(3);
			return new Scratch(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
		}
	}

	record Resolved(
			Map<String, GpuBufferSlice> buffers,
			Map<String, GpuTextureView> textures,
			Map<String, GpuSampler> samplers) {
		Resolved {
			buffers = Map.copyOf(buffers);
			textures = Map.copyOf(textures);
			samplers = Map.copyOf(samplers);
		}
	}

	static Resolved resolve(
			ComputeResources resources,
			GpuBufferSlice uniformBlock,
			SamplerPlan samplers,
			TextureStage textureStage,
			String program,
			ColorTargets targets,
			TargetSchedule.Bound step,
			GpuTextureView depth,
			GpuTextureView distant) {
		return resolve(resources, uniformBlock, samplers, textureStage, program, targets, step,
				depth, distant, Map.of());
	}

	/**
	 * Resolves a compute's resources with transient backend buffers taking precedence over the
	 * pack-level storage-buffer registry.
	 * <p>
	 * Transient buffers are deliberately named by the reflected shader resource rather than by a
	 * native binding index. The shared-memory fallback uses this door for {@code OfSharedMemory};
	 * keeping the exception here means neither the scheduler nor the Metallum bridge learns that
	 * name, and ordinary pack-declared SSBOs keep following {@link StorageBuffers} unchanged.
	 */
	static Resolved resolve(
			ComputeResources resources,
			GpuBufferSlice uniformBlock,
			SamplerPlan samplers,
			TextureStage textureStage,
			String program,
			ColorTargets targets,
			TargetSchedule.Bound step,
			GpuTextureView depth,
			GpuTextureView distant,
			Map<String, GpuBufferSlice> transientBuffers) {
		return resolve(resources, uniformBlock, samplers, textureStage, program, targets, step, depth, distant,
				transientBuffers, Scratch.of());
	}

	/** The same resolve, filling caller-owned maps so a steady frame allocates none of them. */
	static Resolved resolve(
			ComputeResources resources,
			GpuBufferSlice uniformBlock,
			SamplerPlan samplers,
			TextureStage textureStage,
			String program,
			ColorTargets targets,
			TargetSchedule.Bound step,
			GpuTextureView depth,
			GpuTextureView distant,
			Map<String, GpuBufferSlice> transientBuffers,
			Scratch scratch) {
		Map<String, GpuBufferSlice> buffers = scratch.buffers();
		buffers.clear();
		for (String name : resources.uniformBuffers()) {
			buffers.put(name, uniformBlock);
		}
		for (String name : resources.storageBuffers()) {
			GpuBufferSlice slice = transientBuffers.get(name);
			if (slice == null) {
				slice = StorageBuffers.facadeSlice(name);
			}
			if (slice == null) {
				throw new IllegalStateException("Missing backend storage buffer " + name);
			}
			buffers.put(name, slice);
		}

		Map<String, GpuTextureView> textures = scratch.textures();
		textures.clear();
		Map<String, GpuSampler> samplerStates = scratch.samplers();
		samplerStates.clear();
		for (String name : resources.storageImages()) {
			GpuTextureView view = storageImage(name, targets, step);
			if (view == null) {
				throw new IllegalStateException("Missing backend storage image " + name);
			}
			textures.put(name, view);
		}
		for (String name : resources.sampledImages()) {
			Sampled sampled = sampledImage(name, samplers, textureStage, program, targets, step,
					depth, distant);
			if (sampled == null) {
				throw new IllegalStateException("Missing sampled image " + name);
			}
			textures.put(name, sampled.view());
			samplerStates.put(name, sampled.sampler());
		}
		ComputeDispatchCensus.resolved(textures.size() + samplerStates.size());
		return new Resolved(buffers, textures, samplerStates);
	}

	private static GpuTextureView storageImage(String name, ColorTargets targets,
			TargetSchedule.Bound step) {
		GpuTextureView custom = StorageImages.facadeView(name);
		if (custom != null && CustomImages.storage(name)) {
			return custom;
		}

		Matcher image = COLOUR_IMAGE.matcher(name);
		if (!image.matches() || step == null) {
			return null;
		}
		int index = Integer.parseInt(image.group(1));
		TargetSurface surface = targets.surface(index, step.read(index));
		return surface != null && surface.storage() ? surface.storageView() : null;
	}

	private static Sampled sampledImage(
			String name,
			SamplerPlan samplers,
			TextureStage textureStage,
			String program,
			ColorTargets targets,
			TargetSchedule.Bound step,
			GpuTextureView depth,
			GpuTextureView distant) {
		GpuTextureView customStorage = StorageImages.facadeView(name);
		if (customStorage != null && !CustomImages.storage(name)) {
			return new Sampled(customStorage, samplerFor(targets, samplers, name));
		}

		ColorTargets.PackBinding supplied = targets.packTexture(textureStage, name);
		if (supplied != null) {
			return new Sampled(supplied.view(),
					PackPass.sampler(supplied.repeat(), supplied.filter(), false));
		}

		Sampled target = colourTarget(name, targets, samplers, step, program);
		if (target != null) {
			return target;
		}

		GpuTextureView read = switch (SamplerPlan.classify(name)) {
			case DEPTH -> PackPass.depth(name, targets, depth);
			case DISTANT_DEPTH -> PackPass.distant(name, targets, distant);
			case CENTER_DEPTH -> PackPass.centerDepth(targets);
			default -> null;
		};
		if (read != null) {
			return new Sampled(read, samplerFor(targets, samplers, name));
		}

		GpuTextureView engine = engineView(targets, name);
		if (engine != null) {
			return new Sampled(engine, samplerFor(targets, samplers, name));
		}

		SamplerPlan.Binding byDefault = samplers.binding(name);
		if (!byDefault.defaulted()) {
			return null;
		}

		String screen = TargetName.canonical(SamplerPlan.DEFAULT_TARGET);
		if (byDefault.kind() == SamplerPlan.Kind.COLORTEX && step != null) {
			Sampled defaultTarget = colourTarget(screen, targets, samplers, step, program);
			if (defaultTarget != null) {
				return defaultTarget;
			}
		}

		ColorTargets.PackBinding laid = targets.packTexture(textureStage, screen);
		if (laid != null) {
			return new Sampled(laid.view(), PackPass.sampler(laid.repeat(), laid.filter(), false));
		}
		return new Sampled(targets.black(), samplerFor(targets, samplers, name));
	}

	private static Sampled colourTarget(String name, ColorTargets targets, SamplerPlan samplers,
			TargetSchedule.Bound step, String program) {
		OptionalInt index = TargetName.index(name);
		if (index.isEmpty() || step == null) {
			return null;
		}

		int target = index.getAsInt();
		TargetSurface surface = targets.surface(target, step.read(target));
		if (surface == null || surface.view() == null) {
			return new Sampled(targets.black(), samplerFor(targets, samplers, name));
		}

		boolean mipmaps = surface.chainWritten() && targets.lodReads(program).contains(target);
		return new Sampled(surface.view(), PackPass.sampler(false, targets.filter(target), mipmaps));
	}

	private static GpuTextureView engineView(ColorTargets targets, String name) {
		ShadowTargets shadow = targets.shadow();
		return switch (name) {
			case "noisetex" -> targets.noise();
			case "shadowtex0", "shadowtex0HW" ->
					orWhite(targets, shadow == null ? null : shadow.depth());
			case "shadowtex1", "shadowtex1HW" ->
					orWhite(targets, shadow == null ? null : shadow.depthWithoutTranslucents());
			default -> SamplerPlan.isShadowColour(name)
					? orWhite(targets, shadow == null ? null : shadow.colour(SamplerPlan.shadowColour(name)))
					: null;
		};
	}

	private static GpuTextureView orWhite(ColorTargets targets, GpuTextureView view) {
		return view == null ? targets.white() : view;
	}

	private static GpuSampler samplerFor(ColorTargets targets, SamplerPlan samplers, String name) {
		boolean noise = "noisetex".equals(name);
		FilterMode filter = noise ? FilterMode.LINEAR : switch (samplers.binding(name).kind()) {
			case SHADOW_COLOUR -> FilterMode.LINEAR;
			case SHADOW_DEPTH -> targets.shadow().depthFilter(samplers.withoutTranslucents(name));
			default -> PackPass.customImageFilter(name);
		};
		boolean mipmaps = !noise && samplers.binding(name).kind() == SamplerPlan.Kind.SHADOW_DEPTH
				&& targets.shadow().depthMipmapped(samplers.withoutTranslucents(name));
		return PackPass.sampler(noise, filter, mipmaps);
	}

	private record Sampled(GpuTextureView view, GpuSampler sampler) {
	}
}
