package dev.vitrail.mixin.metallum;

import dev.vitrail.render.MipmapCommands;

import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adapts Metallum's backend-native colour mipmap generation to Vitrail's command capability.
 * <p>
 * The target is package-private and optional, so it is named as a soft target under {@link Pseudo}
 * instead of becoming a compile-time dependency of the shader-pack engine. Metallum owns the Metal
 * encoder and synchronization rules; Vitrail only asks whether this command can be performed.
 * <p>
 * Metallum currently accepts colour textures only. Depth/stencil mip chains, including Vitrail's
 * shadow depth chain, therefore continue to return {@code false} and retain the existing safe
 * fallback to level zero.
 */
@Pseudo
@Mixin(targets = "com.metallum.render.MetalCommandEncoder", remap = false)
public abstract class MetalCommandEncoderMixin implements MipmapCommands {

	@Shadow(remap = false)
	public abstract boolean generateMipmaps(GpuTexture texture);

	@Override
	public boolean vitrail$generateMipmaps(GpuTexture texture) {
		return generateMipmaps(texture);
	}
}
