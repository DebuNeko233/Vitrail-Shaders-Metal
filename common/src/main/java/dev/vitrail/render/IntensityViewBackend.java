package dev.vitrail.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

/**
 * The one thing {@link GlyphIntensity} needs that Minecraft's public GPU facade cannot express: a
 * view of an existing texture whose first channel is read in all four.
 * <p>
 * <strong>Why the mapping belongs to the resource and not to the shader.</strong> The reference
 * implementation swizzles the sampled texture for as long as a grayscale program is bound, which is
 * texture state a graphics API of that shape lets a program change. On this platform the equivalent
 * is fixed when the texture resource is described, so a pack's program cannot reach it and the
 * engine has to hand it a resource that already reads the way the pack expects.
 * <p>
 * <strong>A null answer is the honest one and the caller survives it.</strong> A backend that cannot
 * produce such a view - because its resource description has no such field, or because the texture
 * is not the single-channel sheet this is for - returns null, and the caller keeps the game's own
 * view and says once that the pack will draw its text with the coverage in red. That is a worse
 * picture than a correct one and a far better one than a wrong resource handed over silently.
 * <p>
 * Nothing here names a native handle: the answer is a Minecraft {@link GpuTextureView} or nothing.
 */
public interface IntensityViewBackend {

	/**
	 * A view of that texture reading its first channel in all four, or null where this backend cannot
	 * make one.
	 *
	 * @param sheet the game's own view of the single-channel font sheet
	 * @return the view to hand to the pack's program, or null to keep the game's
	 */
	@Nullable GpuTextureView vitrail$createIntensityView(GpuTextureView sheet);
}
