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
 * operation through {@link MipmapCommands}. Metallum currently delegates eligible colour textures
 * to Metal's native blit mipmap command; depth/stencil chains are deliberately refused there until
 * a correct Metal path exists.
 * <p>
 * What the packs do with those levels is not decoration: BSL drives its automatic exposure from
 * {@code texture2DLod(colortex0, vec2(0.5), log2(viewHeight * R))}, which without a chain reads
 * level nought at the centre of the screen, so the whole image is exposed for one pixel and darkens
 * wholesale the moment a jump moves that pixel from the ground to the sky. The same pack reads lods
 * for its depth of field and for the tiles of its bloom.
 * <p>
 * Backend details stay below this class. A backend fills a colour chain by linear filtering where
 * the format is filterable and by nearest filtering where it is not, which is what the native
 * mipmap-generation rules of the texture format allow. Failure remains explicit: callers keep
 * sampling the base level when a backend cannot safely fill a requested chain, and the refusal is
 * said once by {@link MipmapCensus#refused} rather than left to be noticed as a picture.
 * <p>
 * The one chain the corpus asks for that not every backend fills is the shadow map's depth pair,
 * which no native blit covers: it is a progressive reduction of its own, and the backend that
 * carries it is the one whose encoder implements the depth road. Where it does not, the pair keeps
 * the single level its readers are bounded to and the pack draws the shadow map without its coarse
 * levels, which is the fallback this class names rather than a silent one.
 */
final class MipmapReduction {

	/**
	 * Diagnostic arm for the chain reduction, and nothing else.
	 * <p>
	 * <strong>NOT SEMANTICALLY CORRECT and never to be productionised.</strong> It leaves every level
	 * past the base holding whatever it held, so a program that reads a lod gets the base level
	 * instead of an average: the wrong picture by construction, and that is the point. The GPU cost
	 * of the reduction is the difference between a frame with it and a frame without it, because the
	 * only GPU clock this backend reports is the driver's whole-frame figure - a per-pass one does
	 * not exist on the Metal path. It is not a candidate optimisation and no reading of it may be
	 * used as one: the chain is what the packs are written against, not an extra.
	 */
	private static final boolean PROBE_NO_MIP_CHAINS = Boolean.getBoolean("vitrail.probeNoMipChains");

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

		if (!generate(encoder, surface.texture(), surface.label())) {
			return false;
		}

		surface.chainWritten(true);

		return true;
	}

	/**
	 * The same over an image this engine holds directly, which is the shadow map: its depth pair is
	 * not a colour target of a pack and carries its level count from its own directive.
	 *
	 * @param label what the census calls this image, since nothing here has a target's name to hand it. The
	 *              count is taken here and nowhere else: both roads to a chain reach this method, so counting
	 *              in the surface overload as well would report every pack target's chain twice.
	 * @return false when the chain could not be filled, in which case the levels hold whatever they
	 *         held and the caller must keep its readers at the base
	 */
	static boolean generate(CommandEncoder encoder, GpuTexture texture, String label) {
		if (texture == null || texture.getMipLevels() <= 1 || PROBE_NO_MIP_CHAINS) {
			return false;
		}

		GeometryHold.flush(() -> "a mip chain being filled");
		if (!(Backends.capabilities(encoder) instanceof MipmapCommands commands)
				|| !commands.vitrail$generateMipmaps(texture)) {
			MipmapCensus.refused(label, texture.getMipLevels());

			return false;
		}

		MipmapCensus.generated(label, texture.getMipLevels(), texture.getWidth(0), texture.getHeight(0));

		return true;
	}
}
