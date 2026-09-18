package dev.vitrail.compat.metallum;

import dev.vitrail.Vitrail;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Late-bound calls into Metallum's optional attachment-contents surface.
 * <p>
 * The Metal backend is not on Vitrail's common compile classpath, so the two facts a pass may state
 * about its attachments cross as booleans here and become the backend's own value type on the other
 * side. Keeping that reflection in one optional adapter is what lets the public capability interface
 * stay expressed entirely in JDK types.
 * <p>
 * A backend without the surface, or one whose shape has moved, leaves every pass on its own default -
 * contents carried - which is the answer that changes nothing. So every failure here is soft and said
 * once: this is an optimisation, and a frame may not depend on it.
 */
public final class MetallumAttachmentBridge {

	private static final String CONTENTS_CLASS = "com.metallum.render.AttachmentContents";

	private static final String ENCODER_CLASS = "com.metallum.render.MetalCommandEncoder";

	private static Surface surface;

	private static boolean refused;

	private MetallumAttachmentBridge() {
	}

	/**
	 * Tells the encoder what the pass it is about to create needs of each of its colour attachments.
	 * <p>
	 * A no-op wherever the backend cannot be told: a pass that reaches here and finds nothing keeps
	 * the load and store actions it would have had anyway.
	 *
	 * @param readAfterwards one entry per colour attachment slot, in draw buffer order
	 * @param overwritten    one entry per slot, in the same order
	 */
	public static void setNextPassContents(
			Object encoder,
			boolean[] readAfterwards,
			boolean[] overwritten) {
		Surface found = surface();
		if (found == null) {
			return;
		}

		try {
			Object[] contents = (Object[]) Array.newInstance(found.contents(), readAfterwards.length);
			for (int index = 0; index < contents.length; index++) {
				contents[index] = found.answer().newInstance(readAfterwards[index], overwritten[index]);
			}
			found.setter().invoke(encoder, (Object) contents);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			giveUp(exception);
		}
	}

	private static synchronized Surface surface() {
		if (surface != null || refused) {
			return surface;
		}

		try {
			Class<?> contents = Class.forName(CONTENTS_CLASS);
			surface = new Surface(
					contents,
					contents.getConstructor(boolean.class, boolean.class),
					Class.forName(ENCODER_CLASS).getMethod(
							"setNextPassContents", Array.newInstance(contents, 0).getClass()));
		} catch (ReflectiveOperationException | RuntimeException exception) {
			// A backend that is not Metallum at all, or an older one. Not an error: the passes keep
			// the behaviour they had before this existed, and the reason is kept for the log.
			refused = true;
			Vitrail.logger().debug("Attachment contents cannot be stated to this backend, so every "
					+ "pass keeps its load and store actions: {}", exception.toString());
		}

		return surface;
	}

	private static synchronized void giveUp(Exception exception) {
		if (surface == null) {
			return;
		}

		surface = null;
		refused = true;
		Vitrail.logger().warn("A pass could not be told what it needs of its attachments, so every "
				+ "pass after it keeps its load and store actions: {}", exception.toString());
	}

	private record Surface(Class<?> contents, Constructor<?> answer, Method setter) {
	}
}
