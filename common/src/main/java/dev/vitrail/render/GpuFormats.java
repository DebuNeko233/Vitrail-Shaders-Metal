package dev.vitrail.render;

import dev.vitrail.mixin.access.GpuDeviceAccessor;
import dev.vitrail.pack.model.TargetFormat;
import dev.vitrail.render.storage.ShaderWritableTextureBackend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFormatProperties;

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

	/** An integer format carries no filtering, and asking a sampler for it is invalid on Vulkan. */
	static FilterMode filterFor(TargetFormat format) {
		return format.integer() ? FilterMode.NEAREST : FilterMode.LINEAR;
	}

	/**
	 * Whether this device makes a storage image of that format, which is what a compute writing a
	 * colour target as {@code colorimgN} needs. A backend with Vitrail's shader-writable texture
	 * seam owns that contract directly; Vulkan instead answers from the physical-device format
	 * feature bits. False with neither capability available, because a compute must not be scheduled
	 * against an image the active backend cannot promise to write.
	 */
	static boolean storageCapable(GpuFormat format) {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device != null
				&& ((GpuDeviceAccessor) device).vitrail$backend() instanceof ShaderWritableTextureBackend) {
			return true;
		}
		return feature(format, VK10.VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT, false);
	}

	/**
	 * Whether this device blends between two texels of that format, which is what a pack asks for
	 * every time it leaves blur on.
	 * <p>
	 * Asked of the device because the specification only REQUIRES it of some formats. A sixteen bit
	 * float is one of them; a thirty two bit float is not, and neither is the sixteen bit
	 * normalised pair, which are two of the four an atlas is allocated as. The thirty two bit float
	 * is what iterationT's atmosphere table goes up in, and Metal does not filter that width at
	 * all, so under MoltenVK the answer here is no and the sampler falls back to nearest rather
	 * than asking for something the driver never promised. GL required it of every one of them,
	 * which is why Iris asks nothing.
	 * <p>
	 * Yes with no Vulkan device to ask, which is the opposite default to {@link #storageCapable}
	 * and for the opposite reason: a storage image the engine cannot prove is a compute it must not
	 * schedule, where a filter it cannot prove would take linear filtering away from every texture
	 * of every pack on a reading that proves nothing.
	 */
	static boolean filtersLinearly(GpuFormat format) {
		return feature(format, VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT, true);
	}

	/**
	 * Whether the Vulkan backend can blit this format both ways, or whether a non-Vulkan backend may
	 * attempt the backend-neutral mipmap capability and report success/failure when it records it.
	 * <p>
	 * Vulkan has to answer before allocation because the specification requires neither blit bit of
	 * a depth format. Other backends do not share Vulkan's format-bit contract: Vitrail allocates the
	 * requested levels and {@link MipmapReduction} keeps samplers at level zero unless the active
	 * {@link MipmapCommands} implementation actually fills the chain. That lets Metal use a depth
	 * render reduction without pretending it is a Vulkan blit.
	 */
	static boolean blitsBothWays(GpuFormat format) {
		return feature(format, VK10.VK_FORMAT_FEATURE_BLIT_SRC_BIT, true)
				&& feature(format, VK10.VK_FORMAT_FEATURE_BLIT_DST_BIT, true);
	}

	/** One bit of what this device does with that format, or {@code absent} with no device to ask. */
	private static boolean feature(GpuFormat format, int bit, boolean absent) {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null
				|| !(((GpuDeviceAccessor) device).vitrail$backend() instanceof VulkanDevice vulkan)) {
			return absent;
		}

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkFormatProperties properties = VkFormatProperties.calloc(stack);
			VK10.vkGetPhysicalDeviceFormatProperties(vulkan.vkDevice().getPhysicalDevice(),
					VulkanConst.toVk(format), properties);

			return (properties.optimalTilingFeatures() & bit) != 0;
		}
	}
}
