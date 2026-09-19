package dev.vitrail.render;

import dev.vitrail.mixin.access.CommandEncoderAccessor;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Fills the mip chain of a colour target, or of the shadow map. Nothing of the pack takes part.
 * <p>
 * It exists because Minecraft 26.2's public encoder has no {@code generateMipmaps}. Iris pays one
 * {@code glGenerateMipmap} per chain; Vitrail asks the active command backend for the equivalent
 * operation through {@link MipmapCommands}. Vulkan fills the levels with explicit image blits and
 * barriers. Metallum currently delegates eligible colour textures to Metal's native blit mipmap
 * command; depth/stencil chains are deliberately refused there until a correct Metal path exists.
 * <p>
 * What the packs do with those levels is not decoration: BSL drives its automatic exposure from
 * {@code texture2DLod(colortex0, vec2(0.5), log2(viewHeight * R))}, which without a chain reads
 * level nought at the centre of the screen, so the whole image is exposed for one pixel and darkens
 * wholesale the moment a jump moves that pixel from the ground to the sky. The same pack reads lods
 * for its depth of field and for the tiles of its bloom.
 * <p>
 * Backend details stay below this class. The Vulkan implementation uses linear filtering for
 * filterable colour images and nearest filtering for depth/integer cases where Vulkan requires it;
 * the Metal implementation follows the native mipmap-generation rules of the texture format.
 * Failure remains explicit: callers keep sampling the base level when a backend cannot safely fill
 * a requested chain.
 */
final class MipmapReduction {

	private MipmapReduction() {
	}

	/**
	 * Fills every level past the base of one surface, reading the level above each time.
	 * <p>
	 * Must run outside any render pass. Silent and harmless on a surface with no chain, which is
	 * every target no program reads at a lod: the caller is not expected to sort them out first.
	 *
	 * @return false when the chain could not be filled, in which case the levels hold whatever they
	 *         held and a lod read falls back to what it read before there were chains
	 */
	static boolean generate(CommandEncoder encoder, TargetSurface surface) {
		if (surface == null || surface.levels() <= 1) {
			return false;
		}

		if (!generate(encoder, surface.texture())) {
			return false;
		}

		surface.chainWritten(true);

		return true;
	}

	/**
	 * The same over an image this engine holds directly, which is the shadow map: its depth pair is
	 * not a colour target of a pack and carries its level count from its own directive.
	 *
	 * @return false when the chain could not be filled, in which case the levels hold whatever they
	 *         held and the caller must keep its readers at the base
	 */
	static boolean generate(CommandEncoder encoder, GpuTexture texture) {
		if (texture == null || texture.getMipLevels() <= 1) {
			return false;
		}

		GeometryHold.flush(() -> "a mip chain being filled");
		return Backends.capabilities(encoder) instanceof MipmapCommands commands
				&& commands.vitrail$generateMipmaps(texture);
	}
}
