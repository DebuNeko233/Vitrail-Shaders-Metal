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
 * NEAREST/LINEAR, mip and addressing choices, so the backend is asked only to clone those generic
 * properties and add LEQUAL comparison. That is the same split Iris uses when selecting one of its
 * ordinary/hardware, nearest/linear and mipped/non-mipped shadow sampler combinations.
 * <p>
 * There is one road and it goes through Metallum's late-bound sampler bridge, with no shadow name
 * crossing the backend boundary. An earlier shape of this returned the ordinary sampler and left a
 * descriptor walk to substitute a native one behind it; that walk is gone with the backend it
 * belonged to, and the branch that relied on it is gone with the walk.
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
			// Handing back the ordinary sampler here would be worse than a refusal: the shader is
			// already compiled to a depth-reference sample, and that read against a sampler with no
			// comparison state is undefined rather than merely degraded, which is the shape of
			// failure this engine refuses everywhere else. The comparison state is a capability of
			// the one backend this engine draws on, so a backend that does not answer it is a broken
			// session and says so.
			throw new IllegalStateException("The " + device.getDeviceInfo().backendName()
					+ " backend does not supply the comparison sampler that " + name + " is read "
					+ "through, which a sampler2DShadow declaration in this pack requires");
		}

		return MetallumSamplerBridge.comparisonSampler(
				backend, ordinary, CompareOp.LESS_THAN_OR_EQUAL);
	}
}
