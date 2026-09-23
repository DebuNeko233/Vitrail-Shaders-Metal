package dev.vitrail.compat.metallum;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * What the Vitrail-to-Metallum bridge costs a frame, counted rather than assumed.
 * <p>
 * Every call across this seam is a reflective {@code Method.invoke} on the flat bridge, and the long-term plan's
 * Track D asks three things about it: how many calls a frame, what CPU they take, and how much of the frame's
 * allocation they are. The first two are counted here and the third is arithmetic - a varargs call allocates one
 * {@code Object[]}, so the argument counts the bridges hand over are the array bytes a frame pays.
 * <p>
 * <strong>A lookup is not a call.</strong> Each bridge resolves its {@code Method}s once and keeps them
 * ({@code methods()}, {@code method()}, the static {@code Methods} records), so the reflective *resolution* is a
 * startup cost and the *invocation* is the frame cost. Both are counted, because "reflection only at startup" is
 * the plan's own rule for this seam and a count of lookups is what proves it rather than asserts it.
 * <p>
 * It changes nothing: two counters and one clock read a call, a line a second while a frame is drawing, and no
 * behaviour. A session with no bridge calls pays one method call a frame.
 */
final class BridgeCensus {

	/** Bridge identities, as constants rather than strings: this is on the frame path. */
	static final int FRAME = 0;
	static final int COMPUTE = 1;
	static final int DEPTH = 2;
	static final int SAMPLER = 3;
	static final int TEXTURE = 4;
	static final int SCALE = 5;
	static final int ATTACHMENT = 6;
	/**
	 * The shader-module seam, counted on the same terms as every other bridge.
	 * <p>
	 * It is the one bridge the backend calls rather than this engine, and the one that runs once per
	 * stage of a compile rather than once a frame, so its numbers read against a load and not against
	 * a second: a figure here that moved with the frame rate would mean a compile was happening every
	 * frame.
	 */
	static final int SHADER_MODULE = 7;

	private static final String[] NAMES = {"frame", "compute", "depth", "sampler", "texture", "scale",
			"attachment", "shader module"};
	private static final long INTERVAL_NANOS = 1_000_000_000L;

	private static final long[] calls = new long[NAMES.length];
	private static final long[] arguments = new long[NAMES.length];
	private static final long[] nanos = new long[NAMES.length];
	private static long totalCalls;
	private static long totalNanos;
	private static long lookups;
	private static long saidAt;

	/**
	 * Where the line goes, installed once at client start.
	 * <p>
	 * A sink rather than a logger call, because this class is compiled on its own by
	 * `tests/test_metallum_compute_bridge.py` against fixture bridges: a census that imported the mod's own
	 * logger could not be part of that fixture, and the road it measures is the one the fixture tests.
	 */
	private static Consumer<String> sink;

	private BridgeCensus() {
	}

	/** Installs the one place the line goes. Called once, from the client's own start. */
	static void reporter(final Consumer<String> installed) {
		sink = installed;
	}

	/** One call across the seam: which bridge, how many arguments it boxed, and what it took. */
	static void invoked(final int bridge, final int argumentCount, final long elapsedNanos) {
		calls[bridge]++;
		arguments[bridge] += argumentCount;
		nanos[bridge] += elapsedNanos;
		totalCalls++;
		totalNanos += elapsedNanos;
		say();
	}

	/**
	 * One reflective resolution of a bridge method.
	 * <p>
	 * Expected to happen a handful of times a session, and counted so that a change which starts resolving per
	 * frame is visible as a number rather than as a stall nobody can attribute.
	 */
	static void lookedUp() {
		lookups++;
	}

	private static void say() {
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
		StringBuilder parts = new StringBuilder();
		long arrays = 0;
		long slots = 0;
		for (int index = 0; index < NAMES.length; index++) {
			if (calls[index] == 0) {
				continue;
			}
			parts.append(parts.length() == 0 ? "" : ", ")
					.append(NAMES[index]).append(' ').append(calls[index])
					.append(" (").append(String.format(Locale.ROOT, "%.1f", calls[index] / seconds))
					.append("/s, ").append(String.format(Locale.ROOT, "%.0f", nanos[index] / 1.0e6))
					.append(" ms)");
			arrays += calls[index];
			slots += arguments[index];
		}
		// One Object[] a call, sixteen bytes of header plus four a slot and rounded up to sixteen: the bridges
		// box their arguments into exactly that array, and it is the only allocation this seam makes a call.
		long arrayBytes = arrays * 16L + slots * 4L;
		if (sink != null) {
			sink.accept(String.format(Locale.ROOT,
					"Vitrail bridge: %d calls over %d ms (%.1f a second, %.0f ns a call), %s - "
							+ "%d varargs arrays over the second (~%.1f KiB), %d reflective lookup(s) since launch",
					totalCalls, elapsed / 1_000_000, totalCalls / seconds,
					totalNanos / (double) Math.max(1L, totalCalls),
					parts.length() == 0 ? "none" : parts.toString(),
					arrays, arrayBytes / 1024.0, lookups));
		}
		reset();
		saidAt = now;
	}

	private static void reset() {
		for (int index = 0; index < NAMES.length; index++) {
			calls[index] = 0;
			arguments[index] = 0;
			nanos[index] = 0;
		}
		totalCalls = 0;
		totalNanos = 0;
	}
}
