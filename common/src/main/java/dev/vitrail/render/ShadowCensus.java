package dev.vitrail.render;

import dev.vitrail.Vitrail;

import java.util.Locale;

/**
 * What the shadow walk saw, once a second, beside what the shadow pass costs.
 * <p>
 * The pass table already says the shadow chunk row is the most expensive one in the frame. It cannot say how
 * much of the world that row walked to get there, and the two together are what decides whether the cost is
 * avoidable work or the world the pack asked to rasterise: a row that is expensive because it drew every
 * section the light reaches is a different problem from one that is expensive because it drew the same
 * section four times.
 * <p>
 * Pure reading of the walk this engine already keeps for its own overlay line: no counter is added at any draw
 * site, no behaviour changes, and a frame whose walk has not run pays one null check.
 */
public final class ShadowCensus {

	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static long saidAt;
	private static int walks;
	private static long kept;
	private static long drawn;
	private static long total;
	private static boolean terrain;
	private static String culling = "";

	/**
	 * What the light's walk put into the map besides the terrain: the casters that move, counted where
	 * they are handed over rather than where they are drawn.
	 * <p>
	 * The two counts existed before this and were said once per block table, which is one frame's
	 * sample held out for the whole load; a rate is what says whether the family is worth anything in
	 * the frame's cost, and a pack may refuse either family, so the two are summed apart. Nothing is
	 * added at a draw site and no draw changes: this is the number {@code ShadowGeometry} already
	 * keeps, read once a frame.
	 */
	private static long casterEntities;
	private static long casterBlocks;
	private static long casterFrames;

	private ShadowCensus() {
	}

	/** One frame's gather: what the light's walk found before anything was drawn. */
	public static void casters(final int entities, final int blockEntities) {
		casterEntities += entities;
		casterBlocks += blockEntities;
		casterFrames++;
	}

	/** One walk: what it kept, what carries geometry, how many sections the world holds, and its shape. */
	public static void walked(final int keptSections, final int drawnSections, final int totalSections,
			final boolean terrainDrawn, final String cullingShape) {
		walks++;
		kept += keptSections;
		drawn += drawnSections;
		total += totalSections;
		terrain = terrainDrawn;
		culling = cullingShape;

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
		Vitrail.logger().info("Shadow walk: {} walks ({} a second), kept {} a walk, drew {} a walk, {} loaded, "
						+ "terrain={} culling={}",
				walks, String.format(Locale.ROOT, "%.1f", walks / seconds),
				String.format(Locale.ROOT, "%.0f", (double) kept / Math.max(walks, 1)),
				String.format(Locale.ROOT, "%.0f", (double) drawn / Math.max(walks, 1)),
				String.format(Locale.ROOT, "%.0f", (double) total / Math.max(walks, 1)),
				terrain, culling);
		Vitrail.logger().info("Shadow casters: {} frames gathered, {} entities and {} block entities a frame",
				casterFrames,
				String.format(Locale.ROOT, "%.1f", (double) casterEntities / Math.max(casterFrames, 1)),
				String.format(Locale.ROOT, "%.1f", (double) casterBlocks / Math.max(casterFrames, 1)));

		walks = 0;
		kept = 0;
		drawn = 0;
		total = 0;
		casterEntities = 0;
		casterBlocks = 0;
		casterFrames = 0;
		saidAt = now;
	}
}
