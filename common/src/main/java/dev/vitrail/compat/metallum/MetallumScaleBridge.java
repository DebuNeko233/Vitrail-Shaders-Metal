package dev.vitrail.compat.metallum;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.vitrail.Vitrail;

import java.lang.reflect.Method;

/**
 * Late-bound calls into Metallum's optional MetalFX surface.
 * <p>
 * The Metal backend is not on Vitrail's common compile classpath, so the decision to bring a scaled
 * picture back with MetalFX crosses as two calls here and becomes the backend's own method on the other
 * side. Keeping that reflection in one optional adapter is what lets the public capability interface stay
 * expressed entirely in Minecraft's own types - two texture views and two integers - with no framework
 * type, no Metal handle and no pixel format in it.
 * <p>
 * A backend without the surface, or one whose shape has moved, answers "no MetalFX" to the first call and
 * leaves the caller on its bilinear blit. So every failure here is soft and said once: this is a way of
 * bringing a picture back, and a frame may not depend on which one it was.
 */
public final class MetallumScaleBridge {

	private static final String ENCODER_CLASS = "com.metallum.render.MetalScaleBridge";

	private static Surface surface;

	private static boolean refused;

	private MetallumScaleBridge() {
	}

	/**
	 * Whether the backend this encoder belongs to can scale with MetalFX.
	 *
	 * @param encoder the backend's command encoder, which is also the thing that knows its device
	 */
	public static boolean available(Object encoder) {
		Surface found = surface();
		if (found == null) {
			return false;
		}

		try {
			return (boolean) found.available().invoke(null, encoder);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			giveUp(exception);
			return false;
		}
	}

	/**
	 * Brings {@code from} back into {@code to} with MetalFX, if the backend can.
	 *
	 * @return whether the encode happened, which is false wherever the backend cannot be asked
	 */
	public static boolean scale(
			Object encoder,
			GpuTextureView from,
			GpuTextureView to,
			int contentWidth,
			int contentHeight) {
		Surface found = surface();
		if (found == null) {
			return false;
		}

		try {
			return (boolean) found.scale().invoke(null, encoder, from, to, contentWidth, contentHeight);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			giveUp(exception);
			return false;
		}
	}

	private static synchronized Surface surface() {
		if (surface != null || refused) {
			return surface;
		}

		try {
			Class<?> encoder = Class.forName(ENCODER_CLASS);
			surface = new Surface(
					encoder.getMethod("available", Object.class),
					encoder.getMethod("scale", Object.class,
							GpuTextureView.class, GpuTextureView.class, int.class, int.class));
		} catch (ReflectiveOperationException | RuntimeException exception) {
			// A backend that is not Metallum at all, or one from before this existed. Not an error: the
			// render scale keeps the bilinear blit it has always had, and the reason is kept for the log.
			refused = true;
			Vitrail.logger().debug("This backend cannot scale with MetalFX, so the render scale brings "
					+ "the picture back with a blit: {}", exception.toString());
		}

		return surface;
	}

	private static synchronized void giveUp(Exception exception) {
		if (surface == null) {
			return;
		}

		surface = null;
		refused = true;
		Vitrail.logger().warn("MetalFX scaling stopped working and the render scale brings the picture "
				+ "back with a blit from here: {}", exception.toString());
	}

	private record Surface(Method available, Method scale) {
	}
}
