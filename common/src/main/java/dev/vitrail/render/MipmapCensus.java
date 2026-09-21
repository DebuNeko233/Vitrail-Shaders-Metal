package dev.vitrail.render;

import dev.vitrail.Vitrail;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How many mip chains a frame actually reduces, and how big they are.
 * <p>
 * The engine already says at load which targets carry a chain and which program asked for one; what nothing said
 * is the per-frame work: how many chains are generated, over how many levels, once a second. Phase 6 of the
 * optimisation plan asks for exactly that, and the answer decides whether the invalidation rule is worth
 * anything more precise - the code already skips a chain that is still valid, so the only candidate left is
 * invalidating less often, and that needs a number first.
 * <p>
 * <strong>And which target each chain was filled for.</strong> The totals above answer "is this worth anything";
 * they cannot answer the plan's own question about a chain nothing reads, because a rate of four chains a second
 * is four images and not one. The label is the caller's - a target name, or {@code shadow} for the map, which is
 * not a pack target at all - and it is counted rather than derived: two chains of one target in one interval are
 * two entries of the same key, and the line says so.
 * <p>
 * Pure counting: no chain is skipped, no level is changed, and a frame that generates none pays one call.
 */
final class MipmapCensus {

	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static int chains;
	private static long levels;
	private static long pixels;
	private static long saidAt;

	/**
	 * The chains of the interval by the target they were filled for, in the order they were first seen so that
	 * two readings of one pack read the same way round. A handful of entries at most - one a target that a
	 * program reads at a lod - and cleared with the rest of the interval.
	 */
	private static final Map<String, Integer> byTarget = new LinkedHashMap<>();

	private MipmapCensus() {
	}

	/**
	 * One chain reduced: how many levels it holds, level nought included, and how many pixels the reduction
	 * touches - levels one down to the last, at the size that level really has. Counted rather than estimated:
	 * this is the number phase 26 asks for and the one that decides whether the work is worth anything.
	 *
	 * @param target the surface the chain was filled for: a pack target's name, or the shadow map's own
	 */
	static void generated(final String target, final int levelsInChain, final int width, final int height) {
		chains++;
		byTarget.merge(target, 1, Integer::sum);
		levels += levelsInChain;
		for (int level = 1; level < levelsInChain; level++) {
			pixels += (long) Math.max(1, width >> level) * Math.max(1, height >> level);
		}

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
		Vitrail.logger().info("Mip chains: {} reduced over {} ms ({} a second), {} levels ({} a chain), "
						+ "{} pixels ({} a frame at {} a second), by target {}",
				chains, elapsed / 1_000_000, String.format(Locale.ROOT, "%.1f", chains / seconds),
				levels, String.format(Locale.ROOT, "%.1f", (double) levels / Math.max(chains, 1)),
				pixels, String.format(Locale.ROOT, "%.0f", (double) pixels / Math.max(chains, 1)),
				String.format(Locale.ROOT, "%.1f", chains / seconds), described());

		chains = 0;
		byTarget.clear();
		levels = 0L;
		pixels = 0L;
		saidAt = now;
	}

	/** The interval's targets, largest count first so that the busiest reads first in a log line. */
	private static String described() {
		return byTarget.entrySet().stream()
				.sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(java.util.stream.Collectors.joining(","));
	}
}
