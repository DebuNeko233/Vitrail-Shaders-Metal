package dev.vitrail.render;

import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Backend-native mipmap generation that Minecraft 26.2's public command encoder does not expose.
 * <p>
 * The operation belongs to the backend because the synchronisation and the filtering rules around
 * it are the backend's own: this interface asks for a chain to be filled and Metallum decides how,
 * whether that is Metal's native blit mipmap command for an eligible colour chain or a sequence of
 * its own, together with whatever ordering it owes between the levels. Shader-pack policy stays
 * outside this interface; callers only ask whether the active backend can perform the operation
 * safely.
 */
public interface MipmapCommands {

	/**
	 * Fills every level after level zero of {@code texture} using the active backend's native path.
	 *
	 * @return true when the chain was filled, false when this backend cannot, the texture is not
	 *         eligible, or the current encoder state cannot perform the operation
	 */
	boolean vitrail$generateMipmaps(GpuTexture texture);
}
