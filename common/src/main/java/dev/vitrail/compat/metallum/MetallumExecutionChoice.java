package dev.vitrail.compat.metallum;

import dev.vitrail.Vitrail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Which Metal generation the next launch runs, as the player chose it.
 * <p>
 * <strong>One word, in a file of its own, read before the graphics device exists.</strong> Metallum asks the
 * {@code metallum.execution} system property once, while it is creating the Metal device, and that moment is
 * long before this mod's settings screen is built and before Sodium's options are registered - a value that
 * arrives late is a value the session has already been created without. So the choice is persisted here and
 * pushed into the property from {@link #apply()}, which the loader entry point calls at the top of
 * {@code Vitrail.initClient}. The file is not {@code pack.txt} and not {@code vitrail/options.txt}: neither is
 * read at that hour, and the second belongs to the shader-pack settings rather than to the backend.
 * <p>
 * <strong>This is a control-layer choice and nothing else.</strong> It says which generation a session is
 * created for; it does not decide whether that generation is usable, and it must not. Metallum verifies its
 * own minimum contract and refuses a forced generation the device cannot satisfy, and this mod neither fakes
 * "Metal 4 supported" nor keeps a list of chip names. A player who turns Metal 4 on for a device that cannot
 * run it gets Metallum's own refusal, which is the honest answer.
 * <p>
 * <strong>Changing it never changes the running session.</strong> The value is read by the device creation of
 * the <em>next</em> launch, so the settings screen's job is to write the file and say that a restart is owed;
 * the backend, the command queue and the shader profile of the session in front of the player are untouched
 * by the toggle.
 */
public enum MetallumExecutionChoice {

	/** The stable path, and what a fresh install, a missing file and an unreadable one all mean. */
	METAL3("metal3"),

	/** The experimental path, chosen deliberately by the player. */
	METAL4("metal4");

	/** What every launch that asks for nothing runs. */
	public static final MetallumExecutionChoice DEFAULT = METAL3;

	/**
	 * The property Metallum reads once at device creation.
	 * <p>
	 * Named here rather than in the settings screen because this class is the whole of this mod's knowledge
	 * of it: one word out, one word back, and no other file in {@code common/} writes a {@code metallum.*}
	 * property.
	 */
	public static final String PROPERTY = "metallum.execution";

	/** One word, beside {@code graphics-api.txt}, under the game's own directory. */
	private static final String FILE = "metal-execution.txt";

	private final String word;

	MetallumExecutionChoice(String word) {
		this.word = word;
	}

	public String word() {
		return this.word;
	}

	/** Whether this is the experimental path, which is what the settings screen's tooltip is about. */
	public boolean experimental() {
		return this == METAL4;
	}

	/**
	 * What the instance asks for, or {@link #DEFAULT} where it asks for nothing this understands.
	 * <p>
	 * A file that is absent, unreadable or holds a word nothing answers to is the default and not a failure:
	 * a broken settings file must not be able to stop the game from starting, and Metal 3 is the answer that
	 * needs no evidence. The unreadable cases are answered quietly because they are indistinguishable from a
	 * fresh install from here; a word that was readable and wrong is said out loud, since that one is
	 * somebody's mistake and would otherwise be invisible.
	 */
	public static MetallumExecutionChoice read() {
		return readIn(file());
	}

	/**
	 * The same question against a directory, which is what the loader entry point has before this mod has a
	 * game to ask: the platform is its own source for the game directory and it is available from the first
	 * line of {@code initClient}.
	 */
	static MetallumExecutionChoice readIn(Path file) {
		String asked;
		try {
			if (!Files.isRegularFile(file)) {
				return DEFAULT;
			}

			asked = Files.readString(file, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
		} catch (IOException | RuntimeException ignored) {
			return DEFAULT;
		}

		for (MetallumExecutionChoice choice : values()) {
			if (choice.word.equals(asked)) {
				return choice;
			}
		}

		Vitrail.logger().warn("vitrail/{} says \"{}\", which is not one of metal3 or metal4, so {} is used",
				FILE, asked, DEFAULT.word);

		return DEFAULT;
	}

	/**
	 * Writes the choice where {@link #read} will find it.
	 * <p>
	 * Through a temporary and an {@code ATOMIC_MOVE} in the same folder, which is what {@code PackFile} and
	 * {@code SettingsFile} already do for their own files: a crash between the two writes would otherwise
	 * leave half a word, and half a word is a file the next launch cannot read. A failure costs the next
	 * session and never this one - the player keeps playing on the backend they already have.
	 */
	public static void write(Path gameDirectory, MetallumExecutionChoice choice) {
		Path file = gameDirectory.resolve("vitrail").resolve(FILE);
		Path temporary = file.resolveSibling(file.getFileName() + ".part");

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(temporary, choice.word + "\n", StandardCharsets.UTF_8);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | RuntimeException e) {
			Vitrail.logger().error("Vitrail could not write the Metal execution choice to {}", file, e);
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException swallowed) {
				e.addSuppressed(swallowed);
			}
		}
	}

	/**
	 * Puts this launch's choice where Metallum will read it, and answers what that choice is.
	 * <p>
	 * <strong>An explicit JVM argument outranks the stored setting</strong>, always: a developer harness that
	 * passes {@code -Dmetallum.execution=metal4} is running a test of that path, and a settings file silently
	 * turning it back into Metal 3 would make the numbers about a session nobody asked for. So the property is
	 * written only where the JVM left it unset - the absent property is the whole of this method's licence to
	 * speak - and the line it logs says which of the two answered.
	 * <p>
	 * Called once, at the top of {@code Vitrail.initClient}, which both loader entry points reach before the
	 * graphics device is created. Nothing else calls it, and nothing may call it later: the property is read
	 * once per process, and a write after device creation would change nothing except a log line.
	 *
	 * @return the generation this launch will run, whoever asked for it
	 */
	public static MetallumExecutionChoice apply() {
		String asked = System.getProperty(PROPERTY);
		if (asked != null) {
			// The property stays exactly as the JVM set it, whatever it says: Metallum is the one that reads
			// it, and a word this setting does not know - `auto` is the one that exists - is Metallum's to
			// answer rather than this class's to refuse. The line below is only a reading of what will happen.
			MetallumExecutionChoice named = known(asked.trim().toLowerCase(Locale.ROOT));
			Vitrail.logger().info("Vitrail Metal preference: {} (asked for by -D{}={}, so the stored choice is"
					+ " not applied)", named == null
							? "left to Metallum, which does not answer \"" + asked.trim() + "\" with Metal 4"
							: described(named),
					PROPERTY, asked);

			return named == null ? DEFAULT : named;
		}

		// The file's absence is said as itself: a default and a stored choice are the same word and not the
		// same fact, and a reader of this line has to be able to tell a fresh install from a setting.
		Path file = file();
		boolean stored = Files.isRegularFile(file);
		MetallumExecutionChoice choice = readIn(file);
		System.setProperty(PROPERTY, choice.word);
		Vitrail.logger().info("Vitrail Metal preference: {} ({})", described(choice),
				stored ? "from vitrail/" + FILE : "the default: no readable vitrail/" + FILE);

		return choice;
	}

	/** A generation as a log line and a tooltip say it, experimental paths named as such. */
	public static String described(MetallumExecutionChoice choice) {
		return choice.experimental() ? "Metal4 (experimental)" : "Metal3";
	}

	/**
	 * The generation a property word names, or null where it names none.
	 * <p>
	 * A word nothing answers to is <em>not</em> corrected here: Metallum logs it and runs Metal 3, and the
	 * caller is a log line that has to say what was actually asked for. Reporting a typo as the default
	 * silently would hide the warn {@code MetalExecutionPreference.read} already writes about it.
	 */
	private static MetallumExecutionChoice known(String word) {
		for (MetallumExecutionChoice choice : values()) {
			if (choice.word.equals(word)) {
				return choice;
			}
		}

		return null;
	}

	/** Where the choice lives, under the game's directory the platform answers with. */
	private static Path file() {
		return Vitrail.platform().gameDirectory().resolve("vitrail").resolve(FILE);
	}
}
