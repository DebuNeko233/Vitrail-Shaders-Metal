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
		Map<String, GpuBufferSlice> buffers = new LinkedHashMap<>();
		for (String name : resources.uniformBuffers()) {
			buffers.put(name, uniformBlock);
		}
		for (String name : resources.storageBuffers()) {
			GpuBufferSlice slice = StorageBuffers.facadeSlice(name);
			if (slice == null) {
				throw new IllegalStateException("Missing backend storage buffer " + name);
			}
			buffers.put(name, slice);
		}

		Map<String, GpuTextureView> textures = new LinkedHashMap<>();
		Map<String, GpuSampler> samplerStates = new LinkedHashMap<>();
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
