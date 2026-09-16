package dev.vitrail.render;

import dev.vitrail.compat.metallum.MetallumSamplerBridge;
import dev.vitrail.mixin.access.GpuDeviceAccessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;

/**
 * Supplies the backend-native comparison state owed by a translated {@code sampler2DShadow}.
 * <p>
 * Vitrail owns the semantic decision: {@link ShadowCompare} records which sampler names in which
 * pipeline are comparison reads. The ordinary {@link GpuSampler} already carries the pack's
 * NEAREST/LINEAR, mip and addressing choices, so the optional backend is asked only to clone those
 * generic properties and add LEQUAL comparison. That is the same split Iris uses when selecting
 * one of its ordinary/hardware, nearest/linear and mipped/non-mipped shadow sampler combinations.
 * <p>
 * Vulkan keeps its established native descriptor substitution. Metallum receives the same
 * semantic answer through its optional late-bound sampler bridge, with no shadow name crossing the
 * backend boundary.
 */
public final class ComparisonSamplers {

	private ComparisonSamplers() {
	}

	/** Returns {@code ordinary} unchanged unless this exact pipeline/name needs native comparison. */
	public static GpuSampler forBinding(RenderPipeline pipeline, String name, GpuSampler ordinary) {
		if (ordinary == null || pipeline == null || !ShadowCompare.noted()
				|| !ShadowCompare.compared(pipeline).contains(name)) {
			return ordinary;
		}

		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return ordinary;
		}

		GpuDeviceBackend backend = ((GpuDeviceAccessor) device).vitrail$backend();
		if (!MetallumSamplerBridge.supports(backend)) {
			// Vulkan's descriptor walk supplies its native comparison sampler later.
			return ordinary;
		}

		return MetallumSamplerBridge.comparisonSampler(
				backend, ordinary, CompareOp.LESS_THAN_OR_EQUAL);
	}
}
