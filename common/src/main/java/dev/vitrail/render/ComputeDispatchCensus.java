package dev.vitrail.render;

import dev.vitrail.Vitrail;

import java.util.Locale;

/**
 * What a compute dispatch allocates, counted rather than reasoned about.
 * <p>
 * Phase 7 of the optimisation plan asks whether the per-dispatch maps can stop being built from scratch, and its
 * acceptance is a count: maps and objects a dispatch, before and after, with the same bound resources. Nothing
 * in the engine counted them, so this does - one line a second, additive, changing nothing about what is
 * resolved or bound.
 * <p>
 * It counts what the resolve allocates and not what the allocator does: three {@code LinkedHashMap}s and three
 * {@code Map.copyOf} copies a dispatch, whatever their size, plus one {@code Sampled} per sampler reached. A
 * frame with no compute pays one method call.
 */
final class ComputeDispatchCensus {

	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static int dispatches;
	private static long bindings;
	private static long saidAt;

	private ComputeDispatchCensus() {
	}

	/** One resolve: three maps, three copies of them, and the bindings that went into them. */
	static void resolved(final int samplerEntries) {
		dispatches++;
		bindings += samplerEntries;

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
		Vitrail.logger().info("Compute dispatches: {} over {} ms ({} a second), 3 maps and 3 copies each, "
						+ "{} bindings ({} a dispatch)",
				dispatches, elapsed / 1_000_000, String.format(Locale.ROOT, "%.1f", dispatches / seconds),
				bindings, String.format(Locale.ROOT, "%.1f", (double) bindings / Math.max(dispatches, 1)));

		dispatches = 0;
		bindings = 0L;
		saidAt = now;
	}
}
