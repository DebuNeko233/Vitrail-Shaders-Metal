package dev.vitrail;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;

/**
 * Iris in the same instance, and which of the two engines draws this session.
 * <p>
 * <strong>Iris picks its side once, before the game is built</strong>, off the
 * {@code preferredGraphicsBackend} line of {@code options.txt}: the game's native arm takes Iris's
 * hooks for that renderer alone, and anything else takes its OpenGL hooks
 * ({@code IrisMixinPlugin.java:54} and {@code 72-73}). What this engine does beside it follows that
 * read and not the device, which is why the answer is kept rather than asked of the device later.
 * <p>
 * <strong>The native arm is identified by elimination and never by name.</strong> This engine draws
 * on Metal alone, and the renderer Iris keeps those hooks for is one Vitrail no longer names anywhere
 * in its source - a contract test refuses the name outright. The question this class actually has is
 * the narrow one, whether the file asked for the arm Iris has a separate set of hooks for, and
 * Default and OpenGL are the two answers that are not it.
 * <p>
 * The line is taken from the options as the game has just loaded them, at the one read of this mod
 * that runs that early ({@code StartupGuard}), which is the same file Iris read. A later question
 * reads that answer.
 */
public final class IrisBeside {

	/**
	 * A class of Iris's own, looked for as a resource: the first question comes from inside the
	 * game's constructor, before this mod is constructed and before any loader platform exists to
	 * ask by mod id.
	 */
	private static final String MARKER = "net/irisshaders/iris/Iris.class";

	private static volatile Boolean installed;

	/**
	 * Whether the options the game loaded asked for the native arm Iris has hooks of its own for, or
	 * null until they have been seen.
	 */
	private static volatile Boolean askedNativeArm;

	private IrisBeside() {
	}

	/** Whether Iris is in this instance at all, whichever backend it took. */
	public static boolean installed() {
		Boolean known = installed;
		if (known == null) {
			known = IrisBeside.class.getClassLoader().getResource(MARKER) != null;
			installed = known;
		}

		return known;
	}

	/**
	 * Remembers what the options asked for as the game loaded them. Only the first call counts: what
	 * Iris read is the file as it stood at startup, whatever is written into the options afterwards.
	 *
	 * @param options the game's options, loaded and not yet touched by anything of this mod
	 */
	public static void loaded(Options options) {
		if (askedNativeArm == null) {
			askedNativeArm = asksNativeArm(options.preferredGraphicsBackend().get());
		}
	}

	/**
	 * Whether Iris draws this session: installed, and started on options that did not ask for its
	 * other set of hooks. Where the startup read was never seen, the game's options stand in for it,
	 * which is the same answer unless the player moved the setting during the session.
	 */
	public static boolean draws() {
		if (!installed()) {
			return false;
		}

		Boolean asked = askedNativeArm;
		if (asked == null) {
			Minecraft minecraft = Minecraft.getInstance();
			asked = minecraft != null
					&& asksNativeArm(minecraft.options.preferredGraphicsBackend().get());
		}

		return !asked;
	}

	/**
	 * Whether this preference is the arm Iris keeps hooks of its own for, which is every value that is
	 * neither Default nor OpenGL.
	 */
	private static boolean asksNativeArm(PreferredGraphicsApi preference) {
		return preference != PreferredGraphicsApi.DEFAULT
				&& preference != PreferredGraphicsApi.OPENGL;
	}
}
