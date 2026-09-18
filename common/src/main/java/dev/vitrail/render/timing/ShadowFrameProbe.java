package dev.vitrail.render.timing;

import dev.vitrail.Vitrail;
import dev.vitrail.render.ShadowAmortisation;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;

/**
 * One line a frame about the shadow walk and about the camera's own render lists, for as long as it
 * is armed. A developer probe, and the only one whose subject is a question rather than a clock.
 * <p>
 * It exists because three readings of this engine's shadow path have now been taken from the
 * source and each was refuted by the next reading. The lines this engine already prints about the
 * light's walk are per BLOCK TABLE, which is two to seven lines a session: no sample rate at which
 * a per-frame alternation can be seen at all. What a per-frame question needs is a per-frame
 * answer, and this is the cheapest one that settles the fork.
 * <p>
 * <strong>The fork it settles.</strong> A picture that flickers only while the camera moves is
 * either the render lists the frame draws from - the camera's visible set differing between
 * adjacent frames - or the content of the shadow map itself. The camera's section count is the
 * first and the amortisation's own decision is the second: a count that alternates frame to frame
 * is the list layer, a count that holds still while the picture still flickers is the map.
 * <p>
 * <strong>Off unless asked for.</strong> {@code -Dvitrail.probeShadowFrames=true} arms it, and a
 * {@code vitrail/probe-shadow-frames} file in the game directory says the same thing, for the
 * reason {@link RingTimings} gives: the launcher's arguments are a place a session cannot reach
 * while a file next to the pack is one it can. {@code -Dvitrail.shadowFrameBudget=N} bounds the
 * lines, six hundred by default, because an unbounded per-frame probe is a way to fill a disk
 * rather than a way to answer a question. The file is asked once and the answer kept: it arms a
 * launch, not a frame.
 */
public final class ShadowFrameProbe {

	/** Whether the flag asked for it. Read once, at class load, as every property in this package is. */
	private static final boolean FLAG = Boolean.getBoolean("vitrail.probeShadowFrames");

	/** How many frames to write before stopping, so an armed launch cannot fill a disk by accident. */
	private static final int BUDGET = Integer.getInteger("vitrail.shadowFrameBudget", 600);

	private static final String MARKER = "probe-shadow-frames";

	/** The marker file's answer, held after the first ask: a stat per frame is not a probe cost. */
	private static Boolean armedFromFile;

	private static int written;

	private static boolean announced;

	private ShadowFrameProbe() {
	}

	/**
	 * Whether this frame is to be written. One field read per frame and nothing else when it is
	 * false, which is the whole of what an unarmed probe may cost.
	 */
	public static boolean armed() {
		if (!FLAG && !marker()) {
			return false;
		}

		if (written >= BUDGET) {
			return false;
		}

		if (!announced) {
			announced = true;
			Vitrail.logger().info("Shadow frame probe armed: {} line(s), one a frame, arithmetic and "
					+ "counts only", BUDGET);
		}

		return true;
	}

	/**
	 * Writes one frame.
	 *
	 * @param lightSections  sections in the light's render lists, before the camera's were put back
	 * @param cameraSections sections in the camera's render lists, after they were put back
	 */
	public static void frame(int lightSections, int cameraSections) {
		written++;
		// Whether the opaque world is drawn into the map THIS frame, as settled at the head of it.
		// Read here rather than passed in so that the one field decides both what the caller counts
		// and what this line says.
		Vitrail.logger().info("shadow-frame {} mapPlanned={} camera={} light={}",
				written, ShadowAmortisation.drawTerrainThisFrame(), cameraSections, lightSections);

		if (written == BUDGET) {
			Vitrail.logger().info("Shadow frame probe wrote its {} lines and is off; the log holds "
					+ "the run it was armed for", BUDGET);
		}
	}

	private static boolean marker() {
		if (armedFromFile == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null || minecraft.gameDirectory == null) {
				return false;
			}

			armedFromFile = Files.isRegularFile(minecraft.gameDirectory.toPath()
					.resolve("vitrail").resolve(MARKER));
		}

		return armedFromFile;
	}
}
