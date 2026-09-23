package dev.vitrail.render;

import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.pack.model.TargetFormat;
import dev.vitrail.render.storage.ShaderWritableTextureBackend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;

/**
 * The one place a pack's colour format becomes a device format.
 * <p>
 * Both enumerations spell their formats the same way on purpose, so there is nothing left to
 * decide here: what a declared name is worth, and what it has to be promoted to, was settled
 * when the pack side resolved it, and answering any of it a second time would give one question
 * two answers. The switch is written out rather than going through {@code valueOf} so that a
 * name that stops matching is a compilation error and not a lost frame.
 */
final class GpuFormats {

	static {
		// The two enumerations are compiled separately, so a format added on the pack side after
		// this class was built would otherwise surface as a failed draw rather than as a hole.
		for (TargetFormat format : TargetFormat.values()) {
			try {
				of(format);
			} catch (RuntimeException e) {
				throw new IllegalStateException("No device format for " + format, e);
			}
		}
	}

	private GpuFormats() {
	}

	static GpuFormat of(TargetFormat format) {
		return switch (format) {
			case R8_UNORM -> GpuFormat.R8_UNORM;
			case R8_SNORM -> GpuFormat.R8_SNORM;
			case RG8_UNORM -> GpuFormat.RG8_UNORM;
			case RG8_SNORM -> GpuFormat.RG8_SNORM;
			case RGBA8_UNORM -> GpuFormat.RGBA8_UNORM;
			case RGBA8_SNORM -> GpuFormat.RGBA8_SNORM;
			case R16_UNORM -> GpuFormat.R16_UNORM;
			case R16_SNORM -> GpuFormat.R16_SNORM;
			case RG16_UNORM -> GpuFormat.RG16_UNORM;
			case RG16_SNORM -> GpuFormat.RG16_SNORM;
			case RGBA16_UNORM -> GpuFormat.RGBA16_UNORM;
			case RGBA16_SNORM -> GpuFormat.RGBA16_SNORM;
			case R8_UINT -> GpuFormat.R8_UINT;
			case R8_SINT -> GpuFormat.R8_SINT;
			case RG8_UINT -> GpuFormat.RG8_UINT;
			case RG8_SINT -> GpuFormat.RG8_SINT;
			case RGBA8_UINT -> GpuFormat.RGBA8_UINT;
			case RGBA8_SINT -> GpuFormat.RGBA8_SINT;
			case R16_UINT -> GpuFormat.R16_UINT;
			case R16_SINT -> GpuFormat.R16_SINT;
			case RG16_UINT -> GpuFormat.RG16_UINT;
			case RG16_SINT -> GpuFormat.RG16_SINT;
			case RGBA16_UINT -> GpuFormat.RGBA16_UINT;
			case RGBA16_SINT -> GpuFormat.RGBA16_SINT;
			case R32_UINT -> GpuFormat.R32_UINT;
			case R32_SINT -> GpuFormat.R32_SINT;
			case RG32_UINT -> GpuFormat.RG32_UINT;
			case RG32_SINT -> GpuFormat.RG32_SINT;
			case RGBA32_UINT -> GpuFormat.RGBA32_UINT;
			case RGBA32_SINT -> GpuFormat.RGBA32_SINT;
			case R16_FLOAT -> GpuFormat.R16_FLOAT;
			case RG16_FLOAT -> GpuFormat.RG16_FLOAT;
			case RGBA16_FLOAT -> GpuFormat.RGBA16_FLOAT;
			case R32_FLOAT -> GpuFormat.R32_FLOAT;
			case RG32_FLOAT -> GpuFormat.RG32_FLOAT;
			case RGBA32_FLOAT -> GpuFormat.RGBA32_FLOAT;
			case RGB10A2_UNORM -> GpuFormat.RGB10A2_UNORM;
			case RGB10A2_UINT -> GpuFormat.RGB10A2_UINT;
			case RG11B10_FLOAT -> GpuFormat.RG11B10_FLOAT;
		};
	}

	/**
	 * An integer format carries no filtering: a sampler asked to blend between two of its texels has
	 * no defined answer, so the format decides the filter rather than the pack's own request.
	 */
	static FilterMode filterFor(TargetFormat format) {
		return format.integer() ? FilterMode.NEAREST : FilterMode.LINEAR;
	}

	/**
	 * Whether this device makes a storage image of that format, which is what a compute writing a
	 * colour target as {@code colorimgN} needs.
	 * <p>
	 * <strong>The capability owns the answer, and the format is not asked about.</strong> A backend
	 * that serves Vitrail's shader-writable texture seam has promised the whole of this question by
	 * implementing it - it allocates the texture and decides what its own format table makes of the
	 * request - so a second opinion here could only contradict the one that does the work. False with
	 * no such backend, because a compute must not be scheduled against an image nothing has promised
	 * to write.
	 * <p>
	 * An earlier shape of this fell back to a per-format device query when the seam was absent. That
	 * query belonged to the deleted backend, whose specification exposes format feature bits and whose
	 * successor on this platform exposes no per-format query at all, so the fallback could only ever
	 * have answered for a backend this engine does not draw on. What is left is the seam check, which
	 * is the whole of the question here.
	 */
	static boolean storageCapable(GpuFormat format) {
		GpuDevice device = RenderSystem.tryGetDevice();

		return device != null
				&& ((GpuDeviceAccessor) device).vitrail$backend() instanceof ShaderWritableTextureBackend;
	}

	/**
	 * Whether this device blends between two texels of that format, which is what a pack asks for
	 * every time it leaves blur on.
	 * <p>
	 * <strong>Yes, and it is the platform's answer rather than a reading that proves nothing.</strong>
	 * The question is asked of a device because a graphics API's specification only requires the
	 * feature of some formats - a sixteen bit float is one of them, a thirty two bit float is not -
	 * so an engine that assumes it of every format is asking for something never promised. On this
	 * platform the promise is not per-format and not in doubt: Metal declares the filter in the
	 * sampler state at the shader's binding, the formats this engine allocates targets in are all
	 * filterable ones, and the two exceptions are handled where they are known rather than here - an
	 * integer format takes {@link #filterFor}'s NEAREST, and a pack's own depth comparison takes the
	 * comparison sampler that {@link ComparisonSamplers} supplies.
	 * <p>
	 * The parameter is kept rather than dropped: the question is per-format in principle, the callers
	 * ask it per format, and a signature that stopped taking the format would have to be widened again
	 * the first time one needed a different answer.
	 */
	static boolean filtersLinearly(GpuFormat format) {
		return true;
	}

	/**
	 * Whether the active backend can blit this format both ways, which is what the backend-neutral
	 * mipmap capability is attempted against.
	 * <p>
	 * Yes, on the same terms as {@link #filtersLinearly} and for a sharper reason: an API has to
	 * answer this before allocation because its specification requires neither blit bit of a depth
	 * format, where the blit encoder on this platform takes a depth texture as source and as
	 * destination. Nothing here has to be answered before allocation either way:
	 * {@code MipmapReduction} keeps its samplers at level zero unless the active
	 * {@link MipmapCommands} implementation reports that it really filled the chain, so a backend that
	 * cannot blit a format costs a level and not a wrong picture.
	 */
	static boolean blitsBothWays(GpuFormat format) {
		return true;
	}
}
