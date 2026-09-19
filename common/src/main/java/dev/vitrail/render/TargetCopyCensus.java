package dev.vitrail.render;

import dev.vitrail.Vitrail;

/**
 * What the compatibility snapshots cost, counted rather than estimated.
 * <p>
 * A feedback target exists because a geometry program samples a target it also writes, which the public
 * descriptor cannot express: the engine keeps a copy taken at the boundary and lets the program read that. The
 * copies are therefore not an optimisation to remove but a semantic workaround, and the only question worth
 * asking about them is how much they cost - which nothing in the engine said before this class.
 * <p>
 * Pure counting, said once a second at most: how many copies were taken, of how many bytes, and the callers
 * that asked for them. It changes nothing: no copy is skipped, no target is bound differently, and a frame
 * with no feedback targets pays one null check a boundary.
 */
final class TargetCopyCensus {

	/** Once a second, because the numbers are a rate and a frame is not the unit a reader thinks in. */
	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static int copies;
	private static long bytes;
	private static long saidAt;

	private TargetCopyCensus() {
	}

	static void took(final int count, final long copiedBytes) {
		copies += count;
		bytes += copiedBytes;

		long now = System.nanoTime();
		if (saidAt == 0L) {
			saidAt = now;
			return;
		}

		long elapsed = now - saidAt;
		if (elapsed < INTERVAL_NANOS) {
			return;
		}

		double seconds = elapsed / 1_000_000_000.0;
		Vitrail.logger().info("Feedback copies: {} copies of {} MiB in the last {} ms, {} copies and {} MiB a "
						+ "second", copies, String.format(java.util.Locale.ROOT, "%.1f", bytes / 1048576.0),
				elapsed / 1_000_000, String.format(java.util.Locale.ROOT, "%.1f", copies / seconds),
				String.format(java.util.Locale.ROOT, "%.1f", bytes / 1048576.0 / seconds));

		copies = 0;
		bytes = 0L;
		saidAt = now;
	}
}
