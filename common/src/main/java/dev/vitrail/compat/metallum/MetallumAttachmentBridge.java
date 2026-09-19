package dev.vitrail.compat.metallum;

import dev.vitrail.Vitrail;

import java.lang.reflect.Method;

/**
 * Late-bound calls into Metallum's optional attachment-contents surface.
 * <p>
 * The two facts a pass may state about its attachments cross as booleans here and become the backend's own
 * value type on the other side, where that type belongs. This adapter used to build that type itself, by
 * reflection, from a class name it carried: {@code com.metallum.render.AttachmentContents}, which resolves to
 * nothing - the type lives in that backend's {@code render.shared} package. The lookup failed, the failure was
 * caught, and every pass was left on its own default for as long as it existed, which is exactly the silent
 * shape this seam keeps producing. A class name is not an ABI; two arrays of booleans are.
 * <p>
 * A backend without the surface, or one whose shape has moved, leaves every pass on its own default - contents
 * carried - which is the answer that changes nothing. So every failure here is soft and said once: this is an
 * optimisation, and a frame may not depend on it.
 * <p>
 * What this reflects into is the backend's public bridge and not its encoder. The encoder is package-private,
 * and reflection refuses to enter it however public the method on it is: every call here threw, was caught, and
 * quietly left the pass on its default - so the store half of P1 and its storage boundary were measured as
 * worth nothing while never having been delivered at all.
 */
public final class MetallumAttachmentBridge {

	private static final String BRIDGE_CLASS = "com.metallum.render.MetalAttachmentBridge";

	private static Methods methods;

	private static boolean refused;

	private MetallumAttachmentBridge() {
	}

	/**
	 * Tells the encoder what the pass it is about to create needs of each of its colour attachments.
	 * <p>
	 * A no-op wherever the backend cannot be told: a pass that reaches here and finds nothing keeps the load
	 * and store actions it would have had anyway.
	 *
	 * @param readAfterwards one entry per colour attachment slot, in draw buffer order
	 * @param overwritten    one entry per slot, in the same order
	 */
	public static void setNextPassContents(Object encoder, boolean[] readAfterwards, boolean[] overwritten) {
		Methods found = methods();
		if (found == null) {
			return;
		}

		try {
			found.setter().invoke(null, encoder, readAfterwards, overwritten);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			giveUp(exception);
		}
	}

	/**
	 * Tells the encoder whether the pass it is about to create may read a storage image written since the live
	 * encoder opened.
	 * <p>
	 * A no-op wherever the backend cannot be told, which leaves it owing the boundary exactly as it did before
	 * this existed.
	 */
	public static void setNextPassReadsStorageImage(Object encoder, boolean reads) {
		Methods found = methods();
		if (found == null) {
			return;
		}

		try {
			found.reads().invoke(null, encoder, reads);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			giveUp(exception);
		}
	}

	private static synchronized Methods methods() {
		if (methods != null || refused) {
			return methods;
		}

		try {
			Class<?> bridge = Class.forName(BRIDGE_CLASS, false,
					MetallumAttachmentBridge.class.getClassLoader());
			methods = new Methods(
					bridge.getMethod("setNextPassContents", Object.class, boolean[].class, boolean[].class),
					bridge.getMethod("setNextPassReadsStorageImage", Object.class, boolean.class));
		} catch (ReflectiveOperationException | RuntimeException exception) {
			// A backend that is not Metallum at all, or an older one. Not an error: the passes keep the
			// behaviour they had before this existed, and the reason is kept for the log.
			refused = true;
			Vitrail.logger().debug("Attachment contents cannot be stated to this backend, so every "
					+ "pass keeps its load and store actions: {}", exception.toString());
		}

		return methods;
	}

	private static synchronized void giveUp(Exception exception) {
		if (methods == null) {
			return;
		}

		methods = null;
		refused = true;
		Vitrail.logger().warn("A pass could not be told what it needs of its attachments, so every "
				+ "pass after it keeps its load and store actions: {}", exception.toString());
	}

	private record Methods(Method setter, Method reads) {
	}
}
