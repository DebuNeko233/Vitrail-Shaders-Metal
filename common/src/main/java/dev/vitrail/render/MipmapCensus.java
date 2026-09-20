package dev.vitrail.render;

import dev.vitrail.Vitrail;

import java.util.Locale;

/**
 * How many mip chains a frame actually reduces, and how big they are.
 * <p>
 * The engine already says at load which targets carry a chain and which program asked for one; what nothing said
 * is the per-frame work: how many chains are generated, over how many levels, once a second. Phase 6 of the
 * optimisation plan asks for exactly that, and the answer decides whether the invalidation rule is worth
 * anything more precise - the code already skips a chain that is still valid, so the only candidate left is
 * invalidating less often, and that needs a number first.
 * <p>
 * Pure counting: no chain is skipped, no level is changed, and a frame that generates none pays one call.
 */
final class MipmapCensus {

	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static int chains;
	private static long levels;
	private static long pixels;
	private static long saidAt;

	private MipmapCensus() {
	}

	/**
	 * One chain reduced: how many levels it holds, level nought included, and how many pixels the reduction
	 * touches - levels one down to the last, at the size that level really has. Counted rather than estimated:
	 * this is the number phase 26 asks for and the one that decides whether the work is worth anything.
	 */
	static void generated(final int levelsInChain, final int width, final int height) {
		chains++;
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
						+ "{} pixels ({} a frame at {} a second)",
				chains, elapsed / 1_000_000, String.format(Locale.ROOT, "%.1f", chains / seconds),
				levels, String.format(Locale.ROOT, "%.1f", (double) levels / Math.max(chains, 1)),
				pixels, String.format(Locale.ROOT, "%.0f", (double) pixels / Math.max(chains, 1)),
				String.format(Locale.ROOT, "%.1f", chains / seconds));

		chains = 0;
		levels = 0L;
		pixels = 0L;
		saidAt = now;
	}
}
