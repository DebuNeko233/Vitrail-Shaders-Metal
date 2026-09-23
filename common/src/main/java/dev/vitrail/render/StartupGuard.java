package dev.vitrail.render;

import dev.vitrail.IrisBeside;
import dev.vitrail.Vitrail;

import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;

/**
 * Keeps the graphics preference across a startup that ended badly, instead of losing the Metal path
 * to a crash that had nothing to do with it.
 * <p>
 * <strong>What the game does, and why it costs a launch every time.</strong> {@code Minecraft} reads
 * {@code Options.startedCleanly} once at the head of its constructor, sets it false, and saves.
 * Anything that dies before startup finishes leaves it false, and the NEXT launch takes both of
 * these:
 * <pre>
 *   Detected unexpected shutdown during last game startup: resetting fullscreen mode
 *   Detected unexpected shutdown during last game startup: resetting preferred graphics API to Default
 * </pre>
 * and a third, forcing OpenGL, if the launch after that is dirty too. Measured here: a Distant
 * Horizons failure over a native library it could not extract, nothing to do with graphics, took the
 * backend down with it and cost a restart to put back.
 * <p>
 * <strong>Both messages hang off that one read</strong>, which is why answering it is the whole
 * intervention: the fullscreen mode is kept by the same stroke, and it was the second half of the
 * same complaint.
 * <p>
 * <strong>What the Metal path needs from this setting, and why the guard writes Default.</strong>
 * Metallum offers its Metal backend to the game only while the preference is Default and Metallum's
 * own {@code config/metallum.properties} says {@code metal}; a preference of OpenGL, or of the native
 * arm this engine no longer supports, takes the offer away. Default is therefore the value the Metal
 * path is selected through, and it is also the value the game's own first rescue writes - so the
 * guard is not fighting the game, it is holding that rescue's own answer and refusing the second one,
 * the launch that would force OpenGL and leave no Metal backend to try.
 * <p>
 * <strong>Writing Default cannot move Iris.</strong> Iris takes one set of hooks when the file asks
 * for that other native arm and its OpenGL hooks otherwise, so Default and OpenGL are the same answer
 * to it. An earlier shape of this put the other arm back instead, and that could flip Iris's hooks
 * after Iris had already read the file; nothing here does that, so no branch is needed for the case.
 * <p>
 * There is no choice left to make here and so no file to make it in. The engine draws on Metal alone,
 * Default is the only preference that offers it, and a session that cannot get a Metal device has
 * nothing this engine can fall back to by design. {@code vitrail/graphics-api.txt}, which held the
 * three ways between two backends and the game's own behaviour, is gone with the backends.
 */
public final class StartupGuard {

	private StartupGuard() {
	}

	/**
	 * Answers the game's question about the last startup, having first put the preference at the value
	 * the Metal path is selected through.
	 *
	 * @param options the game's options, already loaded, and the only thing that exists this early
	 * @param cleanly what the field really holds
	 * @return what the game should believe, which decides both resets it is about to consider
	 */
	public static boolean startedCleanly(Options options, boolean cleanly) {
		// Before anything below can move the preference: Iris read the same file before the game was
		// built, and which engine draws this session follows that value and nothing written after it.
		IrisBeside.loaded(options);
		if (cleanly) {
			return true;
		}

		// Set rather than merely kept: a launch that already walked the preference on to OpenGL would
		// otherwise stay there, which is the one value at which the Metal backend stops being offered
		// at all.
		if (options.preferredGraphicsBackend().get() != PreferredGraphicsApi.DEFAULT) {
			options.preferredGraphicsBackend().set(PreferredGraphicsApi.DEFAULT);
		}

		Vitrail.logger().warn("The last startup ended badly. The game was about to reset the "
				+ "graphics API and the fullscreen mode; both are kept, and the preference is left at "
				+ "Default, which is where Metallum offers the Metal backend this engine draws on. A "
				+ "crash during startup is almost never the backend's doing, and a launch forced on to "
				+ "OpenGL would leave this engine with no Metal device to draw through");

		// True, so neither reset runs. The game still wrote the flag false and saved it just before
		// this, so the marker itself goes on working for whatever else reads it.
		return true;
	}
}
