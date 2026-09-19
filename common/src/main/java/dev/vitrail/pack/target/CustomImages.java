package dev.vitrail.pack.target;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pack's custom images, as Iris's {@code image.<name>} directive declares them.
 * <p>
 * This is the declaration form Complementary Reimagined 5.9.1 uses for the volumes its Advanced Colored
 * Lighting floods light through:
 *
 * <pre>
 * image.floodfill_img      = floodfill_sampler      rgba rgba16f half_float false false 128 64 128
 * image.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128
 * image.voxel_img          = voxel_sampler          red_integer r16ui unsigned_int true false 128 64 128
 * </pre>
 *
 * The fields are, in order: the sampler name the volume is bound under, its format class, its format, its data
 * type, whether it has mipmaps, whether it is cleared, and its three dimensions.
 * <p>
 * <strong>Read the same way Iris reads its own directives, and the same way the constant directives are read:
 * a line this engine does not fully understand is ignored, never half-honoured.</strong> A volume allocated at
 * the wrong size or format, or bound under a name the shader does not declare, is a wrong picture that looks
 * like a pack defect; skipping the line leaves the pack's own guards to take their other branch, which is a
 * behaviour the pack already has.
 * <p>
 * Nothing here allocates or binds anything: this is the reading of the line, so that what a pack asked for can
 * be tested without a device.
 */
public final class CustomImages {

	/** The properties key prefix, beside {@code colortexNFormat} and the rest of the pack's declarations. */
	public static final String KEY_PREFIX = "image.";

	/** Nine fields is the whole of the form; anything else is a line written by a format this engine has not read. */
	private static final int FIELDS = 9;

	/**
	 * A dimension bound, so a mistyped line cannot ask for a volume the device will refuse: the largest any pack
	 * in the corpus declares is 256, and this leaves room above it without letting a typo through.
	 */
	private static final int MAX_DIMENSION = 4096;

	/**
	 * One custom image.
	 *
	 * @param name        the volume's own name, from the key's suffix ({@code floodfill_img})
	 * @param sampler     the sampler name the shader declares and this volume is bound under
	 * @param formatClass the format's class ({@code rgba}, {@code red_integer})
	 * @param format      the format itself ({@code rgba16f}, {@code r16ui})
	 * @param dataType    the shader type ({@code half_float}, {@code unsigned_int})
	 * @param mipmap      whether the volume carries mipmaps
	 * @param clear       whether it is cleared before use
	 * @param width       the volume's width
	 * @param height      the volume's height
	 * @param depth       the volume's depth
	 */
	public record Volume(String name, String sampler, String formatClass, String format, String dataType,
			boolean mipmap, boolean clear, int width, int height, int depth) {

		/** How the volume is described in a log line, once, where the pack is read. */
		public String shape() {
			return this.width + "x" + this.height + "x" + this.depth;
		}
	}

	private CustomImages() {
	}

	/**
	 * The volume this line declares, or empty when it is not one this engine reads.
	 *
	 * @param key   the properties key, {@code image.<name>}
	 * @param value the whole declaration after the {@code =}
	 */
	public static Optional<Volume> parse(String key, String value) {
		if (key == null || value == null || !key.startsWith(KEY_PREFIX)) {
			return Optional.empty();
		}

		String name = key.substring(KEY_PREFIX.length()).trim();
		if (name.isEmpty() || name.indexOf('.') >= 0 || name.indexOf(' ') >= 0) {
			return Optional.empty();
		}

		// Tokenised by hand rather than with String.split: the build refuses that call, and a pack's line is
		// whitespace-separated with runs of it, which is the one thing this has to get right.
		List<String> fields = whitespace(value);
		if (fields.size() != FIELDS) {
			return Optional.empty();
		}

		Boolean mipmap = flag(fields.get(4));
		Boolean clear = flag(fields.get(5));
		if (mipmap == null || clear == null) {
			return Optional.empty();
		}

		int width = dimension(fields.get(6));
		int height = dimension(fields.get(7));
		int depth = dimension(fields.get(8));
		if (width < 1 || height < 1 || depth < 1) {
			return Optional.empty();
		}

		if (fields.get(0).isEmpty() || fields.get(1).isEmpty() || fields.get(2).isEmpty() || fields.get(3).isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new Volume(name, fields.get(0), fields.get(1), fields.get(2), fields.get(3),
				mipmap, clear, width, height, depth));
	}

	/** The line's fields, in order, with runs of whitespace between them collapsed away. */
	private static List<String> whitespace(String value) {
		List<String> fields = new ArrayList<>();
		int index = 0;
		while (index < value.length()) {
			while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
				index++;
			}
			int start = index;
			while (index < value.length() && !Character.isWhitespace(value.charAt(index))) {
				index++;
			}
			if (index > start) {
				fields.add(value.substring(start, index));
			}
		}

		return fields;
	}

	/** {@code true}, {@code false}, or null where the field is neither. */
	private static Boolean flag(String field) {
		return switch (field) {
			case "true" -> Boolean.TRUE;
			case "false" -> Boolean.FALSE;
			default -> null;
		};
	}

	/** The dimension, or nought where it is not a number in range. */
	private static int dimension(String field) {
		try {
			int size = Integer.parseInt(field);

			return size >= 1 && size <= MAX_DIMENSION ? size : 0;
		} catch (NumberFormatException notANumber) {
			return 0;
		}
	}
}
