package dev.vitrail.render;

import dev.vitrail.Vitrail;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the shadow map has to be drawn again this frame, or whether the one on hand still says
 * the truth.
 * <p>
 * The second walk of the world is the most expensive thing this engine does. Measured on
 * 6 September 2026, at eye level in open terrain with Complementary Unbound at its factory profile,
 * the {@code shadow chunk} pass alone costs 4.12 ms of a 7.05 ms frame, and cutting the pack's
 * shadows entirely takes that frame to 1.96 ms. Nothing else in the frame is worth a third of it.
 * <p>
 * <strong>And most of that work is redrawn identically.</strong> The map holds the terrain lit from
 * the sun, and between two frames of a player standing still nothing in it moves: not the terrain,
 * not the sun by any amount a texel would notice. What changes is the camera, and the map is
 * anchored on the camera through the grid snap of {@link ViewMatrices}, so it stops being the right
 * map once the camera has walked far enough for its box to have moved.
 * <p>
 * <strong>What makes the reuse sound is that the pack is told which map it has.</strong> The engine
 * already publishes a pair of shadow matrices distinct from the fresh ones, because a map can be
 * sampled on a frame that did not draw it: {@code mapShadowModelView} is the matrix the map on hand
 * was drawn with, moved onto the current camera. Reusing a map for several frames is the same
 * mechanism with a longer arm, the anchor moving only on the frames the map is really drawn. A pass
 * sampling the map therefore transforms with the matrix that map was built with, whatever its age,
 * and the lookup lands where it did.
 *
 * <h2>What it does not cover, and what that looks like</h2>
 *
 * <strong>Only the OPAQUE world is kept.</strong> Everything that moves is drawn into the restored
 * map on every frame, so a mob, a boat, a falling block and the player's own shadow are exact
 * whatever the interval. What ages is the ground: a block placed or broken, or a section that
 * finishes loading, keeps casting the shadow it had until the map is drawn again.
 * <p>
 * <strong>And it is judged by walking, not by capturing.</strong> A lag counted in frames looks
 * like whatever the frame rate makes of it, and a pinned camera cannot see it at all: captures
 * taken on one put the difference below the noise two relaunches make on their own. Keeping the
 * whole finished map, which is what this did before the movers were drawn back into it, froze them
 * too, and three frames of that reads as a bug on the player's own shadow at two hundred a second.
 * <p>
 * <strong>A pack that voxelises into its shadow pass must not be amortised at all</strong>, its
 * shadow programs writing a volume the rest of the frame reads rather than only a depth. That
 * refusal is made by the caller, which is the one place that knows.
 *
 * <h2>How it is set</h2>
 *
 * A slider on the engine page, from nought to {@link #MAX_FRAMES}, stored in
 * {@code vitrail/amortise-shadow} beside the pack. Nought has to be exactly the engine as it was:
 * the anchor then moves every frame, which is what the published pair already did before any of
 * this existed.
 * <p>
 * A file of its own rather than a line in the game's options, like the module cache ceiling: it is
 * read at the head of a frame, written by the screen, and belongs to the install rather than to a
 * pack or a world. It also means a session can flip it without a keyboard, which is how both sides
 * of it were measured in one jar.
 */
public final class ShadowAmortisation {

	/**
	 * What an empty arming file asks for: the map kept for ONE frame after the one that drew it, so
	 * it is never more than two frames old.
	 * <p>
	 * One is where a walk left it, and that walk was made against the map kept WHOLE, where three
	 * frames lagged the player's own shadow plainly enough to read as a bug and one was not seen.
	 * The movers are drawn back in now, so what that walk judged is gone and nobody has yet walked
	 * what remains, which is the ground. The value stays where the harsher behaviour put it rather
	 * than being widened on the strength of a lag nobody has looked at.
	 * <p>
	 * The captures of that walk, taken on a pinned camera where nothing moves, put the difference
	 * BELOW the noise two relaunches make on their own and would have signed off on three. An eye
	 * finds this edge and an instrument does not.
	 */
	public static final int DEFAULT_FRAMES = 1;

	/** Nought is the engine as it was: the map drawn every frame, and every caster exact. */
	public static final int MIN_FRAMES = 0;

	/**
	 * The most frames a map may be kept for, whatever the file says. Three is where a walk of the
	 * map kept whole found the artefact, so the selector stops one short of it. Inherited rather
	 * than measured against the ground alone: see {@link #DEFAULT_FRAMES}.
	 */
	public static final int MAX_FRAMES = 2;

	/**
	 * How far the camera may walk from where the map was drawn before it is drawn again, in blocks.
	 * <p>
	 * The map covers a box around the camera, so walking moves ground into the box that was never
	 * drawn into it, and that ground comes out unshadowed. Four blocks is a quarter of a section and
	 * well inside the smallest shadow distance of the corpus; it is not derived from the box, and a
	 * pack asking for a very short shadow distance would want it smaller.
	 */
	private static final double MOVE_BLOCKS = 4.0;

	/**
	 * How far the sun may turn, in turns, before the map is drawn again. A tenth of a degree, which
	 * at the ordinary day length is about six ticks: the trigger that fires is the frame count, not
	 * this one, and this one is here for a world whose time is being set rather than run.
	 */
	private static final float ANGLE_TURNS = 0.0003F;

	/**
	 * Where the value lives, beside the pack rather than in the game's options: it is read at the
	 * head of a frame by the engine and written by the settings screen, and it belongs to the
	 * install rather than to any pack or world.
	 */
	private static final String SETTING_FILE = "amortise-shadow";

	/**
	 * The interval a launch asks for, which is the setting's own range plus an arm beyond it.
	 * <p>
	 * <strong>Two uses, and only the first is a legal setting.</strong> Values from nought to
	 * {@link #MAX_FRAMES} are exactly what the selector offers the player, and the property is then
	 * only a way to write one of them before a launch: the file below holds one value for a whole
	 * session, while the arms of an A/B are launches of the same session and have to differ. Above
	 * {@link #MAX_FRAMES} - up to {@link #PROBE_MAX_FRAMES} - the arm is a measurement and NOT
	 * SEMANTICALLY CORRECT, since the ground in the map is older than the engine's own selector
	 * allows; the picture on such an arm is as wrong as the age of that ground, which is the cost
	 * being measured.
	 * <p>
	 * What it exists for is the question the removal arms cannot answer. They take the terrain
	 * raster out of the frame, which prices it at the interval this engine ships - one, so the
	 * raster is drawn every OTHER frame on every pack of the corpus that does not voxelise. What
	 * the shipped reuse is worth against no reuse at all is therefore a difference between two
	 * arms and not a component of one, and it is the number that decides whether the interval or
	 * the reuse itself is worth more work.
	 * <p>
	 * A JVM property rather than a line in the file because it is read before the first frame of a
	 * launch and cannot be moved afterwards: {@link #setFrames} is the screen's road and stays the
	 * screen's, and nothing here is reachable from it.
	 */
	private static final int PROBE_FRAMES = Integer.getInteger("vitrail.probeShadowInterval", -1);

	/**
	 * The most frames an arm beyond the selector may ask for, so that a typo cannot leave every
	 * frame of a session with a map old enough to be a freeze rather than a measurement.
	 */
	private static final int PROBE_MAX_FRAMES = 6;

	/** Said once, at the first ask: a probe that announced itself per frame would be a cost itself. */
	private static boolean saidProbe;

	/** Read from the file the first time it is asked for, and authoritative from then on. */
	private static int frames = -1;

	private static final Vector3d anchorCamera = new Vector3d();

	private static float anchorAngle;

	/**
	 * What this frame would anchor on if it draws. Taken at the head of the frame rather than at the
	 * draw, because the map is drawn with the matrices built at the head of the frame: an anchor read
	 * where the draw happens would be the camera as it stood after the rest of the head of the frame
	 * had moved it, and the published pair would then be measured from a place the map was not drawn
	 * around.
	 */
	private static final Vector3d pendingCamera = new Vector3d();

	private static float pendingAngle;

	private static boolean seeded;

	private static int sinceDraw;

	private static boolean drawThisFrame = true;

	private static boolean drewLastFrame = true;

	private static boolean drewSinceBegin;

	private static boolean missedLastPlan;

	/** Whether a map drawn this frame is to be kept for a later frame, settled at the head of it. */
	private static boolean keepThisFrame;

	/** Whether the map last drawn was kept, which is the only map a later frame may put back. */
	private static boolean keptAtLastDraw;

	private static boolean saidRefused;

	private static int countedFrames;
	private static int countedDraws;

	private ShadowAmortisation() {
	}

	/**
	 * Decides, once, whether the map will be drawn at the end of this frame, and answers whether it
	 * was drawn at the end of the last one.
	 * <p>
	 * Called from the shadow half of the frame setup, before the fresh matrices are built, because
	 * the answer decides which pair the sampling passes of this frame are handed.
	 *
	 * @param camera      where the camera stands this frame, unshifted
	 * @param shadowAngle the casting body's angle in turns, as {@link ViewMatrices} takes it
	 * @param amortisable whether the pack allows it at all: false forces a draw every frame
	 * @return whether the map on hand was drawn at the end of the previous frame
	 */
	public static boolean beginFrame(Vector3dc camera, float shadowAngle, boolean amortisable) {
		// What the previous frame ACTUALLY did, not what it planned: the stage refuses to open on
		// its own account (no chain, no device, an OpenGL boot, a pack whose shadow programs were
		// turned down), and on those frames the map is older than the plan says. The anchor has to
		// follow the map and never the intention, or the pack would be handed matrices for a map
		// that was never drawn.
		// Read before the line below clears drewSinceBegin, which is the binding constraint, and
		// before the plan is overwritten at the foot of this method. Answered rather than reported
		// from here: this class is one of the few of render/ the off-game harness compiles, and the
		// census it would call drags the game in with it.
		//
		// Guarded on seeded, which is what tells a real miss from the two states where the plan
		// says DRAW forever and nothing is wrong: the first frame after a load, and a pack that
		// keeps the opaque world out of its map, whose stage never reaches drawn() at all
		// (ShadowTerrain.java:364).
		missedLastPlan = seeded && drawThisFrame && !drewSinceBegin;

		drewLastFrame = drewSinceBegin;
		drewSinceBegin = false;
		// Counted here rather than by the stage: what the interval counts is frames since the map
		// was last FILLED, and this is the one place that sees every frame whatever the stage did.
		if (!drewLastFrame) {
			sinceDraw++;
		}

		pendingCamera.set(camera);
		pendingAngle = shadowAngle;

		int asked = frames();
		// Said once per pack, because a setting that does nothing and says nothing is worse than a
		// setting that is not there: this pack refuses the reuse on its own account, and the player
		// moving the slider would otherwise watch the frame rate not move.
		if (asked > 0 && !amortisable && !saidRefused) {
			saidRefused = true;
			Vitrail.logger().info("This pack voxelises into its shadow pass, so the map is drawn "
					+ "every frame whatever the reuse setting says");
		}

		// A rate and not a one-shot line: what has to be proved is how OFTEN the world is walked for
		// the light, and a message saying it happened once says nothing about the frame after it.
		if (asked > 0 && ++countedFrames >= 600) {
			Vitrail.logger().info("Shadow map: the opaque world was drawn into it {} times in the "
					+ "last {} frames", countedDraws, countedFrames);
			countedFrames = 0;
			countedDraws = 0;
		}

		keepThisFrame = asked > 0 && amortisable;
		// A map that was not kept when it was drawn is not in the store, and the store may hold an
		// older one from before the reuse was switched off: the first frame after switching it on
		// draws and keeps, so a put back only ever follows a kept draw.
		drawThisFrame = !seeded || asked <= 0 || !amortisable || !keptAtLastDraw
				|| sinceDraw >= asked
				|| anchorCamera.distance(camera) >= MOVE_BLOCKS
				|| Math.abs(shadowAngle - anchorAngle) >= ANGLE_TURNS;

		return drewLastFrame;
	}

	/**
	 * Whether the frame before this one meant to draw the map and the stage did not, settled by the
	 * last {@link #beginFrame} and read once by the census.
	 * <p>
	 * <strong>Not the same thing as a frame that reused the map.</strong> A reuse is the player's
	 * own setting doing its work and the anchor follows it. This is the stage giving up on a frame
	 * the rest of the engine believed it had served, no chain, no device, an OpenGL boot: what the
	 * sampling passes are handed is then corrected for a shorter walk than the camera actually
	 * made.
	 * <p>
	 * <strong>And not a pack that declares no terrain caster either.</strong> That pack's stage
	 * never reaches {@link #drawn()}, so the plan says DRAW on every frame of it and nothing is
	 * wrong. {@link #seeded} separates the two: false until the map has really been filled once,
	 * and false again after every {@link #forget()}, which is a pack load AND every world or
	 * dimension change.
	 * <p>
	 * <strong>The price of that guard is what it cannot see.</strong> While nothing has been drawn
	 * yet, a real failure is not counted either, and a stage that never draws at all reads zero
	 * here for the whole session. That window closes at the first frame the stage really fills the
	 * map, so what survives it is the intermittent failure and not the permanent one, which is the
	 * one worth a number: a stage that never draws is loud elsewhere, a stage that skips one frame
	 * in a thousand is silent everywhere else.
	 */
	public static boolean missedLastPlan() {
		return missedLastPlan;
	}

	/**
	 * Whether the OPAQUE world is drawn into the map this frame, settled at the head of it.
	 * <p>
	 * Everything else in the stage runs whatever this answers: the walk, so Sodium keeps answering
	 * visibility from the light's tree, the casters that move, and the translucent world, which
	 * costs five per cent of what the opaque one does.
	 */
	public static boolean drawTerrainThisFrame() {
		return drawThisFrame;
	}

	/**
	 * Taken by the stage once the map has really been drawn. What it anchors on is what this frame
	 * was set up with, not what the world looks like now: see {@link #pendingCamera}.
	 */
	public static void drawn() {
		countedDraws++;
		anchorCamera.set(pendingCamera);
		anchorAngle = pendingAngle;
		sinceDraw = 0;
		seeded = true;
		drewSinceBegin = true;
		keptAtLastDraw = keepThisFrame;
	}

	/**
	 * Whether the map drawn this frame is to be kept, which the stage asks before it copies it: only
	 * where the reuse is asked for and the pack allows it. Everywhere else every frame draws its own
	 * map and a copy of it would never be read. Nought is then the engine as it was, copy included.
	 */
	public static boolean keepsDrawnMap() {
		return keepThisFrame;
	}

	/**
	 * Forgets the MAP, at a pack load and wherever else the chain is torn down: one pack's shadow
	 * map is not another's, and the next frame draws before anything samples it.
	 * <p>
	 * The interval is not forgotten with it. It is a setting of the install, not of the pack, and
	 * re-reading it here is what an earlier version did: {@code beginFrame} runs once per FRAME
	 * despite its name, so the value was read off the disk sixty times a second and the map was
	 * dropped just as often, which is the amortisation doing nothing while announcing itself.
	 */
	public static void forget() {
		seeded = false;
		sinceDraw = 0;
		drawThisFrame = true;
		drewLastFrame = true;
		drewSinceBegin = false;
		keptAtLastDraw = false;
		saidRefused = false;
	}

	/**
	 * How many frames a map may be kept for after the one that drew it. Nought is the engine as it
	 * was. Read from the file the first time, and from memory after that.
	 */
	public static int frames() {
		// Ahead of the setting and not beside it, read on every ask and answered from the property
		// rather than cached: the file the setting lives in is written by the screen, and a probe arm
		// that a later screen write could lift out of is not a controlled arm. Read once per frame,
		// which is what this method already costs.
		if (PROBE_FRAMES >= 0) {
			int asked = Math.min(PROBE_FRAMES, PROBE_MAX_FRAMES);
			if (!saidProbe) {
				saidProbe = true;
				// Said in the two words the two uses deserve: a value the selector already offers is
				// the setting written before the launch, and one past its cap is a measurement arm
				// whose picture is wrong by construction. A session reading the log has to be able to
				// tell which of the two it is looking at without knowing what was asked for.
				Vitrail.logger().info("Shadow map kept for {} frame(s) after the one that draws it "
								+ "(-Dvitrail.probeShadowInterval={}, this build's selector stopping at {}): {}",
						asked, PROBE_FRAMES, MAX_FRAMES,
						asked > MAX_FRAMES
								? "a measurement arm beyond the selector, so the ground in the map is "
										+ "older than any setting allows and the picture is wrong by construction"
								: "the setting's own value, written before the launch because the arms of one "
										+ "session have to differ");
			}

			return asked;
		}

		if (frames < 0) {
			frames = clamp(read());
			// Said once, and only when it is on: a shadow one frame late is the first thing to
			// suspect for a shadow artefact, and a session reading a log has no other way to know
			// the map it is looking at is not this frame's.
			if (frames > 0) {
				Vitrail.logger().info("Shadow map kept for {} frame(s) after the one that draws it, "
						+ "so the ground in it is that many frames old, everything that moves being "
						+ "drawn afresh", frames);
			}
		}

		return frames;
	}

	/**
	 * Takes the value the settings screen chose, writes it beside the pack and keeps the live
	 * answer. It applies at the next frame: no pack reload and no restart.
	 */
	public static void setFrames(int asked) {
		frames = clamp(asked);
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Files.writeString(file, frames + "\n");
		} catch (IOException | RuntimeException e) {
			// Kept live anyway: a value that cannot be stored is still the one the player asked for
			// this session, and a selector that springs back to its old place says nothing at all.
			Vitrail.logger().warn("Could not store the shadow amortisation, it holds for this "
					+ "session only", e);
		}
	}

	private static int read() {
		try {
			Path file = file();
			if (!Files.isRegularFile(file)) {
				return DEFAULT_FRAMES;
			}

			String asked = Files.readString(file).trim();
			// A file that is there and holds a typo reads as the default rather than as nought: it
			// was written on purpose, and answering it with the engine switched off is a gain that
			// disappears without a word.
			return asked.isEmpty() ? DEFAULT_FRAMES : Integer.parseInt(asked);
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT_FRAMES;
		}
	}

	private static Path file() {
		return Vitrail.platform().gameDirectory().resolve("vitrail").resolve(SETTING_FILE);
	}

	private static int clamp(int asked) {
		return Math.min(Math.max(asked, MIN_FRAMES), MAX_FRAMES);
	}
}
