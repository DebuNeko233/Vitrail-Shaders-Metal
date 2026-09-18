package dev.vitrail.mixin.sodium;

import dev.vitrail.sodium.ShadowTerrain;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the exact camera traversal inputs Sodium used this frame so the shadow stage can restore
 * the camera render-list contents after its temporary light walk.
 * <p>
 * Captured at RETURN rather than reconstructed from Minecraft matrices later: Sodium's viewport is
 * already the result of its cull-frustum setup, and using that same object is what makes the restore
 * a repeat of the camera traversal rather than an approximation of one.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class MixinSodiumWorldRendererSetup {

	@Inject(method = "setupTerrain", at = @At("RETURN"), require = 1)
	private void vitrail$captureCameraWalk(Camera camera, Viewport viewport,
			FogParameters fogParameters, boolean useOcclusionCulling,
			boolean updateChunksImmediately, Matrix4f cullMatrix, CallbackInfo ci) {
		ShadowTerrain.captureCameraWalk(camera, viewport, fogParameters);
	}
}
