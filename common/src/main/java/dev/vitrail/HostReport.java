package dev.vitrail;

import dev.vitrail.render.BufferBlending;
import dev.vitrail.render.MetallumStatus;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Why this install can or cannot draw a shader pack, said once at startup, plus the one part of that
 * nothing here can set and that changes what is drawn: which of Chloride's own settings are on.
 * <p>
 * Metal is the only backend this engine draws on, and reaching it takes a short list of facts that
 * are all decided before this mod runs: the platform has to be macOS on Apple Silicon, Metallum has
 * to be installed, its public API has to answer the version this build understands, its owner has to
 * have selected Prefer Metal, and the game has to have brought a Metal device up through it. When one
 * of them is missing the session does not fall back to another backend - there is none to fall back
 * to - so the useful thing to say is which one is missing, and {@link #diagnosis()} answers that in a
 * clause that goes into the log and, once a world is on the screen, into chat.
 * <p>
 * The chat line exists because the log is read by whoever goes looking, and a player whose pack does
 * nothing at all is not yet looking. Once a session and not once a world, since the answer cannot
 * change between them.
 * <p>
 * Chloride is the second thing said, and it is a different kind of statement: a mod that culls what
 * this engine would have drawn, where a family missing from the picture is worth looking for there
 * before it is looked for in the pack. Nothing here is fixed on the player's behalf. What this can do
 * is name the setting, spell it the way the file spells it, and say what it costs.
 * <p>
 * <strong>It is a reading of one moment and does not follow any of it afterwards.</strong> Chloride
 * offers its settings in the Sodium screen and can rebuild the device without a restart, so a session
 * that changes one of them mid-way has a report that no longer describes it. What that costs is a
 * line that is missing rather than a line that is wrong, which is the direction to be wrong in.
 */
public final class HostReport {

	/** The backend name reported by Metallum's Metal device, and the only one this engine draws on. */
	private static final String METAL = "Metal";

	/**
	 * Answered where the device is not up yet, which at client setup cannot happen: the game builds
	 * it in the {@code Minecraft} constructor and dispatches mod events long afterwards. It is here
	 * so that a caller earlier than that gets a word rather than a crash, and it is a word the
	 * diagnosis below stays silent on: a backend nobody can name is not one to send somebody into
	 * the video settings over.
	 */
	private static final String UNKNOWN = "unknown";

	/**
	 * Chloride's own file, under the game's config folder rather than under this mod's: it belongs to
	 * Chloride, and the path is spelled here the way {@code INSTALL.md} spells it to whoever has to
	 * go and edit it.
	 */
	private static final String CHLORIDE_CONFIG = "config/chloride-client.toml";

	/** Chloride, asked for by id because it is optional and most installs will not have it. */
	private static final String CHLORIDE_ID = "chloride";

	/**
	 * Whether the chat line has been said, or found to have nothing to say, which closes it for the
	 * session either way.
	 */
	private static volatile boolean saidInWorld;

	/**
	 * The entries of Chloride's file this engine has something to say about. Three of them take
	 * geometry away before this engine sees it at all and two hand it to a path this engine's own
	 * final pass then covers, so a family missing from the picture is worth looking for here before
	 * it is looked for in the pack.
	 * <p>
	 * Each is written as its table and its key, which is how the messages spell them and how the
	 * file does: {@code entities} alone would name a key another of Chloride's tables declares as
	 * well, and {@code tileEntities} alone sits among neighbours that begin with the same word.
	 * <p>
	 * The three culling entries take by DISTANCE and by nothing else, which is why none of the
	 * messages names a count. Chloride has one, {@code entityLimit}, but it is a key of its own read
	 * by a different mixin, so a message about these three that named it would send whoever reads it
	 * to a line none of them is behind.
	 */
	private static final List<Entry> CHLORIDE = List.of(
			new Entry("fastBlocks", "chests",
					"Chloride draws chests as static block models. That path is covered over by this "
							+ "engine's final pass, so chests go invisible with no other symptom. Set "
							+ "it to false to see them"),
			new Entry("fastBlocks", "beds",
					"Chloride draws beds as static block models, and they go the way chests do: "
							+ "covered over by the final pass, invisible with no other symptom. Set "
							+ "it to false to see them"),
			new Entry("culling", "tileEntities",
					"Chloride decides on its own which block entities are drawn, by distance. What it "
							+ "takes out is never handed to the pack, so a chest or a sign that is "
							+ "not there is this rather than anything the pack does. Set it to false "
							+ "to have them all"),
			new Entry("culling", "entities",
					"Chloride decides on its own which entities are drawn, by distance, and this one "
							+ "governs every kind but the monsters. What it takes out is never handed "
							+ "to the pack either, so a boat, an item frame or a villager that is "
							+ "missing, or that appears as you walk towards it, is this. Set it to "
							+ "false to have them all"),
			new Entry("culling", "monsters",
					"Chloride decides on its own which monsters are drawn, by a distance of their "
							+ "own. A zombie or a creeper takes this line rather than the one above, "
							+ "so it is the one to set to false when what is missing is a monster"));

	private HostReport() {
	}

	/**
	 * The backend the game really came up on, which is not the same question as the one
	 * {@code options.txt} answers: asked for one, the game tries it and falls back to another in the
	 * same run when it cannot be brought up, leaving the file saying one thing and the session
	 * running on the other. Only the device knows.
	 *
	 * @return the backend name, or {@link #UNKNOWN} before the device exists
	 */
	public static String backend() {
		GpuDevice device = RenderSystem.tryGetDevice();
		return device == null ? UNKNOWN : device.getDeviceInfo().backendName();
	}

	/**
	 * The backend name Metallum's Metal device reports, which is the only one this engine draws on.
	 * <p>
	 * Exposed because more than one place has to ask the same question of a backend that is not up
	 * yet: the allocation helper asks it of a device's own report, and NeoForge's early-window guard
	 * asks it of the {@code GpuBackend} the game is about to build a window for, before there is any
	 * device to ask. One spelling, so the two cannot come to disagree about what Metal is called.
	 */
	public static String metalBackendName() {
		return METAL;
	}

	/**
	 * Whether this session is on Metal with everything the path needs: the backend is actually Metal,
	 * Metallum's versioned API matches and reports Prefer Metal, and Vitrail's Metal capability
	 * provider has already published after device creation. True here means Vitrail draws.
	 */
	public static boolean metalCandidate() {
		return METAL.equals(backend())
				&& MetallumStatus.compatibleAndPreferred()
				&& BufferBlending.served();
	}

	/**
	 * Whether the device is up and is not one this engine will draw a pack on.
	 * <p>
	 * Metal is the only backend this engine draws on, so there is exactly one way to be a candidate
	 * and every other named backend is refused. Unknown is not refused, because the device may simply
	 * not exist yet and a question asked before then must not read as a verdict.
	 * <p>
	 * The refusal is a fact about a session that is already running rather than a switch: nothing here
	 * offers another backend, because Vitrail has none. What a refused session gets is the clause in
	 * {@link #diagnosis()} naming the one fact that is missing, which is the whole of what can be done
	 * about it at this point.
	 */
	public static boolean otherBackend() {
		String backend = backend();
		if (UNKNOWN.equals(backend)) {
			return false;
		}

		return !METAL.equals(backend) || !MetallumStatus.renderingEnabled();
	}

	/**
	 * The one fact standing between this session and a drawn pack, as a clause that reads after a
	 * colon, or empty where nothing is: the platform, then Metallum, then its version, then its
	 * preference, then the device.
	 * <p>
	 * Asked in that order deliberately, because each answer only means anything given the one before
	 * it: a preference cannot be blamed before the mod that holds it is known to be installed, and a
	 * device cannot be blamed before the owner has been shown to want one. Read as a diagnosis, the
	 * first clause that is not empty is the thing to fix.
	 */
	public static String diagnosis() {
		if (!onAppleSilicon()) {
			return "this engine draws through Metal, which is macOS on Apple Silicon, and this session "
					+ "is " + System.getProperty("os.name", "an unknown system") + " on "
					+ System.getProperty("os.arch", "an unknown architecture");
		}

		MetallumStatus.Status status = MetallumStatus.status();
		if (!status.present()) {
			return "Metallum is not installed; it is the backend this engine draws through, and it is "
					+ "required rather than optional";
		}

		if (!status.compatible()) {
			return "Metallum answers API v" + status.apiVersion() + " and this build of "
					+ Vitrail.MOD_NAME + " understands v" + MetallumStatus.SUPPORTED_API_VERSION
					+ ", so the two cannot talk to each other";
		}

		if (!status.metalPreferred()) {
			return "Metallum is installed but its own graphics API preference is not Prefer Metal, so "
					+ "it never offers a Metal device to the game";
		}

		if (!BufferBlending.served()) {
			return "Metallum reports Prefer Metal and no Metal device came up through it, so there is "
					+ "nothing for the engine to draw with";
		}

		return "";
	}

	/** Whether this is the one platform the Metal path exists on. */
	private static boolean onAppleSilicon() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

		return os.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"));
	}

	/**
	 * Says in chat, once a session, that nothing of the pack is drawn and that the log names why.
	 * Asked every tick and answering nothing until a world is on the screen with no screen over it:
	 * said from the login packet, the line would land behind the loading terrain screen and be fading
	 * by the time the world appears.
	 * <p>
	 * Added to the chat as a message of the client's own rather than sent to the player the way the
	 * reload key's line is: {@code LocalPlayer.sendSystemMessage} hands it to the chat listener as a
	 * server message, and a chat set to hidden drops those, {@code ChatListener.handleSystemMessage}
	 * checking {@code canReceiveSystemMessages} first. The client's own source is the one
	 * {@code ChatAbilities.selectVisibleMessages} always lets through, and a line about the whole
	 * picture being missing is not one to leave to a chat setting.
	 * <p>
	 * The line names no setting to change, and that is the difference from the shape this had when
	 * there were two backends: there is no other backend to send a player to, so what the line can do
	 * is say that the picture is missing and that the reason is in the log, which carries the exact
	 * clause.
	 */
	public static void sayInWorld() {
		if (saidInWorld) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}

		saidInWorld = true;
		if (!otherBackend()) {
			return;
		}

		// Iris drawing this session means a player who picked it, and a red line saying the picture
		// is missing would be false for them. Asked of what Iris chose at startup rather than of Iris
		// being there.
		if (IrisBeside.draws()) {
			return;
		}

		minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable(
				ScreenText.OTHER_BACKEND, backend()).withStyle(ChatFormatting.RED));
	}

	/**
	 * Says what an install decides for this mod, once, and only where it decides something. A session
	 * that reached the Metal path and on which Chloride has nothing on costs no line at all: the log
	 * is read by whoever is chasing something else, and a line saying that all is well is one more to
	 * step over.
	 */
	public static void say(Path gameDirectory) {
		sayBackend();

		// Asked before the file is looked for, and that question is the difference between a mod
		// that is installed and has not written its settings yet, which is worth a line because its
		// defaults are then in play, and a mod that is simply not there, which is worth none.
		if (Vitrail.platform().isModLoaded(CHLORIDE_ID)) {
			sayChloride(gameDirectory.resolve(CHLORIDE_CONFIG));
		}
	}

	/**
	 * The whole of what is wrong with this session, in one line, or nothing at all.
	 * <p>
	 * Said as an error rather than a warning because the pack a player asks for is not drawn at all,
	 * and that is the engine's doing: {@code PackChoice.load} finds whichever one is named and stops
	 * before reading it. It stops because the programs are translated against Metal's depth and clip
	 * conventions, so a pass let run on another backend draws a picture both credible and wrong.
	 * Credible and wrong reads as a pack fault, which is worse than a picture the game draws alone,
	 * so nothing is drawn and the line says why.
	 * <p>
	 * Where Iris draws this session the same state is information rather than an error: the picture is
	 * not missing, it is the other engine's.
	 */
	private static void sayBackend() {
		if (!otherBackend()) {
			return;
		}

		if (IrisBeside.draws()) {
			Vitrail.logger().info("This game is running the {} backend with Iris installed, so Iris "
					+ "draws the packs and {} stands aside. {} draws on Metal alone: {}",
					backend(), Vitrail.MOD_NAME, Vitrail.MOD_NAME, diagnosis());

			return;
		}

		Vitrail.logger().error("This game is running the {} backend, and {}'s programs are translated "
				+ "for Metal alone. A pack it is asked for is neither read nor drawn: the game keeps "
				+ "its own image. {}",
				backend(), Vitrail.MOD_NAME, diagnosis());
	}

	/**
	 * Reads Chloride's file rather than Chloride, which nothing here calls into and which the caller
	 * has already established is installed.
	 * <p>
	 * <strong>An entry that is not there is said as well, and that is not tidiness.</strong> Some of
	 * them are ones Chloride starts with on, so answering a missing line the way a line written
	 * {@code false} is answered would leave the exact case that costs a picture silent: what a player
	 * would then have is block entities disappearing and a log that never mentioned it. Which way any
	 * given build of Chloride writes them is its business and is not asserted here, so what is said
	 * is that they were not found rather than what they are.
	 */
	private static void sayChloride(Path file) {
		List<String> lines;
		try {
			if (!Files.isRegularFile(file)) {
				Vitrail.logger().warn("Chloride's own settings were not read: there is no file at {}. "
						+ "Some of them decide which blocks and which entities are drawn at all, "
						+ "before anything of the pack is asked", file);
				return;
			}

			lines = Files.readAllLines(file);
		} catch (IOException failure) {
			Vitrail.logger().warn("Chloride's own settings were not read: {} could not be opened. Some "
					+ "of them decide which blocks and which entities are drawn at all, before "
					+ "anything of the pack is asked", file, failure);
			return;
		}

		List<String> missing = new ArrayList<>();
		for (Entry entry : CHLORIDE) {
			Boolean written = written(lines, entry.table(), entry.key());
			if (written == null) {
				missing.add(entry.name());
			} else if (written) {
				Vitrail.logger().warn("{} is on in {}. {}", entry.name(), file, entry.cost());
			}
		}

		if (!missing.isEmpty()) {
			// Joined rather than handed over whole: each name is already bracketed, and a list
			// printed as one reads "for [[culling] entities]".
			Vitrail.logger().warn("{} writes no line for {}, so whether Chloride is drawing what that "
					+ "governs was not established. It decides which blocks and which entities reach "
					+ "the pack at all", file, String.join(", ", missing));
		}
	}

	/**
	 * One boolean out of one table of a TOML file, read by hand rather than by a parser: a handful of
	 * keys of one file is not worth a dependency.
	 * <p>
	 * A value that is neither {@code true} nor {@code false} answers the same as an absent key, which
	 * is the reading that says it does not know rather than the one that says no.
	 *
	 * @return what the file writes, or null where it writes nothing this understands
	 */
	private static Boolean written(List<String> lines, String table, String key) {
		String current = "";
		for (String raw : lines) {
			// A comment runs to the end of the line, and both a table header and a value may carry
			// one. Cutting it here rather than at each reading below is also what turns a whole line
			// of comment into an empty one.
			int hash = raw.indexOf('#');
			String line = (hash < 0 ? raw : raw.substring(0, hash)).trim();

			if (line.startsWith("[") && line.endsWith("]")) {
				current = line.substring(1, line.length() - 1).trim();
				continue;
			}

			int equals = line.indexOf('=');
			if (!current.equals(table) || equals < 1
					|| !line.substring(0, equals).trim().equals(key)) {
				continue;
			}

			String value = line.substring(equals + 1).trim();
			if (value.equals("true")) {
				return Boolean.TRUE;
			}

			return value.equals("false") ? Boolean.FALSE : null;
		}

		return null;
	}

	/** One entry of Chloride's file, its table, its key and what having it on costs here. */
	private record Entry(String table, String key, String cost) {

		String name() {
			return "[" + this.table + "] " + this.key;
		}
	}
}
