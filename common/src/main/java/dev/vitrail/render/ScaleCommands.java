package dev.vitrail.render;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Backend command-encoder capability for bringing a smaller picture back to the size of the one it is
 * drawn into.
 * <p>
 * The render scale draws the world small and has to put the result back before anything of the interface
 * lands on it. What does that is the backend's business, and on this engine's production path it is
 * MetalFX - a framework, two objects and an encode, none of which belongs in a backend-neutral module.
 * What crosses is therefore the decision and the two images, and never a framework type, a Metal handle
 * or a pixel format: the backend owns both textures, so it reads their formats and their handles itself.
 * <p>
 * The conservative answer is false at every call, which is what a backend without this leaves the caller
 * on: a plain bilinear blit, which brings the picture back without MetalFX's quality and without MetalFX
 * either. So a frame is never left half scaled, and a device that cannot scale loses sharpness rather
 * than the picture.
 */
public interface ScaleCommands {

	/**
	 * Whether this device can bring a scaled picture back with MetalFX.
	 * <p>
	 * Asked per frame and answered from a question asked once per device, so a caller may ask every frame
	 * without paying for it. False covers every way there is: a system without the framework, a framework
	 * without the class, a device that does not support the scaler, and a backend that is not this one.
	 */
	boolean vitrail$metalFxAvailable();

	/**
	 * Encodes one MetalFX spatial upscale of {@code from} into {@code to}.
	 *
	 * @param from          the texture the world was drawn into, at the scaled size
	 * @param to            the texture the picture is brought back into, at the window's size
	 * @param contentWidth  how much of {@code from} really holds this frame
	 * @param contentHeight the same, in rows
	 * @return whether the encode happened; false leaves the caller on its own road for this frame
	 */
	boolean vitrail$metalFxScale(GpuTextureView from, GpuTextureView to, int contentWidth, int contentHeight);
}
