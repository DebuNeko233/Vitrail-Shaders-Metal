package dev.vitrail.render.storage;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Backend allocation capability for an ordinary Minecraft texture that also needs shader writes.
 * <p>
 * Minecraft 26.2 has no public storage-image usage bit. Vitrail therefore asks only for the
 * missing allocation fact while retaining the normal {@link GpuTexture} facade and all pack-facing
 * target policy. Backends must not infer shader-pack names or dispatch behavior here.
 */
public interface ShaderWritableTextureBackend {

	GpuTexture vitrail$createShaderWritableTexture(String label, int usage, GpuFormat format,
			int width, int height, int depthOrLayers, int mipLevels);
}
